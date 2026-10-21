package com.nepse.portfolio.controller;

import com.nepse.portfolio.service.DcaService;
import com.nepse.portfolio.service.RecommendationService;

import com.nepse.portfolio.dto.analysis.AnalysisDtos.AverageSimulationRequest;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.AverageSimulationResponse;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.DcaOpportunityDto;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.RecommendationDto;
import com.nepse.portfolio.dto.analysis.AnalysisDtos.StockAnalysisDto;
import com.nepse.portfolio.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final RecommendationService recommendationService;
    private final DcaService dcaService;
    private final CurrentUser currentUser;

    public AnalysisController(RecommendationService recommendationService,
                              DcaService dcaService,
                              CurrentUser currentUser) {
        this.recommendationService = recommendationService;
        this.dcaService = dcaService;
        this.currentUser = currentUser;
    }

    @GetMapping("/recommendations")
    public List<RecommendationDto> recommendations() {
        return recommendationService.recommendations(currentUser.id());
    }

    @GetMapping("/stocks/{symbol}")
    public StockAnalysisDto analyse(@PathVariable String symbol) {
        return recommendationService.analyse(currentUser.id(), symbol);
    }

    @GetMapping("/opportunities")
    public List<DcaOpportunityDto> opportunities() {
        return dcaService.opportunities(currentUser.id());
    }

    @PostMapping("/average-simulation")
    public AverageSimulationResponse simulate(@Valid @RequestBody AverageSimulationRequest request) {
        return dcaService.simulate(currentUser.id(), request);
    }
}
