package com.nepse.portfolio.utils;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One entry from {@code /api/nots/security?nonDelisted=true}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NepseSecurity(
        String symbol,
        String securityName,
        String activeStatus
) {
}
