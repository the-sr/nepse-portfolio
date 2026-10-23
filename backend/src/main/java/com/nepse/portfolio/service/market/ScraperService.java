package com.nepse.portfolio.service.market;

import java.util.Map;

public interface ScraperService {
    Map<String, Object> scrape(boolean force);
    boolean isMarketOpen();
    Map<String, Object> status(String message);

    /** Small, clean payload for logged-in users: verdict, business date and
     *  the last feed result — enough for a UI status pill, no internals. */
    Map<String, Object> publicStatus();
}
