package com.rutaexpress.rabbitadmin.service;

import com.rutaexpress.rabbitadmin.exception.ConflictoRabbitException;
import com.rutaexpress.rabbitadmin.exception.RabbitNoDisponibleException;
import com.rutaexpress.rabbitadmin.exception.RecursoNoEncontradoException;
import com.rutaexpress.rabbitadmin.exception.SolicitudInvalidaException;
import com.rutaexpress.rabbitadmin.web.dto.BindingRequest;
import com.rutaexpress.rabbitadmin.web.dto.BindingResponse;
import com.rutaexpress.rabbitadmin.web.dto.ColaEstadoResponse;
import com.rutaexpress.rabbitadmin.web.dto.ColaRequest;
import com.rutaexpress.rabbitadmin.web.dto.ColaResponse;
import com.rutaexpress.rabbitadmin.web.dto.ExchangeRequest;
import com.rutaexpress.rabbitadmin.web.dto.ExchangeResponse;
import com.rutaexpress.rabbitadmin.web.dto.TipoExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[A-Za-z0-9._:-]{1,255}$");
    private static final Set<String> OVERFLOW_VALIDOS = Set.of("drop-head", "reject-publish", "reject-publish-dlx");
    private static final String PREFIJO_RESERVADO = "amq.";

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
    }


    public ColaResponse crearCola(ColaRequest request) {
        String nombre = validarNombre(request.nombre(), "cola");
        rechazarReservado(nombre, "cola");

        String overflow = limpiar(request.overflow());
        String dlx = limpiar(request.deadLetterExchange());
        String dlrk = limpiar(request.deadLetterRoutingKey());

        if (overflow != null && !OVERFLOW_VALIDOS.contains(overflow)) {
            throw new SolicitudInvalidaException("overflow '" + overflow
                    + "' no es válido. Valores permitidos: drop-head, reject-publish, reject-publish-dlx.");
        }
        if (dlx != null) {
            validarNombre(dlx, "dead-letter exchange");
        }
        if (dlrk != null && dlx == null) {
            throw new SolicitudInvalidaException("deadLetterRoutingKey requiere indicar también deadLetterExchange.");
        }
        if ("reject-publish-dlx".equals(overflow) && dlx == null) {
            throw new SolicitudInvalidaException("overflow 'reject-publish-dlx' requiere indicar deadLetterExchange.");
        }

        boolean durable = request.durable() == null || request.durable();
        boolean autoDelete = request.autoDelete() != null && request.autoDelete();

        Map<String, Object> argumentos = new HashMap<>();
        if (request.ttlMs() != null) {
            argumentos.put("x-message-ttl", request.ttlMs());
        }
        if (request.maxLength() != null) {
            argumentos.put("x-max-length", request.maxLength());
        }
        if (overflow != null) {
            argumentos.put("x-overflow", overflow);
        }
        if (dlx != null) {
            argumentos.put("x-dead-letter-exchange", dlx);
        }
        if (dlrk != null) {
            argumentos.put("x-dead-letter-routing-key", dlrk);
        }

        Queue cola = new Queue(nombre, durable, false, autoDelete, argumentos);
        try {
            amqpAdmin.declareQueue(cola);
        } catch (AmqpException ex) {
            throw traducir(ex, "La cola '" + nombre + "' ya existe con argumentos distintos a los solicitados. "
                    + "Elimínela primero o use los mismos argumentos.");
        }

        log.info("Cola creada: {} (durable={}, argumentos={})", nombre, durable, argumentos);
        return new ColaResponse(nombre, durable, autoDelete, request.ttlMs(), request.maxLength(),
                overflow, dlx, dlrk);
    }

    public ColaEstadoResponse obtenerCola(String nombre) {
        String n = validarNombre(nombre, "cola");
        Properties props = propiedadesCola(n);
        if (props == null) {
            throw new RecursoNoEncontradoException("La cola '" + n + "' no existe.");
        }
        return new ColaEstadoResponse(n,
                comoEntero(props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)),
                comoEntero(props.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)));
    }

    public void eliminarCola(String nombre) {
        String n = validarNombre(nombre, "cola");
        rechazarReservado(n, "cola");
        if (propiedadesCola(n) == null) {
            throw new RecursoNoEncontradoException("La cola '" + n + "' no existe.");
        }
        try {
            amqpAdmin.deleteQueue(n);
        } catch (AmqpException ex) {
            throw traducir(ex, null);
        }
        log.info("Cola eliminada: {}", n);
    }


    public ExchangeResponse crearExchange(ExchangeRequest request) {
        String nombre = validarNombre(request.nombre(), "exchange");
        rechazarReservado(nombre, "exchange");
        if (request.tipo() == null) {
            throw new SolicitudInvalidaException("El tipo de exchange es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS).");
        }

        boolean durable = request.durable() == null || request.durable();
        boolean autoDelete = request.autoDelete() != null && request.autoDelete();

        try {
            amqpAdmin.declareExchange(construirExchange(nombre, request.tipo(), durable, autoDelete));
        } catch (AmqpException ex) {
            throw traducir(ex, "El exchange '" + nombre + "' ya existe con un tipo o configuración distintos. "
                    + "Elimínelo primero o use la misma configuración.");
        }

        log.info("Exchange creado: {} (tipo={}, durable={})", nombre, request.tipo(), durable);
        return new ExchangeResponse(nombre, request.tipo(), durable, autoDelete);
    }

    public void eliminarExchange(String nombre) {
        String n = validarNombre(nombre, "exchange");
        rechazarReservado(n, "exchange");
        if (!exchangeExiste(n)) {
            throw new RecursoNoEncontradoException("El exchange '" + n + "' no existe.");
        }
        try {
            amqpAdmin.deleteExchange(n);
        } catch (AmqpException ex) {
            throw traducir(ex, null);
        }
        log.info("Exchange eliminado: {}", n);
    }


    public BindingResponse crearBinding(BindingRequest request) {
        Binding binding = construirBinding(request.exchange(), request.cola(), request.routingKey());
        verificarExtremosDelBinding(binding);
        try {
            amqpAdmin.declareBinding(binding);
        } catch (AmqpException ex) {
            throw traducir(ex, null);
        }
        log.info("Binding creado: {} -> {} (routingKey={})",
                binding.getExchange(), binding.getDestination(), binding.getRoutingKey());
        return new BindingResponse(binding.getExchange(), binding.getDestination(), binding.getRoutingKey());
    }

    public void eliminarBinding(String exchange, String cola, String routingKey) {
        Binding binding = construirBinding(exchange, cola, routingKey);
        verificarExtremosDelBinding(binding);
        try {
            amqpAdmin.removeBinding(binding);
        } catch (AmqpException ex) {
            throw traducir(ex, null);
        }
        log.info("Binding eliminado: {} -> {} (routingKey={})",
                binding.getExchange(), binding.getDestination(), binding.getRoutingKey());
    }


    private Binding construirBinding(String exchange, String cola, String routingKey) {
        String ex = validarNombre(exchange, "exchange");
        String q = validarNombre(cola, "cola");
        if (routingKey == null || routingKey.isBlank()) {
            throw new SolicitudInvalidaException("La routingKey no puede estar vacía.");
        }
        if (routingKey.length() > 255) {
            throw new SolicitudInvalidaException("La routingKey no puede superar 255 caracteres.");
        }
        return new Binding(q, Binding.DestinationType.QUEUE, ex, routingKey, null);
    }

    private void verificarExtremosDelBinding(Binding binding) {
        if (propiedadesCola(binding.getDestination()) == null) {
            throw new RecursoNoEncontradoException("La cola '" + binding.getDestination() + "' no existe.");
        }
        if (!exchangeExiste(binding.getExchange())) {
            throw new RecursoNoEncontradoException("El exchange '" + binding.getExchange() + "' no existe.");
        }
    }

    private Exchange construirExchange(String nombre, TipoExchange tipo, boolean durable, boolean autoDelete) {
        return switch (tipo) {
            case DIRECT -> new DirectExchange(nombre, durable, autoDelete);
            case TOPIC -> new TopicExchange(nombre, durable, autoDelete);
            case FANOUT -> new FanoutExchange(nombre, durable, autoDelete);
            case HEADERS -> new HeadersExchange(nombre, durable, autoDelete);
        };
    }

    private Properties propiedadesCola(String nombre) {
        try {
            return amqpAdmin.getQueueProperties(nombre);
        } catch (AmqpException ex) {
            throw traducir(ex, null);
        }
    }

    private boolean exchangeExiste(String nombre) {
        try {
            Boolean existe = rabbitTemplate.execute(channel -> {
                try {
                    channel.exchangeDeclarePassive(nombre);
                    return true;
                } catch (IOException e) {
                    return false;
                }
            });
            return Boolean.TRUE.equals(existe);
        } catch (AmqpException ex) {
            throw traducir(ex, null);
        }
    }

    private String validarNombre(String valor, String tipo) {
        if (valor == null || valor.isBlank()) {
            throw new SolicitudInvalidaException("El nombre de la " + tipo + " no puede estar vacío.");
        }
        if (!NOMBRE_VALIDO.matcher(valor).matches()) {
            throw new SolicitudInvalidaException("Nombre de " + tipo + " inválido: '" + valor + "'. "
                    + "Solo se permiten letras, números, punto, guion, guion bajo y dos puntos (máximo 255 caracteres).");
        }
        return valor;
    }

    private void rechazarReservado(String nombre, String tipo) {
        if (nombre.startsWith(PREFIJO_RESERVADO)) {
            throw new SolicitudInvalidaException("Los nombres que empiezan con '" + PREFIJO_RESERVADO
                    + "' están reservados por RabbitMQ y no se pueden usar para la " + tipo + ".");
        }
    }

    private String limpiar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private int comoEntero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : 0;
    }

    private RuntimeException traducir(AmqpException ex, String mensajeConflicto) {
        log.error("Error de RabbitMQ: {}", ex.getMessage());
        if (mensajeConflicto != null && esPrecondicionFallida(ex)) {
            return new ConflictoRabbitException(mensajeConflicto);
        }
        return new RabbitNoDisponibleException("No se pudo completar la operación en RabbitMQ: " + ex.getMessage(), ex);
    }

    private boolean esPrecondicionFallida(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String mensaje = t.getMessage();
            if (mensaje != null && mensaje.contains("PRECONDITION_FAILED")) {
                return true;
            }
        }
        return false;
    }
}
