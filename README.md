# ms-rutaexpress-rabbit-admin

Administración de RabbitMQ para RutaExpress (EP2, DSY1107). Este repositorio contiene:

- `docker/` — clúster RabbitMQ de 2 nodos con Docker Compose.
- Microservicio Spring Boot que crea y elimina colas, exchanges y bindings por REST.

## Arquitectura

```
Angular (MSAL) → API Gateway (/api/admin/*) → BFF (solo rol Admin) → ms-rabbit-admin → RabbitMQ
```

Este servicio es **interno**, igual que shipments y catalog: no valida JWT, porque
el BFF ya lo valida y exige rol `Admin` en toda la ruta de administración.
En EC2 su puerto (8084) no debe estar abierto a internet.

## Requisitos

- Java 17 y Maven 3.9+
- Un clúster RabbitMQ en marcha (ver más abajo)

## Variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `SERVER_PORT` | `8084` | Puerto del servicio |
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto AMQP |
| `RABBITMQ_USER` | `rutaexpress` | Usuario (el `RABBITMQ_DEFAULT_USER` de `docker/.env`) |
| `RABBITMQ_PASSWORD` | *(vacío)* | Clave (el `RABBITMQ_DEFAULT_PASS` de `docker/.env`) |

```bash
export RABBITMQ_PASSWORD='la-misma-clave-del-.env'
mvn spring-boot:run
```

Salud del servicio (incluye la conexión a RabbitMQ): `GET http://localhost:8084/actuator/health`

## Endpoints

| Método | Ruta | Descripción | Respuesta |
|---|---|---|---|
| POST | `/queues` | Crea una cola (con retención y DLQ opcionales) | 201 |
| GET | `/queues/{name}` | Mensajes pendientes y consumidores de la cola | 200 |
| DELETE | `/queues/{name}` | Elimina una cola | 204 |
| POST | `/exchanges` | Crea un exchange (`DIRECT`, `TOPIC`, `FANOUT`, `HEADERS`) | 201 |
| DELETE | `/exchanges/{name}` | Elimina un exchange | 204 |
| POST | `/bindings` | Enlaza una cola a un exchange con una routing key | 201 |
| DELETE | `/bindings?exchange=&cola=&routingKey=` | Elimina un binding | 204 |

Toda la lógica está en `RabbitAdminService`; los controladores solo delegan.

### Campos de `POST /queues`

| Campo | Obligatorio | Efecto |
|---|---|---|
| `nombre` | sí | Letras, números, `.` `-` `_` `:`; máx. 255; no puede empezar con `amq.` |
| `durable` | no (true) | Sobrevive a reinicios del broker |
| `autoDelete` | no (false) | Se borra al desconectarse el último consumidor |
| `ttlMs` | no | Expiración de mensajes (`x-message-ttl`) |
| `maxLength` | no | Máximo de mensajes en la cola (`x-max-length`) |
| `overflow` | no | `drop-head`, `reject-publish` o `reject-publish-dlx` |
| `deadLetterExchange` | no | Dónde van los mensajes rechazados, expirados o desbordados |
| `deadLetterRoutingKey` | no | Routing key con la que llegan a la DLQ |

## Ejemplo: topología completa con retención y DLQ

