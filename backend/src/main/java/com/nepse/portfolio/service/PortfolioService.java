package com.nepse.portfolio.service;

import com.nepse.portfolio.dto.portfolio.PortfolioDtos.HoldingDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.HistoryPointDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.PortfolioDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.SummaryDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.TradeDto;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos.TradeRequest;

import java.util.List;

public interface PortfolioService {
    PortfolioDto portfolio(Long userId);
    SummaryDto summary(Long userId);
    List<HoldingDto> holdings(Long userId);
    List<TradeDto> trades(Long userId);
    TradeDto addTrade(Long userId, TradeRequest request);
    void deleteTrade(Long userId, Long tradeId);
    List<HistoryPointDto> history(Long userId, int days);
}
