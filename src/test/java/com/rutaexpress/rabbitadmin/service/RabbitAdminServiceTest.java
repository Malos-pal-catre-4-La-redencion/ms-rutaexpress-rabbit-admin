package com.rutaexpress.rabbitadmin.service;

import com.rutaexpress.rabbitadmin.exception.ConflictoRabbitException;
import com.rutaexpress.rabbitadmin.exception.RecursoNoEncontradoException;
import com.rutaexpress.rabbitadmin.exception.SolicitudInvalidaException;
import com.rutaexpress.rabbitadmin.web.dto.BindingRequest;
import com.rutaexpress.rabbitadmin.web.dto.ColaRequest;
import com.rutaexpress.rabbitadmin.web.dto.ColaResponse;
import com.rutaexpress.rabbitadmin.web.dto.ExchangeRequest;
import com.rutaexpress.rabbitadmin.web.dto.TipoExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private AmqpAdmin amqpAdmin;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        service = new RabbitAdminService(amqpAdmin, rabbitTemplate);
    }

    // ------------------------------------------------------------------ colas

    @Test
    void creaColaConRetencionYDlq() {
        ColaRequest request = new ColaRequest("q.envio.comandos", null, null,
                60000L, 1000L, "reject-publish-dlx", "rutaexpress.dlx", "envio.comandos.dlq");

        ColaResponse respuesta = service.crearCola(request);

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());
        Queue cola = captor.getValue();
        assertEquals("q.envio.comandos", cola.getName());
        assertTrue(cola.isDurable());
        assertFalse(cola.isAutoDelete());
        assertEquals(60000L, cola.getArguments().get("x-message-ttl"));
        assertEquals(1000L, cola.getArguments().get("x-max-length"));
        assertEquals("reject-publish-dlx", cola.getArguments().get("x-overflow"));
        assertEquals("rutaexpress.dlx", cola.getArguments().get("x-dead-letter-exchange"));
        assertEquals("envio.comandos.dlq", cola.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("q.envio.comandos", respuesta.nombre());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "con espacios", "cola/con/slash", "amq.reservada"})
    void rechazaNombresDeColaInvalidosSinTocarRabbit(String nombre) {
        ColaRequest request = new ColaRequest(nombre, null, null, null, null, null, null, null);

        assertThrows(SolicitudInvalidaException.class, () -> service.crearCola(request));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void rechazaOverflowDesconocido() {
        ColaRequest request = new ColaRequest("q.a", null, null, null, null, "borrar-todo", null, null);

        assertThrows(SolicitudInvalidaException.class, () -> service.crearCola(request));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void rechazaRoutingKeyDeDlqSinExchangeDeDlq() {
        ColaRequest request = new ColaRequest("q.a", null, null, null, null, null, null, "clave.dlq");

        assertThrows(SolicitudInvalidaException.class, () -> service.crearCola(request));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void rechazaOverflowConDlqSinExchangeDeDlq() {
        ColaRequest request = new ColaRequest("q.a", null, null, null, null, "reject-publish-dlx", null, null);

        assertThrows(SolicitudInvalidaException.class, () -> service.crearCola(request));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void informaConflictoSiLaColaYaExisteConOtrosArgumentos() {
        when(amqpAdmin.declareQueue(any(Queue.class)))
                .thenThrow(new AmqpIOException(new IOException("PRECONDITION_FAILED - inequivalent arg 'x-message-ttl'")));
        ColaRequest request = new ColaRequest("q.a", null, null, 5000L, null, null, null, null);

        assertThrows(ConflictoRabbitException.class, () -> service.crearCola(request));
    }

    @Test
    void eliminaUnaColaExistente() {
        when(amqpAdmin.getQueueProperties("q.a")).thenReturn(new Properties());

        service.eliminarCola("q.a");

        verify(amqpAdmin).deleteQueue("q.a");
    }

    @Test
    void eliminarColaInexistenteDevuelveNoEncontrado() {
        when(amqpAdmin.getQueueProperties("q.nada")).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminarCola("q.nada"));

        verify(amqpAdmin, never()).deleteQueue(any());
    }

    @Test
    void noPermiteEliminarColasReservadas() {
        assertThrows(SolicitudInvalidaException.class, () -> service.eliminarCola("amq.gen-123"));

        verifyNoInteractions(amqpAdmin);
    }

    // -------------------------------------------------------------- exchanges

    @Test
    void creaExchangeTopic() {
        service.crearExchange(new ExchangeRequest("rutaexpress.events", TipoExchange.TOPIC, null, null));

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(captor.capture());
        Exchange exchange = captor.getValue();
        assertEquals("rutaexpress.events", exchange.getName());
        assertEquals("topic", exchange.getType());
        assertTrue(exchange.isDurable());
    }

    @Test
    void creaExchangeDirect() {
        service.crearExchange(new ExchangeRequest("rutaexpress.commands", TipoExchange.DIRECT, true, false));

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(captor.capture());
        assertEquals("direct", captor.getValue().getType());
    }

    @Test
    void rechazaExchangeSinNombreValido() {
        assertThrows(SolicitudInvalidaException.class,
                () -> service.crearExchange(new ExchangeRequest("  ", TipoExchange.DIRECT, null, null)));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void eliminaUnExchangeExistente() {
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Boolean>>any())).thenReturn(true);

        service.eliminarExchange("rutaexpress.events");

        verify(amqpAdmin).deleteExchange("rutaexpress.events");
    }

    @Test
    void eliminarExchangeInexistenteDevuelveNoEncontrado() {
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Boolean>>any())).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminarExchange("no.existe"));

        verify(amqpAdmin, never()).deleteExchange(any(String.class));
    }

    // --------------------------------------------------------------- bindings

    @Test
    void creaBindingEntreExchangeYCola() {
        when(amqpAdmin.getQueueProperties("q.auditoria")).thenReturn(new Properties());
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Boolean>>any())).thenReturn(true);

        service.crearBinding(new BindingRequest("rutaexpress.events", "q.auditoria", "#"));

        ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(captor.capture());
        Binding binding = captor.getValue();
        assertEquals("rutaexpress.events", binding.getExchange());
        assertEquals("q.auditoria", binding.getDestination());
        assertEquals("#", binding.getRoutingKey());
    }

    @Test
    void noCreaBindingSiLaColaNoExiste() {
        when(amqpAdmin.getQueueProperties("q.nada")).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.crearBinding(new BindingRequest("rutaexpress.events", "q.nada", "#")));

        verify(amqpAdmin, never()).declareBinding(any());
    }

    @Test
    void noCreaBindingSiElExchangeNoExiste() {
        when(amqpAdmin.getQueueProperties("q.a")).thenReturn(new Properties());
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Boolean>>any())).thenReturn(false);

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.crearBinding(new BindingRequest("no.existe", "q.a", "clave")));

        verify(amqpAdmin, never()).declareBinding(any());
    }

    @Test
    void rechazaBindingConRoutingKeyVacia() {
        assertThrows(SolicitudInvalidaException.class,
                () -> service.crearBinding(new BindingRequest("rutaexpress.events", "q.a", "  ")));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void eliminaUnBindingExistente() {
        when(amqpAdmin.getQueueProperties("q.a")).thenReturn(new Properties());
        when(rabbitTemplate.execute(ArgumentMatchers.<ChannelCallback<Boolean>>any())).thenReturn(true);

        service.eliminarBinding("rutaexpress.events", "q.a", "envio.#");

        verify(amqpAdmin).removeBinding(any(Binding.class));
    }
}
