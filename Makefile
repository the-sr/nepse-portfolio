SHELL := /bin/bash
COMPOSE := docker compose

.PHONY: help up down restart build logs ps clean reset train-ml \
	backend-build backend-test backend-run frontend-install frontend-build \
	frontend-test frontend-run ml-install ml-test ml-run test

help: ## Show available targets
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

up: ## Build images and start the full stack
	$(COMPOSE) up --build -d
	@echo "Frontend: http://localhost:$${FRONTEND_PORT:-3000}"
	@echo "API:      http://localhost:$${BACKEND_PORT:-8080}/api"
	@echo "Swagger:  http://localhost:$${BACKEND_PORT:-8080}/swagger-ui.html"
	@echo "ML:       http://localhost:$${ML_PORT:-8000}/docs"

down: ## Stop the stack
	$(COMPOSE) down

restart: ## Restart all services
	$(COMPOSE) restart

build: ## Build all images without starting
	$(COMPOSE) build

logs: ## Follow logs from all services
	$(COMPOSE) logs -f --tail=200

ps: ## Show service status
	$(COMPOSE) ps

clean: ## Stop and remove volumes (deletes database!)
	$(COMPOSE) down -v

reset: clean up ## Recreate everything from scratch (deletes database!)

train-ml: ## Train the ML model on the data currently in Postgres
	curl -fsS -X POST http://localhost:$${ML_PORT:-8000}/train | python3 -m json.tool

backend-build: ## Compile the backend
	cd backend && ./mvnw -B -DskipTests package

backend-test: ## Run backend tests
	cd backend && ./mvnw -B test

backend-run: ## Run the backend against local Postgres
	cd backend && ./mvnw spring-boot:run

frontend-install: ## Install frontend dependencies
	cd frontend && npm install

frontend-build: ## Type-check and build the frontend
	cd frontend && npm run build

frontend-test: ## Run frontend unit tests
	cd frontend && npm test

frontend-run: ## Start the Vite dev server
	cd frontend && npm run dev

ml-install: ## Create a venv and install ML dependencies
	cd ml-service && python3 -m venv .venv && .venv/bin/pip install -r requirements.txt

ml-test: ## Run ML service tests
	cd ml-service && .venv/bin/python -m pytest -q

ml-run: ## Run the ML service with auto-reload
	cd ml-service && .venv/bin/uvicorn app.main:app --reload --port 8000

smoke: ## Check a running stack end-to-end (requires curl + jq)
	bash tests/api-smoke.sh

test: backend-test frontend-test ml-test ## Run all test suites
