package com.rutaexpress.rabbitadmin.web.dto;

public record ExchangeResponse(String nombre, TipoExchange tipo, boolean durable, boolean autoDelete) {
}
