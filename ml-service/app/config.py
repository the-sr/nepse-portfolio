import os

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://nepse:nepse@localhost:5432/nepse_portfolio",
)
MODEL_DIR = os.getenv("MODEL_DIR", "/app/models")
MODEL_PATH = os.path.join(MODEL_DIR, "recommender.joblib")
CORS_ORIGINS = os.getenv("CORS_ORIGINS", "*").split(",")

ACTIONS = ["STRONG_SELL", "SELL", "HOLD", "BUY", "STRONG_BUY"]
FEATURES = [
    "sma_gap",
    "rsi14",
    "return_1d",
    "return_7d",
    "return_30d",
    "volatility_20d",
    "volume_ratio",
    "range_pct",
]
