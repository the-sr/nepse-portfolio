package com.nepse.portfolio.service.market;

import java.util.Map;

public interface ScraperService {
    Map<String, Object> scrape(boolean force);
    boolean isMarketOpen();
    Map<String, Object> status(String message);
}
