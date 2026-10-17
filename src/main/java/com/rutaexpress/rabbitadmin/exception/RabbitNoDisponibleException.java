package com.rutaexpress.rabbitadmin.exception;

public class RabbitNoDisponibleException extends RuntimeException {

    public RabbitNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
