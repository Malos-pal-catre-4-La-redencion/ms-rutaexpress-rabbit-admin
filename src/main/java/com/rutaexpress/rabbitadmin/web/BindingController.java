package com.rutaexpress.rabbitadmin.web;

import com.rutaexpress.rabbitadmin.service.RabbitAdminService;
import com.rutaexpress.rabbitadmin.web.dto.BindingRequest;
import com.rutaexpress.rabbitadmin.web.dto.BindingResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bindings")
public class BindingController {

    private final RabbitAdminService service;

    public BindingController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BindingResponse> crear(@Valid @RequestBody BindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearBinding(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminar(@RequestParam("exchange") String exchange,
                                         @RequestParam("cola") String cola,
                                         @RequestParam("routingKey") String routingKey) {
        service.eliminarBinding(exchange, cola, routingKey);
        return ResponseEntity.noContent().build();
    }
}
