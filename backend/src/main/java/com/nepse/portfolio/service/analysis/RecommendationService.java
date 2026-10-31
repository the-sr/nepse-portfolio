package com.nepse.portfolio.service.analysis;

import com.nepse.portfolio.model.Stock;
import com.nepse.portfolio.dto.AnalysisDtos.RecommendationDto;
import com.nepse.portfolio.dto.AnalysisDtos.StockAnalysisDto;
import com.nepse.portfolio.dto.Indicators;

import java.util.List;

public interface RecommendationService {
    List<RecommendationDto> recommendations(Long userId);
    StockAnalysisDto analyse(Long userId, String symbol);
    Indicators indicatorsFor(Stock stock);
}
