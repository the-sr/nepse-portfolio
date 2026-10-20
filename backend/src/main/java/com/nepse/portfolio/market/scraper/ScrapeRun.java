package com.nepse.portfolio.market.scraper;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "scrape_runs")
public class ScrapeRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(nullable = false)
    private boolean success = false;

    @Column(name = "rows_parsed", nullable = false)
    private int rowsParsed = 0;

    @Column(nullable = false)
    private String source;

    private String message;

    protected ScrapeRun() {
    }

    public ScrapeRun(String source) {
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public int getRowsParsed() {
        return rowsParsed;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public void finish(boolean success, int rowsParsed, String message) {
        this.finishedAt = Instant.now();
        this.success = success;
        this.rowsParsed = rowsParsed;
        this.message = message;
    }
}
