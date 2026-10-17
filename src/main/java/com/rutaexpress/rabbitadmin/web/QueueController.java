package com.rutaexpress.rabbitadmin.web;

import com.rutaexpress.rabbitadmin.service.RabbitAdminService;
import com.rutaexpress.rabbitadmin.web.dto.ColaEstadoResponse;
import com.rutaexpress.rabbitadmin.web.dto.ColaRequest;
import com.rutaexpress.rabbitadmin.web.dto.ColaResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Este servicio no valida JWT por su cuenta: solo lo llama el BFF, que ya
 * exige rol Admin para toda la ruta de administración.
 */
@RestController
@RequestMapping("/queues")
public class QueueController {

    private final RabbitAdminService service;

    public QueueController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ColaResponse> crear(@Valid @RequestBody ColaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCola(request));
    }

    @GetMapping("/{name}")
    public ColaEstadoResponse obtener(@PathVariable("name") String name) {
        return service.obtenerCola(name);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> eliminar(@PathVariable("name") String name) {
        service.eliminarCola(name);
        return ResponseEntity.noContent().build();
    }
}
