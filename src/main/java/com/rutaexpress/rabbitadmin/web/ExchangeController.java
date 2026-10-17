package com.rutaexpress.rabbitadmin.web;

import com.rutaexpress.rabbitadmin.service.RabbitAdminService;
import com.rutaexpress.rabbitadmin.web.dto.ExchangeRequest;
import com.rutaexpress.rabbitadmin.web.dto.ExchangeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exchanges")
public class ExchangeController {

    private final RabbitAdminService service;

    public ExchangeController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> crear(@Valid @RequestBody ExchangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearExchange(request));
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> eliminar(@PathVariable("name") String name) {
        service.eliminarExchange(name);
        return ResponseEntity.noContent().build();
    }
}
