package com.nepse.portfolio.domain;

/**
 * How a share lot entered (or left) the portfolio.
 *
 * <p>Cost semantics per type (see docs/nepse-costs.md):</p>
 * <ul>
 *   <li>{@link #BUY} / {@link #AUCTION} — paid market purchase; broker
 *       commission + SEBON fee apply.</li>
 *   <li>{@link #IPO} / {@link #FPO} — allotted at the offer price; no fees.</li>
 *   <li>{@link #RIGHT} — paid subscription at the offer price; no fees.</li>
 *   <li>{@link #BONUS} — free shares at Rs 0 basis; the weighted average cost
 *       dilutes instead of rising.</li>
 *   <li>{@link #SELL} — secondary-market sale; commission + SEBON + DP +
 *       capital gains tax apply.</li>
 * </ul>
 */
public enum TradeType {
    BUY,
    SELL,
    IPO,
    FPO,
    BONUS,
    RIGHT,
    AUCTION
}
