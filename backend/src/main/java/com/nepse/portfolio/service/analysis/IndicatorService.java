package com.nepse.portfolio.service.analysis;

import com.nepse.portfolio.model.PriceHistory;
import com.nepse.portfolio.dto.Indicators;

import java.util.List;

public interface IndicatorService {
    Indicators compute(List<PriceHistory> history);
}
