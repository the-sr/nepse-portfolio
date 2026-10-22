package com.nepse.portfolio.service.market;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.service.market.ScraperService;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketDataScheduler {

    private final ScraperService scraperService;


    @Scheduled(fixedDelayString = "${app.scraper.interval-ms:90000}", initialDelayString = "15000")
    public void refreshPrices() {
        scraperService.scrape(false);
    }
}
