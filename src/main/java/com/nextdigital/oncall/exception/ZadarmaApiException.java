package com.nextdigital.oncall.exception;

/**
 * Thrown when the Zadarma VoIP API returns an error or is unreachable.
 * Unchecked — used to trigger transaction rollback where needed.
 */
public class ZadarmaApiException extends RuntimeException {

    public ZadarmaApiException(String message) {
        super(message);
    }

    public ZadarmaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
