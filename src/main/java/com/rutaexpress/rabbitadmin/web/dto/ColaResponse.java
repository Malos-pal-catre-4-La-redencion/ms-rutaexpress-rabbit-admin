package com.rutaexpress.rabbitadmin.web.dto;

public record ColaResponse(
        String nombre,
        boolean durable,
        boolean autoDelete,
        Long ttlMs,
        Long maxLength,
        String overflow,
        String deadLetterExchange,
        String deadLetterRoutingKey
) {
}
