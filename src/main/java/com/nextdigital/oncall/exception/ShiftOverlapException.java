package com.nextdigital.oncall.exception;

import lombok.Getter;

@Getter
public class ShiftOverlapException extends RuntimeException {

    public ShiftOverlapException(String message) {
        super(message);
    }
}
