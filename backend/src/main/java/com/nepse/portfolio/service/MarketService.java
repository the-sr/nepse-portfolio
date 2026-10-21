package com.nepse.portfolio.service;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.dto.market.MarketDtos.MoversDto;
import com.nepse.portfolio.dto.market.MarketDtos.SectorPerformanceDto;
import com.nepse.portfolio.dto.market.MarketDtos.StockDetailDto;
import com.nepse.portfolio.dto.market.MarketDtos.StockDto;

import java.util.List;

public interface MarketService {
    List<StockDto> list(String query, String sector, boolean includeInactive);
    Stock stock(String symbol);
    StockDetailDto detail(String symbol, String range);
    MoversDto movers();
    List<SectorPerformanceDto> sectors();
    List<String> sectorsList();
}
