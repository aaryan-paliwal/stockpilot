# Contributing to StockPilot

Thank you for your interest in contributing to **StockPilot**! Please follow the guidelines below to keep the codebase clean, secure, and well-tested.

## Development Workflow

1. **Fork & Clone** the repository:
   ```bash
   git clone https://github.com/aaryan-paliwal/stockpilot.git
   cd stockpilot
   ```
2. **Create a Feature Branch** from `main`:
   ```bash
   git checkout -b feat/your-feature-name
   ```
3. **Configure Environment Variables**:
   - Copy `.env.example` to `.env` (and `frontend/.env` if running OAuth locally).
   - Never commit `.env` files, credentials, or local IDE configs (`.idea/`, `.vscode/`).

## Running Checks Before Opening a PR

### Backend (`inventory-backend/`)
```bash
cd inventory-backend
./mvnw clean verify
```

### Frontend (`frontend/`)
```bash
cd frontend
npm ci --legacy-peer-deps
npm run lint
npm run build
```

## Pull Request Guidelines
- Keep commits focused and descriptive (e.g., `feat: ...`, `fix: ...`, `docs: ...`).
- Ensure all unit tests and frontend production builds pass with zero errors.
- Update `README.md` or `.env.example` if you introduce new configuration variables or endpoints.
