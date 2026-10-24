package com.nepse.portfolio.controller.portfolio;

import lombok.RequiredArgsConstructor;
import com.nepse.portfolio.dto.portfolio.PortfolioDtos;
import com.nepse.portfolio.service.portfolio.PortfolioService;

import com.nepse.portfolio.dto.portfolio.PortfolioDtos.*;
import com.nepse.portfolio.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final CurrentUser currentUser;


    @GetMapping
    public PortfolioDto portfolio() {
        return portfolioService.portfolio(currentUser.id());
    }

    @GetMapping("/summary")
    public SummaryDto summary() {
        return portfolioService.summary(currentUser.id());
    }

    @GetMapping("/holdings")
    public List<HoldingDto> holdings() {
        return portfolioService.holdings(currentUser.id());
    }

    @GetMapping("/history")
    public List<HistoryPointDto> history(@RequestParam(defaultValue = "90") int days) {
        return portfolioService.history(currentUser.id(), days);
    }

    @GetMapping("/trades")
    public List<TradeDto> trades() {
        return portfolioService.trades(currentUser.id());
    }

    @PostMapping("/trades")
    @ResponseStatus(HttpStatus.CREATED)
    public TradeDto addTrade(@Valid @RequestBody TradeRequest request) {
        return portfolioService.addTrade(currentUser.id(), request);
    }

    /** Fee/tax preview for the trade form — same math as booking, nothing saved. */
    @PostMapping("/trades/preview")
    public ChargesPreviewDto previewTrade(@Valid @RequestBody TradeRequest request) {
        return portfolioService.previewTrade(currentUser.id(), request);
    }

    /** "Sell everything today" capital-gains estimate (WAC basis, current rates). */
    @GetMapping("/cgt")
    public CgtReportDto cgt() {
        return portfolioService.cgtReport(currentUser.id());
    }

    @DeleteMapping("/trades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrade(@PathVariable Long id) {
        portfolioService.deleteTrade(currentUser.id(), id);
    }
}

