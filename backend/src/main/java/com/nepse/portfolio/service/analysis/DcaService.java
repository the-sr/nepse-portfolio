package com.nepse.portfolio.service.analysis;

import com.nepse.portfolio.dto.analysis.AnalysisDtos.AverageSimulationRequest;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.AverageSimulationResponse;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.DcaOpportunityDto;

import java.util.List;

public interface DcaService {
    List<DcaOpportunityDto> opportunities(Long userId);
    AverageSimulationResponse simulate(Long userId, AverageSimulationRequest request);
}
