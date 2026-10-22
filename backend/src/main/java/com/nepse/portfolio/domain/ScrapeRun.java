package com.nepse.portfolio.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scrape_runs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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


    public ScrapeRun(String source) {
        this.source = source;
    }

    public void finish(boolean success, int rowsParsed, String message) {
        this.finishedAt = Instant.now();
        this.success = success;
        this.rowsParsed = rowsParsed;
        this.message = message;
    }
}
