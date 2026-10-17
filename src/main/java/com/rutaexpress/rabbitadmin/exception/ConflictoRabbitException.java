package com.rutaexpress.rabbitadmin.exception;

public class ConflictoRabbitException extends RuntimeException {

    public ConflictoRabbitException(String mensaje) {
        super(mensaje);
    }
}
