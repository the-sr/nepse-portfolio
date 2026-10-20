import numpy as np
import pandas as pd

from app.features import add_features, latest_feature_row, training_frame


def synthetic_prices(days: int = 120) -> pd.DataFrame:
    rng = np.random.default_rng(7)
    close = 500 + np.cumsum(rng.normal(0, 4, days))
    frame = pd.DataFrame({
        "symbol": ["TEST"] * days,
        "trade_date": pd.date_range("2025-01-01", periods=days, freq="D"),
        "open_price": close - 1,
        "high_price": close + 3,
        "low_price": close - 3,
        "close_price": close,
        "volume": rng.integers(1000, 5000, days),
    })
    return frame


def test_features_are_computed():
    enriched = add_features(synthetic_prices())
    assert "sma_gap" in enriched.columns
    assert enriched["rsi14"].between(0, 100).all()


def test_training_frame_has_labels():
    data = training_frame(synthetic_prices(200))
    assert not data.empty
    assert set(data["label"].unique()).issubset({0, 1, 2, 3, 4})


def test_latest_row_has_all_features():
    row = latest_feature_row(synthetic_prices(200))
    assert row is not None
    for feature in ["sma_gap", "rsi14", "return_7d", "volume_ratio"]:
        assert not pd.isna(row[feature])
