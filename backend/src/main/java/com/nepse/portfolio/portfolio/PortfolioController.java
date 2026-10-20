package com.nepse.portfolio.portfolio;

import com.nepse.portfolio.portfolio.PortfolioDtos.*;
import com.nepse.portfolio.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final CurrentUser currentUser;

    public PortfolioController(PortfolioService portfolioService, CurrentUser currentUser) {
        this.portfolioService = portfolioService;
        this.currentUser = currentUser;
    }

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

    @DeleteMapping("/trades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrade(@PathVariable Long id) {
        portfolioService.deleteTrade(currentUser.id(), id);
    }
}
