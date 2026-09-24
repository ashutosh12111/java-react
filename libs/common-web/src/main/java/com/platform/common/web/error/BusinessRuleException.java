package com.platform.common.web.error;

import org.springframework.http.HttpStatus;

/** The request is well-formed but violates a business rule (e.g. insufficient stock). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
