package com.platform.notification.domain;

import java.util.regex.Pattern;

/** Delivery channel, with the recipient format each channel accepts. */
public enum Channel {
    EMAIL("^[^@\\s]{1,64}@[^@\\s]{1,255}$"),
    SMS("^\\+[1-9]\\d{6,14}$"),
    PUSH("^[A-Za-z0-9:_-]{10,256}$");

    private final Pattern recipientFormat;

    Channel(String recipientRegex) {
        this.recipientFormat = Pattern.compile(recipientRegex);
    }

    public boolean acceptsRecipient(String recipient) {
        return recipient != null && recipientFormat.matcher(recipient).matches();
    }
}
