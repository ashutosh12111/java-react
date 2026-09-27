package com.platform.master.checkout;

import com.platform.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/** Re-raises a stored 4xx outcome when a checkout request is repeated with the same key. */
class ReplayedFailureException extends ApiException {

    ReplayedFailureException(int status, String code, String message) {
        super(HttpStatus.valueOf(status), code, message);
    }
}
