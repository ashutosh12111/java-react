package com.platform.common.web.error;

import org.springframework.http.HttpStatus;

/** The request is syntactically valid but semantically wrong (e.g. unknown sort field). */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
