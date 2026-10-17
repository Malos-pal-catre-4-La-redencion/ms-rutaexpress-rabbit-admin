package com.rutaexpress.rabbitadmin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ColaRequest(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(max = 255, message = "El nombre de la cola no puede superar 255 caracteres")
        String nombre,
        Boolean durable,
        Boolean autoDelete,
        @Positive(message = "ttlMs debe ser mayor que 0")
        Long ttlMs,
        @Positive(message = "maxLength debe ser mayor que 0")
        Long maxLength,
        String overflow,
        String deadLetterExchange,
        String deadLetterRoutingKey
) {
}
