# Option Pricing for 0DTE SPX Strategies — Design

Date: 2026-10-18. Status: approved, pending spec review.

## Purpose

Strategy classes need option and credit-spread prices during backtests, when the data contains SPX and VIX but no option quotes. This adds a pricing library that strategies call. It is not exposed through REST or the CLI.

Scope is **0DTE**: every option expires at the close of the current trading day.

## Placement

The package is `com.tradeevo.pricing` in `tradeevo-app`, beside `strategies`. It is plain Java with no Spring. It stays out of `tradeevo-core`, because the core holds no instrument-specific logic (see `docs/tradeevo-design-architecture.md`). All functions are pure and deterministic.

## Time basis

Almost all variance on expiry day accrues during trading minutes. Spreading VIX's annual variance over the 525,600 calendar minutes of a year underprices 0DTE options by about √5.35 ≈ 2.3×. Annual variance is therefore assigned to trading time only:

```
minutesLeft = minutes from now to expiration time (0 if now ≥ expiration)
years       = minutesLeft / (tradingDaysPerYear × 390)
σ           = VIX / 100
EM          = SPX × σ × √years                    (expected move, in index points)
```

Defaults: expiration time 16:00 (exchange time), `tradingDaysPerYear` 252. On early-close days the strategy sets the expiration time (e.g. 13:00), and each minute keeps the same variance. `now` is a `LocalTime` in exchange time. Seconds count, as fractional minutes.

Known simplifications, documented in the Javadoc:
- The session carries the whole day's σ²/252. The overnight share (roughly 20–25%) has already passed by the open, so this slightly overstates the variance left.
- Variance is uniform within the day. The open, the close and events such as FOMC are not weighted.

## Components

| Type | Responsibility |
|---|---|
| `OptionType` | `CALL`, `PUT` |
| `SpreadDirection` | `BULL_PUT` (short put spread, long strike < short strike), `BEAR_CALL` (short call spread, long strike > short strike) |
| `ZeroDteTime` | `minutesLeft(now)` and `years(now)`, from `expiration` (default 16:00) and `tradingDaysPerYear` (default 252) |
| `ExpectedMove` | `points(spx, vix, years)` gives EM; `distance(spx, shortStrike, direction, em)` gives N = D / EM |
| `NormalDistribution` | `cdf(x)` with Hart's double-precision algorithm (West 2005), absolute error ≈ 1e-15 |
| `BlackScholes` | `price(spot, strike, sigma, years, rate, dividend, type)`: European; when `years ≤ 0` or `sigma = 0`, returns the intrinsic value |
| `ZeroDteOptionPricer` | `price(spx, strike, vix, now, type)`: σ = VIX/100, years from `ZeroDteTime`, plus `rate` and `dividend` (default 0) |
| `SpreadPricer` (interface) | `price(spx, vix, now, direction, shortStrike, longStrike)`: the spread's value to close, in `[0, width]` |
| `BlackScholesSpreadPricer` | short leg minus long leg via `BlackScholes`, clamped to `[0, width]` |
| `EmpiricalSpreadPricer` | the calibrated formula below, with all constants as parameters |
| `SpreadPricers` | `fromJson(JsonNode)` builds a `SpreadPricer` from config and collects errors |

Distance D from the short strike is positive when out of the money:
- `BULL_PUT`: D = SPX − shortStrike
- `BEAR_CALL`: D = shortStrike − SPX

Width = |longStrike − shortStrike|. Intrinsic value = min(max(−D, 0), width).

## Empirical model

This is the user's calibrated credit-spread formula, with T = minutesLeft:

```
C(T) = clamp(cScale × (T / cPivot)^cExponent, cMin, cMax)
M(T) = 1 + mHeight × exp(−(T − mCenter)² / (2 × mWidth²))     midday stickiness
A(T) = 1 + aHeight × exp(−(T − aCenter)² / (2 × aWidth²))     early-afternoon support
f(N) = min(width × base × exp(−decay × N) × C × M × A, width)
```

