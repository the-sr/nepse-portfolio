package com.nepse.portfolio.service.analysis;

import com.nepse.portfolio.domain.PriceHistory;
import com.nepse.portfolio.dto.analysis.Indicators;

import java.util.List;

public interface IndicatorService {
    Indicators compute(List<PriceHistory> history);
}
