package com.nepse.portfolio.service.portfolio;

import com.nepse.portfolio.utils.portfolio.TradingRates;

/**
 * Resolves the Nepali cost/tax schedule for a user through the precedence
 * chain: admin global rates (app_settings) then the user's own overrides,
 * over the verified defaults. Per-trade overrides live on the trade row
 * itself, so they never reach this service.
 */
public interface TradingRatesService {

    /** Fully merged schedule used by every cost calculation for this user. */
    TradingRates effective(Long userId);

    /** The user's own overlay (what they would edit). Never null. */
    TradingRates userRates(Long userId);

    /** Partial update: null fields keep the user's current values. */
    TradingRates updateUser(Long userId, TradingRates patch);

    /** Admin-global schedule merged over the defaults. */
    TradingRates global();

    /** Partial update of the admin-global schedule. */
    TradingRates updateGlobal(TradingRates patch);
}
