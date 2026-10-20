import os
from datetime import datetime, timezone

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from .config import ACTIONS, FEATURES, MODEL_PATH
from .features import training_frame


class Recommender:
    def __init__(self):
        self.bundle = None
        self.load()

    def load(self):
        if os.path.exists(MODEL_PATH):
            self.bundle = joblib.load(MODEL_PATH)

    @property
    def loaded(self) -> bool:
        return self.bundle is not None

    def metadata(self) -> dict:
        if not self.bundle:
            return {"loaded": False, "modelVersion": None, "samples": 0, "accuracy": None}
        return {
            "loaded": True,
            "modelVersion": self.bundle.get("version"),
            "trainedAt": self.bundle.get("trained_at"),
            "samples": self.bundle.get("samples"),
            "accuracy": self.bundle.get("accuracy"),
            "features": FEATURES,
            "classes": ACTIONS,
            "featureImportance": self.bundle.get("feature_importance", {}),
        }

    def train(self, prices: pd.DataFrame) -> dict:
        data = training_frame(prices)
        if len(data) < 200:
            raise ValueError(f"Not enough training rows ({len(data)}); need at least 200")
        if data["label"].nunique() < 2:
            raise ValueError("Training data contains only one label")

        X = data[FEATURES]
        y = data["label"]
        split = int(len(data) * 0.8)
        X_train, X_test = X.iloc[:split], X.iloc[split:]
        y_train, y_test = y.iloc[:split], y.iloc[split:]

        pipeline = Pipeline([
            ("scaler", StandardScaler()),
            ("model", RandomForestClassifier(
                n_estimators=250,
                max_depth=7,
                min_samples_leaf=15,
                class_weight="balanced_subsample",
                random_state=42,
                n_jobs=-1,
            )),
        ])
        pipeline.fit(X_train, y_train)
        predictions = pipeline.predict(X_test)
        accuracy = float(accuracy_score(y_test, predictions)) if len(y_test) else 0.0

        importances = pipeline.named_steps["model"].feature_importances_
        version = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
        self.bundle = {
            "pipeline": pipeline,
            "version": version,
            "trained_at": datetime.now(timezone.utc).isoformat(),
            "samples": int(len(data)),
            "accuracy": round(accuracy, 4),
            "feature_importance": {
                name: round(float(value), 4) for name, value in zip(FEATURES, importances)
            },
        }

        os.makedirs(os.path.dirname(MODEL_PATH), exist_ok=True)
        joblib.dump(self.bundle, MODEL_PATH)
        return self.metadata()

    def predict(self, features: pd.Series) -> dict:
        if not self.bundle:
            raise RuntimeError("Model is not trained yet")

        pipeline = self.bundle["pipeline"]
        row = pd.DataFrame([features[FEATURES].astype(float).to_dict()])
        probabilities = pipeline.predict_proba(row)[0]
        classes = list(pipeline.named_steps["model"].classes_)

        by_action = {}
        for index, probability in zip(classes, probabilities):
            by_action[ACTIONS[int(index)]] = round(float(probability), 4)

        best_index = int(np.argmax(probabilities))
        best_class = int(classes[best_index])
        action = ACTIONS[best_class]
        confidence = float(probabilities[best_index])

        return {
            "action": action,
            "confidence": round(confidence, 4),
            "probabilities": by_action,
            "confidenceGap": round(float(sorted(probabilities)[-1] - sorted(probabilities)[-2]), 4)
            if len(probabilities) > 1 else 0.0,
        }
