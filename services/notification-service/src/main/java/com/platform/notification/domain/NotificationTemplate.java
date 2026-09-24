package com.platform.notification.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Known message templates. Callers send a template name plus variables, never free-form text, so
 * wording, localisation and escaping stay owned by this service.
 */
public enum NotificationTemplate {
    WELCOME("Welcome to the platform", "Hi {firstName}, your account is ready."),
    ORDER_CONFIRMED("Order {orderId} confirmed", "Your order {orderId} for {total} {currency} is confirmed."),
    ORDER_CANCELLED("Order {orderId} cancelled", "Your order {orderId} was cancelled: {reason}."),
    PAYMENT_FAILED("Payment failed for order {orderId}", "We could not process the payment for order {orderId}.");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final String subject;
    private final String body;

    NotificationTemplate(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public RenderedMessage render(Map<String, String> variables) {
        return new RenderedMessage(fill(subject, variables), fill(body, variables));
    }

    private static String fill(String text, Map<String, String> variables) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(variables.getOrDefault(m.group(1), "")));
        }
        m.appendTail(out);
        return out.toString();
    }

    public record RenderedMessage(String subject, String body) {
    }
}
