# TradeEvo

TradeEvo is an AI-powered trading strategy research and evolution platform. It combines systematic backtesting with AI-driven strategy generation and refinement to research, evolve, and validate options trading strategies.

## Initial Target

**Market:** SPX 0DTE (zero days to expiration) options

**Strategies:**

- **BPSC — Bull Put Credit Spread**: a defined-risk, bullish/neutral credit spread that sells a higher-strike put and buys a lower-strike put for downside protection, collecting net premium.
- **BCSC — Bear Call Credit Spread**: a defined-risk, bearish/neutral credit spread that sells a lower-strike call and buys a higher-strike call for upside protection, collecting net premium.

## Tech Stack

- **Backend:** Python (data ingestion, strategy logic, backtesting engine, research/evolution pipeline, API)
- **Frontend:** Web application (dashboard/UI, consumes the backend API)

## Project Structure

```
TradeEvo/
├── backend/
│   ├── data/            # Market data ingestion & storage (SPX 0DTE chains, etc.)
│   ├── strategies/
│   │   ├── bpsc/        # Bull Put Credit Spread
│   │   └── bcsc/        # Bear Call Credit Spread
│   ├── backtesting/     # Backtest engine
│   ├── research/        # AI-driven strategy evolution / experiments
│   ├── api/             # Web API serving the frontend
│   └── tests/
├── frontend/
│   ├── src/
│   └── public/
└── docs/
```

## Status

Early scaffolding — no implementation yet.
