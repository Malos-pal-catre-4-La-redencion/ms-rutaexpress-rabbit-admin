package com.rutaexpress.rabbitadmin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExchangeRequest(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(max = 255, message = "El nombre del exchange no puede superar 255 caracteres")
        String nombre,
        @NotNull(message = "El tipo es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
        TipoExchange tipo,
        Boolean durable,
        Boolean autoDelete
) {
}
