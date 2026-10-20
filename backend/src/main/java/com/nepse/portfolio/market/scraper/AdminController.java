package com.nepse.portfolio.market.scraper;

import com.nepse.portfolio.recommend.MlClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ScraperService scraperService;
    private final MlClient mlClient;

    public AdminController(ScraperService scraperService, MlClient mlClient) {
        this.scraperService = scraperService;
        this.mlClient = mlClient;
    }

    @GetMapping("/market/status")
    public Map<String, Object> marketStatus() {
        return scraperService.status(null);
    }

    @PostMapping("/market/sync")
    public Map<String, Object> sync() {
        return scraperService.scrape(true);
    }

    @GetMapping("/ml/status")
    public Map<String, Object> mlStatus() {
        return mlClient.status().orElse(Map.of("available", false));
    }
}
