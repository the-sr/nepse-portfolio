package com.nepse.portfolio.utils;


import com.nepse.portfolio.utils.ScrapedQuote;

import java.util.List;

/**
 * A source of live NEPSE quotes. Implementations are tried in order (see
 * {@code app.market.providers}) until one returns a non-empty result.
 */
public interface MarketDataProvider {

    /** Stable identifier used in configuration and scrape-run records. */
    String name();

    /** Human readable description shown in the admin status endpoint. */
    default String description() {
        return name();
    }

    List<ScrapedQuote> fetch();
}
