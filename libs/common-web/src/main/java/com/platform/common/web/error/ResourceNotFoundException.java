package com.platform.common.web.error;

import org.springframework.http.HttpStatus;

/** The addressed resource does not exist. Produces {@code 404} with code {@code <RESOURCE>_NOT_FOUND}. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND,
                resource.toUpperCase().replace(' ', '_') + "_NOT_FOUND",
                resource + " '" + id + "' was not found");
    }
}
