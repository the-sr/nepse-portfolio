package com.nepse.portfolio.market.scraper;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataScheduler {

    private final ScraperService scraperService;

    public MarketDataScheduler(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @Scheduled(fixedDelayString = "${app.scraper.interval-ms:90000}", initialDelayString = "15000")
    public void refreshPrices() {
        scraperService.scrape(false);
    }
}
