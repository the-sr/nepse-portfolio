package com.nepse.portfolio.service;

import com.nepse.portfolio.dto.report.ReportDtos.RealizedGainsReport;

import java.time.LocalDate;

public interface ReportService {
    RealizedGainsReport realizedGains(Long userId, LocalDate from, LocalDate to);
    String toCsv(RealizedGainsReport report);
}
