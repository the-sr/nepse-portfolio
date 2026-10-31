package com.nepse.portfolio.service.portfolio;

import com.nepse.portfolio.dto.PortfolioDtos.ChargesPreviewDto;
import com.nepse.portfolio.dto.PortfolioDtos.CgtReportDto;
import com.nepse.portfolio.dto.PortfolioDtos.HoldingDto;
import com.nepse.portfolio.dto.PortfolioDtos.HistoryPointDto;
import com.nepse.portfolio.dto.PortfolioDtos.PortfolioDto;
import com.nepse.portfolio.dto.PortfolioDtos.SummaryDto;
import com.nepse.portfolio.dto.PortfolioDtos.TradeDto;
import com.nepse.portfolio.dto.PortfolioDtos.TradeRequest;

import java.util.List;

public interface PortfolioService {
    PortfolioDto portfolio(Long userId);
    SummaryDto summary(Long userId);
    List<HoldingDto> holdings(Long userId);
    List<TradeDto> trades(Long userId);
    TradeDto addTrade(Long userId, TradeRequest request);
    ChargesPreviewDto previewTrade(Long userId, TradeRequest request);
    CgtReportDto cgtReport(Long userId);
    void deleteTrade(Long userId, Long tradeId);
    List<HistoryPointDto> history(Long userId, int days);
}
