from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from .config import CORS_ORIGINS
from .db import load_all_prices, load_symbol_prices
from .features import latest_feature_row
from .model import Recommender

recommender = Recommender()


@asynccontextmanager
async def lifespan(app: FastAPI):
    recommender.load()
    yield


app = FastAPI(
    title="NEPSE Recommender",
    version="1.0.0",
    description="RandomForest buy/hold/sell recommendations trained on NEPSE price history",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


class PredictRequest(BaseModel):
    symbol: str
    lossPercent: Optional[float] = 0.0
    holdingDays: Optional[int] = 0
    concentration: Optional[float] = 0.0


class PredictResponse(BaseModel):
    symbol: str
    action: str
    confidence: float
    probabilities: dict[str, float]
    modelVersion: str
    reasons: list[str]


def build_reasons(row, request: PredictRequest, probabilities) -> list[str]:
    reasons = []

    sma_gap = float(row["sma_gap"])
    if sma_gap > 0.005:
        reasons.append(f"20-day average is {sma_gap * 100:.2f}% above the 50-day average (bullish structure)")
    elif sma_gap < -0.005:
        reasons.append(f"20-day average is {abs(sma_gap) * 100:.2f}% below the 50-day average (bearish structure)")
    else:
        reasons.append("20-day and 50-day averages are close, so trend conviction is low")

    rsi = float(row["rsi14"])
    if rsi < 30:
        reasons.append(f"RSI {rsi:.1f} is oversold")
    elif rsi > 70:
        reasons.append(f"RSI {rsi:.1f} is overbought")
    else:
        reasons.append(f"RSI {rsi:.1f} is in a neutral range")

    ret7 = float(row["return_7d"]) * 100
    reasons.append(f"7-day momentum is {ret7:+.2f}%")

    volume_ratio = float(row["volume_ratio"])
    if volume_ratio > 1.5:
        reasons.append(f"Volume is {volume_ratio:.1f}x its 20-day average")
    elif volume_ratio < 0.6:
        reasons.append(f"Volume is only {volume_ratio:.1f}x its 20-day average")

    if request.lossPercent and request.lossPercent <= -5:
        reasons.append(f"Your open position is down {abs(request.lossPercent):.2f}%; review the averaging simulator")
    if request.concentration and request.concentration > 25:
        reasons.append(f"Position is {request.concentration:.1f}% of the portfolio, which is concentrated")

    top = max(probabilities, key=probabilities.get)
    reasons.append(f"Model most likely outcome: {top} ({probabilities[top] * 100:.1f}% probability)")
    return reasons


@app.get("/health")
def health():
    return {"status": "ok", "modelLoaded": recommender.loaded}


@app.get("/model/status")
def model_status():
    metadata = recommender.metadata()
    metadata["available"] = True
    return metadata


@app.post("/train")
def train():
    try:
        prices = load_all_prices()
        if prices.empty:
            raise HTTPException(status_code=422, detail="No price history in the database")
        metadata = recommender.train(prices)
        return {"status": "trained", **metadata}
    except HTTPException:
        raise
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc))
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=f"Training failed: {exc}")


@app.post("/predict", response_model=PredictResponse)
def predict(request: PredictRequest):
    if not recommender.loaded:
        raise HTTPException(status_code=503, detail="Model is not trained yet; POST /train first")

    prices = load_symbol_prices(request.symbol)
    if prices.empty:
        raise HTTPException(status_code=404, detail=f"No price history for {request.symbol}")

    row = latest_feature_row(prices)
    if row is None:
        raise HTTPException(status_code=422, detail="Not enough price history to compute indicators")

    prediction = recommender.predict(row)
    reasons = build_reasons(row, request, prediction["probabilities"])

    return PredictResponse(
        symbol=request.symbol.upper(),
        action=prediction["action"],
        confidence=prediction["confidence"],
        probabilities=prediction["probabilities"],
        modelVersion=str(recommender.bundle.get("version")),
        reasons=reasons,
    )
