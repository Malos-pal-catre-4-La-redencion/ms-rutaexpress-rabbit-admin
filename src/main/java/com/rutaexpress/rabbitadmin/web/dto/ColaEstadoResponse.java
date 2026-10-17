package com.rutaexpress.rabbitadmin.web.dto;

public record ColaEstadoResponse(String nombre, int mensajesPendientes, int consumidores) {
}
