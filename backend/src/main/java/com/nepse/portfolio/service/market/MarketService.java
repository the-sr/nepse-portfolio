package com.nepse.portfolio.service.market;

import com.nepse.portfolio.model.Stock;
import com.nepse.portfolio.dto.MarketDtos.MoversDto;
import com.nepse.portfolio.dto.MarketDtos.SectorPerformanceDto;
import com.nepse.portfolio.dto.MarketDtos.StockDetailDto;
import com.nepse.portfolio.dto.MarketDtos.StockDto;

import java.util.List;

public interface MarketService {
    List<StockDto> list(String query, String sector, boolean includeInactive);
    Stock stock(String symbol);
    StockDetailDto detail(String symbol, String range);
    MoversDto movers();
    List<SectorPerformanceDto> sectors();
    List<String> sectorsList();
}
