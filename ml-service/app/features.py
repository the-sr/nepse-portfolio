import numpy as np
import pandas as pd

from .config import FEATURES


def _rsi(close: pd.Series, period: int = 14) -> pd.Series:
    delta = close.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)
    avg_gain = gain.ewm(alpha=1 / period, adjust=False).mean()
    avg_loss = loss.ewm(alpha=1 / period, adjust=False).mean()
    rs = avg_gain / avg_loss.replace(0, np.nan)
    rsi = 100 - (100 / (1 + rs))
    return rsi.fillna(50)


def add_features(frame: pd.DataFrame) -> pd.DataFrame:
    """Adds technical features. Expects one symbol ordered by trade_date."""
    data = frame.copy()
    close = data["close_price"].astype(float)
    volume = data["volume"].astype(float)

    sma20 = close.rolling(20).mean()
    sma50 = close.rolling(50).mean()

    data["sma_gap"] = (sma20 - sma50) / sma50.replace(0, np.nan)
    data["rsi14"] = _rsi(close, 14)
    data["return_1d"] = close.pct_change(1)
    data["return_7d"] = close.pct_change(7)
    data["return_30d"] = close.pct_change(30)
    data["volatility_20d"] = close.pct_change().rolling(20).std()
    data["volume_ratio"] = volume / volume.rolling(20).mean().replace(0, np.nan)
    data["range_pct"] = (data["high_price"].astype(float) - data["low_price"].astype(float)) / close

    data["forward_return_5d"] = close.shift(-5) / close - 1
    return data


def training_frame(prices: pd.DataFrame) -> pd.DataFrame:
    """Builds a labelled frame across all symbols."""
    frames = []
    for symbol, group in prices.groupby("symbol"):
        enriched = add_features(group)
        enriched["symbol"] = symbol
        frames.append(enriched)
    if not frames:
        return pd.DataFrame(columns=FEATURES + ["label", "trade_date"])

    data = pd.concat(frames, ignore_index=True)
    bins = [-np.inf, -0.03, -0.005, 0.005, 0.03, np.inf]
    data["label"] = pd.cut(data["forward_return_5d"], bins=bins, labels=False)
    data = data.dropna(subset=FEATURES + ["label"])
    data["label"] = data["label"].astype(int)
    data["trade_date"] = pd.to_datetime(data["trade_date"])
    return data.sort_values("trade_date").reset_index(drop=True)


def latest_feature_row(prices: pd.DataFrame) -> pd.Series | None:
    """Returns the most recent row with a complete technical feature vector."""
    if prices.empty:
        return None
    enriched = add_features(prices)
    enriched = enriched.dropna(subset=FEATURES)
    if enriched.empty:
        return None
    return enriched.iloc[-1]