| Parameter | Default | Parameter | Default |
|---|---|---|---|
| `base` | 0.3 | `mHeight` | 0.22 |
| `decay` | 3.8 | `mCenter` | 240 |
| `cScale` | 1.34 | `mWidth` | 55 |
| `cPivot` | 138 | `aHeight` | 0.22 |
| `cExponent` | 0.45 | `aCenter` | 150 |
| `cMin` | 0.8 | `aWidth` | 45 |
| `cMax` | 2.1 | | |

Pricing rules:
- **T ≤ 0:** returns the intrinsic value. (The original returned 0, which counted spreads that finish in the money as full wins.)
- **N ≥ 0 (out of the money):** returns f(N), identical to the original function.
- **N < 0 (in the money):** returns max(intrinsic, width − (width − f₀) × exp(−decay × |N|)), where f₀ = f(0). (The original returned the intrinsic value, which dropped by about 0.65 × width at the strike.) This is continuous at the strike, approaches the width deep in the money, and never falls below intrinsic.

The out-of-the-money values are much lower than Black-Scholes beyond about 0.3 EM. For example, at 12:00 and N = 1 this model gives 0.015 × width, against about 0.16 for Black-Scholes. The constants are kept as given. Comparing backtest runs that use the two models is how to judge them.

## Configuration

A strategy reads an optional `pricing` block from its `settings`:

```json
"pricing": {
  "model": "empirical",
  "params": { "decay": 3.8, "expiration": "16:00", "tradingDaysPerYear": 252 }
}
```

- `model` is `"black-scholes"` or `"empirical"`.
- Common params: `expiration` (`HH:mm`) and `tradingDaysPerYear` (positive integer).
- Black-Scholes params: `rate` and `dividend` (finite numbers).
- Empirical params: the table above. Every parameter must be finite. `cMin ≤ cMax`. `decay`, `cPivot`, `mWidth` and `aWidth` must be > 0.
- Parameters left out take their defaults.
- `SpreadPricers.validate(JsonNode, String path)` returns error strings such as `settings.pricing.params.decay: must be > 0`. They are written in the same style as `Strategy.validate` errors, so a strategy can add them to its own errors. Unknown models or parameters are errors.

## Validation

At call time, SPX, the strikes and VIX must be finite and > 0. The short and long strikes must differ, and their order must match the direction. A violation throws `IllegalArgumentException` with a message naming the bad input.

## Testing

- **`NormalDistribution`:** reference values at 0, ±1, ±1.96 and ±5, and symmetry.
- **`BlackScholes`:**
  - Matches a published reference value (Hull: S=42, K=40, r=0.10, σ=0.20, T=0.5 gives call 4.76 and put 0.81).
  - Put-call parity holds to 1e-10.
  - At or after expiry, and when σ = 0, the price equals the intrinsic value.
  - The price rises with σ and with time left.
- **`ZeroDteOptionPricer`:**
  - With SPX 5000, VIX 15 at 10:00, the ATM straddle is ≈ $36, within the 0.7–1% of SPX market rule of thumb.
  - From 16:00 onwards, the price equals the intrinsic value.
- **`ZeroDteTime`:** 16:00 gives 0 minutes; 15:59:30 gives 0.5 minutes; after 16:00 gives 0; a custom expiration and trading-days setting is honored.
- **`EmpiricalSpreadPricer`:**
  - Out of the money, matches a port of the original function to 1e-12 at sample points.
  - At N = 0, the values on either side differ by < 1e-9.
  - At T ≤ 0, returns the intrinsic value, both in and out of the money.
  - Always in `[intrinsic, width]`.
  - Non-increasing in N (strictly decreasing with the defaults, where f₀ < width).
- **`BlackScholesSpreadPricer`:** in `[0, width]`; a narrow spread at the money ≈ 0.5 × width; at expiry equals the intrinsic value.
- **Comparison:** a test that pins both models' prices at N ∈ {0, 0.5, 1, 1.28} at 12:00. It documents the gap and catches unintended changes.
- **`SpreadPricers`:**
  - Defaults are applied.
  - Each model is chosen correctly.
  - Errors are reported for an unknown model, an unknown parameter, a non-finite value, `cMin > cMax` and a bad `expiration`.

## Out of scope

- A strategy class that uses these pricers.
- Volatility skew or smile.
- The overnight variance share.
- Re-fitting the empirical constants.
- Options with more than one day to expiry.
- REST or CLI pricing endpoints.
