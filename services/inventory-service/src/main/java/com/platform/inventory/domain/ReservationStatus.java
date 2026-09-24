package com.platform.inventory.domain;

/**
 * <pre>
 * RESERVED ──release──▶ RELEASED   (compensation: order cancelled / payment failed)
 *    │
 *    └──────commit────▶ COMMITTED  (order confirmed; stock is sold)
 * </pre>
 * RELEASED and COMMITTED are terminal.
 */
public enum ReservationStatus {
    RESERVED,
    RELEASED,
    COMMITTED
}
