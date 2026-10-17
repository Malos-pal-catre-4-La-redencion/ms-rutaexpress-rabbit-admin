package com.rutaexpress.rabbitadmin.web;

import com.rutaexpress.rabbitadmin.exception.ConflictoRabbitException;
import com.rutaexpress.rabbitadmin.exception.RecursoNoEncontradoException;
import com.rutaexpress.rabbitadmin.exception.SolicitudInvalidaException;
import com.rutaexpress.rabbitadmin.service.RabbitAdminService;
import com.rutaexpress.rabbitadmin.web.dto.BindingResponse;
import com.rutaexpress.rabbitadmin.web.dto.ColaResponse;
import com.rutaexpress.rabbitadmin.web.dto.ExchangeResponse;
import com.rutaexpress.rabbitadmin.web.dto.TipoExchange;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({QueueController.class, ExchangeController.class, BindingController.class})
class AdminControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RabbitAdminService service;

    // ------------------------------------------------------------------ colas

    @Test
    void crearColaConNombreVacioDevuelve400YNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Datos inválidos"))
                .andExpect(jsonPath("$.campos.nombre").value("El nombre de la cola es obligatorio"));

        verifyNoInteractions(service);
    }

    @Test
    void crearColaSinNombreDevuelve400() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());

        verifyNoInteractions(service);
    }

    @Test
    void crearColaConTtlNegativoDevuelve400() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"q.a\",\"ttlMs\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.ttlMs").value("ttlMs debe ser mayor que 0"));

        verifyNoInteractions(service);
    }

    @Test
    void crearColaConJsonMalFormadoDevuelve400() throws Exception {
        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void crearColaValidaDevuelve201() throws Exception {
        when(service.crearCola(any())).thenReturn(
                new ColaResponse("q.envio.comandos", true, false, 60000L, 1000L, null, null, null));

        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"q.envio.comandos\",\"ttlMs\":60000,\"maxLength\":1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("q.envio.comandos"))
                .andExpect(jsonPath("$.ttlMs").value(60000));
    }

    @Test
    void crearColaConReglaInvalidaDelServicioDevuelve400() throws Exception {
        when(service.crearCola(any())).thenThrow(new SolicitudInvalidaException("Nombre de cola inválido: 'a b'."));

        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"a b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Nombre de cola inválido: 'a b'."));
    }

    @Test
    void crearColaQueYaExisteConOtraConfiguracionDevuelve409() throws Exception {
        when(service.crearCola(any())).thenThrow(new ConflictoRabbitException("La cola 'q.a' ya existe."));

        mockMvc.perform(post("/queues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"q.a\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminarColaDevuelve204() throws Exception {
        mockMvc.perform(delete("/queues/{name}", "q.a"))
                .andExpect(status().isNoContent());

        verify(service).eliminarCola("q.a");
    }

    @Test
    void eliminarColaInexistenteDevuelve404() throws Exception {
        doThrow(new RecursoNoEncontradoException("La cola 'q.nada' no existe."))
                .when(service).eliminarCola("q.nada");

        mockMvc.perform(delete("/queues/{name}", "q.nada"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("La cola 'q.nada' no existe."));
    }

    // -------------------------------------------------------------- exchanges

    @Test
    void crearExchangeSinTipoDevuelve400() throws Exception {
        mockMvc.perform(post("/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"rutaexpress.events\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipo").exists());

        verifyNoInteractions(service);
    }

    @Test
    void crearExchangeConTipoInexistenteDevuelve400() throws Exception {
        mockMvc.perform(post("/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"rutaexpress.events\",\"tipo\":\"RARO\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void crearExchangeAceptaElTipoEnMinusculas() throws Exception {
        when(service.crearExchange(any())).thenReturn(
                new ExchangeResponse("rutaexpress.events", TipoExchange.TOPIC, true, false));

        mockMvc.perform(post("/exchanges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"rutaexpress.events\",\"tipo\":\"topic\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("TOPIC"));
    }

    @Test
    void eliminarExchangeDevuelve204() throws Exception {
        mockMvc.perform(delete("/exchanges/{name}", "rutaexpress.events"))
                .andExpect(status().isNoContent());

        verify(service).eliminarExchange("rutaexpress.events");
    }

    // --------------------------------------------------------------- bindings

    @Test
    void crearBindingSinRoutingKeyDevuelve400() throws Exception {
        mockMvc.perform(post("/bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"rutaexpress.events\",\"cola\":\"q.auditoria\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.routingKey").exists());

        verifyNoInteractions(service);
    }

    @Test
    void crearBindingValidoDevuelve201() throws Exception {
        when(service.crearBinding(any())).thenReturn(new BindingResponse("rutaexpress.events", "q.auditoria", "#"));

        mockMvc.perform(post("/bindings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"rutaexpress.events\",\"cola\":\"q.auditoria\",\"routingKey\":\"#\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routingKey").value("#"));
    }

    @Test
    void eliminarBindingSinParametrosDevuelve400() throws Exception {
        mockMvc.perform(delete("/bindings"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void eliminarBindingConParametrosDevuelve204() throws Exception {
        mockMvc.perform(delete("/bindings")
                        .param("exchange", "rutaexpress.events")
                        .param("cola", "q.auditoria")
                        .param("routingKey", "#"))
                .andExpect(status().isNoContent());

        verify(service).eliminarBinding("rutaexpress.events", "q.auditoria", "#");
    }
}
