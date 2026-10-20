package com.nepse.portfolio.market.nepse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry from {@code /api/nots/company/list}. {@code instrumentType} is
 * NEPSE's own classification: Equity, Mutual Funds, Non-Convertible Debentures
 * or Preference Shares.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NepseCompany(
        String symbol,
        String companyName,
        String securityName,
        String sectorName,
        String instrumentType,
        String status
) {
}
