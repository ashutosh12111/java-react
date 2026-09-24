package com.platform.common.web.error;

import org.springframework.http.HttpStatus;

/** The request conflicts with the current state of the resource (duplicate key, invalid state transition). */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