```bash
A=http://localhost:8084

# 1) Exchanges: uno direct (comandos), uno topic (eventos) y el de dead-letter
curl -X POST $A/exchanges -H 'Content-Type: application/json' -d '{"nombre":"demo.commands","tipo":"DIRECT"}'
curl -X POST $A/exchanges -H 'Content-Type: application/json' -d '{"nombre":"demo.events","tipo":"TOPIC"}'
curl -X POST $A/exchanges -H 'Content-Type: application/json' -d '{"nombre":"demo.dlx","tipo":"DIRECT"}'

# 2) Cola de destino de los mensajes muertos (la DLQ)
curl -X POST $A/queues -H 'Content-Type: application/json' -d '{"nombre":"q.demo.comandos.dlq"}'
curl -X POST $A/bindings -H 'Content-Type: application/json' \
  -d '{"exchange":"demo.dlx","cola":"q.demo.comandos.dlq","routingKey":"demo.comandos.dlq"}'

# 3) Cola principal con retención (expira a 60 s, máx. 1000 mensajes) y DLQ
curl -X POST $A/queues -H 'Content-Type: application/json' -d '{
  "nombre":"q.demo.comandos","ttlMs":60000,"maxLength":1000,
  "overflow":"reject-publish-dlx",
  "deadLetterExchange":"demo.dlx","deadLetterRoutingKey":"demo.comandos.dlq"}'
curl -X POST $A/bindings -H 'Content-Type: application/json' \
  -d '{"exchange":"demo.commands","cola":"q.demo.comandos","routingKey":"demo.comando"}'

# 4) Ver métricas y limpiar
curl $A/queues/q.demo.comandos
curl -X DELETE "$A/bindings?exchange=demo.commands&cola=q.demo.comandos&routingKey=demo.comando"
curl -X DELETE $A/queues/q.demo.comandos
curl -X DELETE $A/queues/q.demo.comandos.dlq
curl -X DELETE $A/exchanges/demo.commands
curl -X DELETE $A/exchanges/demo.events
curl -X DELETE $A/exchanges/demo.dlx
```

Cada cambio se ve de inmediato en el Management UI (`http://HOST:15672`, pestañas **Queues** y **Exchanges**).

## Errores

Todas las respuestas de error usan el mismo formato que shipments y catalog:

```json
{ "timestamp": "2026-10-16T15:00:00Z", "status": 400, "error": "Datos inválidos", "campos": { "nombre": "El nombre de la cola es obligatorio" } }
```

| Código | Cuándo |
|---|---|
| 400 | Nombre vacío o con caracteres no permitidos, nombre reservado (`amq.`), tipo de exchange inexistente, `ttlMs` o `maxLength` ≤ 0, `overflow` desconocido, DLQ mal configurada, JSON mal formado, parámetros faltantes. **No se crea nada.** |
| 404 | La cola o el exchange no existe (al eliminar, o al crear un binding hacia ellos) |
| 409 | La cola o el exchange ya existe con otra configuración |
| 503 | RabbitMQ no responde o rechazó las credenciales |

## Pruebas

```bash
mvn test
```

No necesitan un RabbitMQ en marcha: `RabbitAdminServiceTest` usa mocks de `AmqpAdmin` y
`AdminControllersTest` prueba los endpoints con MockMvc.

---

## Clúster RabbitMQ (rabbit1 + rabbit2)

| Nodo | AMQP | Management UI |
|---|---|---|
| rabbit1 | 5672 | http://HOST:15672 |
| rabbit2 | 5673 | http://HOST:15673 |

### Levantar en local o en AWS EC2

```bash
cd docker
cp .env.example .env      # editar usuario, clave y cookie
docker compose up -d
docker compose ps         # ambos nodos deben quedar "healthy"
```

`rabbit2` se une solo a `rabbit1` mediante `scripts/join-cluster.sh`
(`stop_app` → `join_cluster rabbit@rabbit1` → `start_app`). Si el contenedor
se reinicia, el script detecta que ya es parte del clúster y no repite la unión.

### Verificar que hay clúster

```bash
docker exec rabbit1 rabbitmqctl cluster_status
```

Deben aparecer `rabbit@rabbit1` y `rabbit@rabbit2` en *Running Nodes*. En el
Management UI (pestaña **Overview → Nodes**) se ven los dos nodos en verde.

### Despliegue en EC2

1. Instancia con Docker y el plugin Compose instalados.
2. Security group: abrir **15672** (UI) solo a tu IP y **5672** solo a los
   security groups de los microservicios. No exponer 5673 ni 15673 a internet.
3. Copiar `docker/` a la instancia, crear el `.env` y ejecutar `docker compose up -d`.

### Notas de diseño (para la defensa)

- Ambos nodos comparten `RABBITMQ_ERLANG_COOKIE`; sin la misma cookie no pueden
  unirse.
- Las colas clásicas viven en el nodo donde se declaran. Con solo 2 nodos, las
  *quorum queues* pierden la mayoría si cae uno, por eso no aportan alta
  disponibilidad en este montaje.
- Los volúmenes `rabbit1-data` y `rabbit2-data` conservan el estado del clúster
  entre reinicios.
- Detener y borrar todo: `docker compose down -v`.
