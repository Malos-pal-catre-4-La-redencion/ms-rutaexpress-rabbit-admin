# ms-rutaexpress-rabbit-admin

Administración de RabbitMQ para RutaExpress (EP2, DSY1107).
Este repositorio contiene:

- `docker/` — clúster RabbitMQ de 2 nodos con Docker Compose (**listo**).
- Microservicio administrador Spring Boot (colas, exchanges y bindings por REST) — *próximamente*.

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
