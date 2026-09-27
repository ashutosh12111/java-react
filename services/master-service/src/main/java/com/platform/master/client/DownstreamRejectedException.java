package com.platform.master.client;

import com.platform.common.web.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A downstream service answered with a 4xx: it understood the request and definitively refused it
 * (validation, not found, business rule). Nothing happened on its side, so this is never retried.
 *
 * <p>The orchestrator translates the ones it expects into client-facing errors. Any that escape
 * untranslated indicate a bug in the master-service and surface as {@code 502 Bad Gateway}.
 */
public class DownstreamRejectedException extends ApiException {

    private final String service;
    private final int downstreamStatus;
    private final String downstreamCode;
    private final String downstreamMessage;

    public DownstreamRejectedException(String service, int downstreamStatus, String downstreamCode, String downstreamMessage) {
        super(HttpStatus.BAD_GATEWAY, "DOWNSTREAM_REJECTED",
                service + " rejected the request (" + downstreamStatus + " " + downstreamCode + ")");
        this.service = service;
        this.downstreamStatus = downstreamStatus;
        this.downstreamCode = downstreamCode;
        this.downstreamMessage = downstreamMessage;
    }

    public String service() {
        return service;
    }

    public int downstreamStatus() {
        return downstreamStatus;
    }

    public String downstreamCode() {
        return downstreamCode;
    }

    public String downstreamMessage() {
        return downstreamMessage;
    }

    public boolean is(int status) {
        return downstreamStatus == status;
    }
}
