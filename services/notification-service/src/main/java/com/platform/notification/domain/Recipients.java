package com.platform.notification.domain;

/** Masks personal data before it reaches logs or API listings. */
public final class Recipients {

    private Recipients() {
    }

    public static String mask(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        int at = recipient.indexOf('@');
        if (at > 0) {
            return recipient.charAt(0) + "***" + recipient.substring(at);
        }
        return recipient.substring(0, 2) + "***" + recipient.substring(recipient.length() - 3);
    }
}
