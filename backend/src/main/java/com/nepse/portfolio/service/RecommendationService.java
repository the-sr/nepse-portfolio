package com.nepse.portfolio.service;

import com.nepse.portfolio.domain.Stock;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.RecommendationDto;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.StockAnalysisDto;
import com.nepse.portfolio.dto.analysis.Indicators;

import java.util.List;

public interface RecommendationService {
    List<RecommendationDto> recommendations(Long userId);
    StockAnalysisDto analyse(Long userId, String symbol);
    Indicators indicatorsFor(Stock stock);
}
