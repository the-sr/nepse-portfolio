package com.nepse.portfolio.controller.market;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.dto.market.MarketDtos;
import com.nepse.portfolio.service.market.MarketService;
import com.nepse.portfolio.service.market.ScraperService;

import com.nepse.portfolio.dto.market.MarketDtos.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final MarketService marketService;
    private final ScraperService scraperService;


    @GetMapping("/stocks")
    public List<StockDto> stocks(@RequestParam(required = false) String query,
                                 @RequestParam(required = false) String sector,
                                 @RequestParam(defaultValue = "false") boolean includeInactive) {
        return marketService.list(query, sector, includeInactive);
    }

    @GetMapping("/stocks/{symbol}")
    public StockDetailDto detail(@PathVariable String symbol,
                                 @RequestParam(defaultValue = "6M") String range) {
        return marketService.detail(symbol, range);
    }

    @GetMapping("/stocks/{symbol}/history")
    public List<HistoryPoint> history(@PathVariable String symbol,
                                      @RequestParam(defaultValue = "6M") String range) {
        return marketService.detail(symbol, range).history();
    }

    @GetMapping("/movers")
    public MoversDto movers() {
        return marketService.movers();
    }

    @GetMapping("/sectors")
    public List<SectorPerformanceDto> sectors() {
        return marketService.sectors();
    }

    @GetMapping("/sector-names")
    public List<String> sectorNames() {
        return marketService.sectorsList();
    }

    /** Live market-open verdict + last feed result, for the UI status pill. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return scraperService.publicStatus();
    }
}
