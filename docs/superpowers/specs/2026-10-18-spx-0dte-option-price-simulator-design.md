# SPX 0DTE Option Price Simulator Design

Date: 2026-10-18. Status: approved and implemented.

## Purpose

Strategy classes need option and credit-spread prices during backtests, when the data contains SPX and VIX but no option quotes. This adds a pricing library that strategies call. It is not exposed through REST or the CLI.

Scope is **0DTE**: every option expires at the close of the current trading day.

## Placement

- **Code:** package `com.tradeevo.pricing` in `tradeevo-app`, beside `strategies`. It is plain Java with no Spring, apart from one bean definition in the existing configuration. It stays out of `tradeevo-core`, because the core holds no instrument-specific logic (see `docs/tradeevo-design-architecture.md`). All pricing functions are pure and deterministic.
- **Parameters:** every model parameter lives in **`config/pricing-models.json`** at the project root, tracked in git. No numeric parameter is hard-coded in Java. You change a parameter by editing the file and rerunning; no rebuild is needed.

## Time basis

Almost all variance on expiry day accrues during trading minutes. Spreading VIX's annual variance over the 525,600 calendar minutes of a year underprices 0DTE options by about √5.35 ≈ 2.3×. Annual variance is therefore assigned to trading time only:

```
minutesLeft = minutes from now to expiration (0 if now ≥ expiration)
years       = minutesLeft / (tradingDaysPerYear × sessionMinutes)
σ           = VIX / 100
EM          = SPX × σ × √years                    (expected move, in index points)
```

Black-Scholes prices with an adjustable effective volatility:

```
σ_effective = VIX / 100 × ivMultiplier
```

`ivMultiplier` is a Black-Scholes parameter (shipped value 1.0). It calibrates for the gap between VIX, which is 30-day implied volatility, and the volatility the market prices into same-day options. EM and N always use the raw σ, because the empirical model was calibrated on raw VIX.

`expiration`, `tradingDaysPerYear` and `sessionMinutes` come from the parameters file (shipped values: 16:00, 252, 390). On early-close days the strategy overrides `expiration` (e.g. 13:00), and each minute keeps the same variance. `now` is a `LocalTime` in exchange time. Seconds count, as fractional minutes.

Known simplifications, documented in the Javadoc:
- The session carries the whole day's σ²/252. The overnight share (roughly 20–25%) has already passed by the open, so this slightly overstates the variance left.
- Variance is uniform within the day. The open, the close and events such as FOMC are not weighted.

## Parameters file

`config/pricing-models.json`, as shipped:

```json
{
  "defaultModel": "black-scholes",
  "time": {
    "expiration": "16:00",
    "tradingDaysPerYear": 252,
    "sessionMinutes": 390
  },
  "models": {
    "black-scholes": {
      "ivMultiplier": 1.0,
      "rate": 0.0,
      "dividend": 0.0
    },
    "empirical": {
      "base": 0.3,
      "decay": 3.8,
      "cScale": 1.34, "cPivot": 138, "cExponent": 0.45, "cMin": 0.8, "cMax": 2.1,
      "mHeight": 0.22, "mCenter": 240, "mWidth": 55,
      "aHeight": 0.22, "aCenter": 150, "aWidth": 45
    }
  }
}
```

Rules:
- **Complete:** every parameter shown is required. A missing parameter is an error; Java does not fill it in.
- **Strict:** unknown sections, models or parameters are errors, so a typo can't be silently ignored.
- **Valid values:**
  - Every number must be finite.
  - `expiration` must be `HH:mm`.
  - `tradingDaysPerYear` and `sessionMinutes` must be positive integers.
  - `defaultModel` must name a model in `models`.
  - `ivMultiplier`, `decay`, `cPivot`, `mWidth` and `aWidth` must be > 0.
  - `cMin` ≤ `cMax`.
- **Errors are reported together,** each with its JSON path, e.g. `models.empirical.decay: must be > 0`.

Location: `tradeevo.pricing-file` in `TradeEvoProperties`, defaulting to `./config/pricing-models.json`. It can be overridden like the other settings (`--tradeevo.pricing-file=...` or an environment variable).

Loading: the file is read and validated **on first use**, then cached. A strategy that doesn't price options never needs the file, so the CLI and server still work from another directory. If the file is missing or invalid when a pricer is requested, the run fails with `InvalidConfig`, listing every error. The file is read at most once per process, so editing it takes effect on the next run of the CLI or after a server restart.

## Strategy selection and overrides

A strategy reads an optional `pricing` block from its `settings`. It chooses the model and can override any parameter from the file:

```json
"pricing": {
  "model": "empirical",
  "overrides": { "decay": 3.5, "expiration": "13:00" }
}
```

- `model` is `"black-scholes"` or `"empirical"`. When it is left out, or the whole `pricing` block is absent, the file's `defaultModel` is used (shipped: `black-scholes`).
- Example: `"pricing": {"overrides": {"ivMultiplier": 1.15}}` uses Black-Scholes with 15% more volatility than VIX implies.
- An override can set a `time` parameter or a parameter of the chosen model. The validation rules for the file apply to overrides too.
- Override errors are reported with the strategy path, e.g. `settings.pricing.overrides.decay: must be > 0`, in the same style as `Strategy.validate`, so a strategy can add them to its own errors.

## Reproducibility

Prices now depend on a file outside the strategy config and data, so the run's existing hashes don't cover it. To close that gap, `SpreadPricers.create(...)` returns the pricer together with its **resolved parameters**: model name, time parameters and model parameters after overrides. The strategy copies them into its `assumptions` under `pricing`. They are then saved in the run manifest, and `RunComparator` already reports runs whose `assumptions` differ. A file edit between runs is therefore visible in `compare`.

## Components

| Type | Responsibility |
|---|---|
| `OptionType` | `CALL`, `PUT` |
| `SpreadDirection` | `BULL_PUT` (short put spread, long strike < short strike), `BEAR_CALL` (short call spread, long strike > short strike) |
| `TimeParams` (record) | `expiration`, `tradingDaysPerYear`, `sessionMinutes` |
| `BlackScholesParams` (record) | `ivMultiplier`, `rate`, `dividend` |
| `EmpiricalParams` (record) | the 13 empirical parameters |
| `PricingModels` | loads and validates the file into those records, applies overrides and returns errors; it has no defaults of its own |
| `ZeroDteTime` | built from `TimeParams`; `minutesLeft(now)` and `years(now)` |
| `ExpectedMove` | `points(spx, vix, years)` gives EM; `distance(spx, shortStrike, direction, em)` gives N = D / EM |
| `NormalDistribution` | `cdf(x)` with Hart's double-precision algorithm (West 2005), absolute error ≈ 1e-15 |
| `BlackScholes` | `price(spot, strike, sigma, years, rate, dividend, type)`: European; when `years ≤ 0` or `sigma = 0`, returns the intrinsic value |
| `ZeroDteOptionPricer` | built from `TimeParams` and `BlackScholesParams`; `price(spx, strike, vix, now, type)` uses σ_effective = VIX/100 × ivMultiplier |
| `SpreadPricer` (interface) | `price(spx, vix, now, direction, shortStrike, longStrike)`: the spread's value to close, in `[0, width]` |
| `BlackScholesSpreadPricer` | built from `TimeParams` and `BlackScholesParams`; short leg minus long leg via `BlackScholes` at σ_effective, clamped to `[0, width]` |
| `EmpiricalSpreadPricer` | built from `TimeParams` and `EmpiricalParams`; the calibrated formula below |
| `SpreadPricers` | Spring bean in `TradeEvoConfiguration`. It loads the file lazily via `PricingModels`. `validate(JsonNode pricing, String path)` returns error strings. `create(JsonNode pricing)` returns a `ResolvedPricer(SpreadPricer pricer, JsonNode parameters)` |

Strategies get `SpreadPricers` by constructor injection, since they are already Spring beans. Tests build `PricingModels` and the pricers directly from records or from a JSON string, without Spring.

Distance D from the short strike is positive when out of the money:
- `BULL_PUT`: D = SPX − shortStrike
- `BEAR_CALL`: D = shortStrike − SPX

Width = |longStrike − shortStrike|. Intrinsic value = min(max(−D, 0), width).

## Empirical model

This is the user's calibrated credit-spread formula, with T = minutesLeft. Every name below is a parameter from `models.empirical`:

```
C(T) = clamp(cScale × (T / cPivot)^cExponent, cMin, cMax)
M(T) = 1 + mHeight × exp(−(T − mCenter)² / (2 × mWidth²))     midday stickiness
A(T) = 1 + aHeight × exp(−(T − aCenter)² / (2 × aWidth²))     early-afternoon support
f(N) = min(width × base × exp(−decay × N) × C × M × A, width)
```

Pricing rules:
- **T ≤ 0:** returns the intrinsic value. (The original returned 0, which counted spreads that finish in the money as full wins.)
- **N ≥ 0 (out of the money):** returns f(N), identical to the original function.
- **N < 0 (in the money):** returns max(intrinsic, width − (width − f₀) × exp(−decay × |N|)), where f₀ = f(0). (The original returned the intrinsic value, which dropped by about 0.65 × width at the strike.) This is continuous at the strike, approaches the width deep in the money, and never falls below intrinsic.

With the shipped parameters, the out-of-the-money values are much lower than Black-Scholes beyond about 0.3 EM. For example, at 12:00 and N = 1 this model gives 0.015 × width, against about 0.16 for Black-Scholes. Comparing backtest runs that use the two models is how to judge them.

## Validation at call time

SPX, the strikes and VIX must be finite and > 0. The short and long strikes must differ, and their order must match the direction. A violation throws `IllegalArgumentException` with a message naming the bad input.

## Testing

Pricing tests build their parameters from records or inline JSON, so they don't depend on the shipped file. One test checks the shipped file itself.

### Accuracy tolerance

The simulator gives estimates, not exact market prices. A simulated price is **acceptable within ±20%** of a market-based reference value:

```
|simulated − reference| ≤ 0.20 × |reference|
```

Tests use one shared helper, `assertWithinTolerance(actual, reference)`, so the 20% lives in one place.

| Check | Tolerance |
|---|---|
| A price against a market-based estimate: the rule-of-thumb straddle, the narrow ATM spread at ≈ 0.5 × width, and real quotes added later as test fixtures | **±20% relative** |
| A mathematical identity or bound: textbook Black-Scholes values, put-call parity, intrinsic value at expiry, 0 ≤ spread ≤ width, continuity, monotonicity, the multiplier/VIX equivalence | Tight, as stated in each test (1e-12 to 1e-6, or 0.01 for the textbook values to two decimals) |
| A port or pinned value: the empirical model against the original function, and the model-comparison table | Exact match (1e-12), so any change to the formula or the file is caught |

The ±20% band never applies to identities or bounds. A put-call parity break or a spread priced above its width is a bug, not an estimation error.

Out of scope here: a 20% gap between the two models is not a failure. At N = 1 they differ by about 10×, which the comparison test only records. Judging which model meets ±20% against real fills is a calibration task that needs real quotes.

- **`NormalDistribution`:** reference values at 0, ±1, ±1.96 and ±5, and symmetry.
- **`BlackScholes`:**
  - Matches a published reference value (Hull: S=42, K=40, r=0.10, σ=0.20, T=0.5 gives call 4.76 and put 0.81).
  - Put-call parity holds to 1e-10.
  - At or after expiry, and when σ = 0, the price equals the intrinsic value.
  - The price rises with σ and with time left.
- **`ZeroDteOptionPricer`:**
  - With SPX 5000, VIX 15 at 10:00, the ATM straddle is within ±20% of $36, i.e. $28.80–$43.20. This is consistent with the market rule of thumb of 0.7–1% of SPX.
  - From the expiration time onwards, the price equals the intrinsic value.
  - `ivMultiplier` 1.0 matches plain σ = VIX/100. Pricing with a multiplier k equals pricing with plain σ at VIX × k. The price rises with the multiplier.
- **`ZeroDteTime`:** 16:00 gives 0 minutes; 15:59:30 gives 0.5 minutes; after 16:00 gives 0; a different expiration, trading-days and session-minutes setting is honored.
- **`EmpiricalSpreadPricer`:**
  - Out of the money, matches a port of the original function to 1e-12 at sample points.
  - At N = 0, the values on either side differ by < 1e-9.
  - At T ≤ 0, returns the intrinsic value, both in and out of the money.
  - Always in `[intrinsic, width]`.
  - Non-increasing in N, and strictly decreasing out of the money (with the shipped parameters, where f₀ < width). Once both strikes are in the money, intrinsic is the full width, so the price is flat at the width.
- **`BlackScholesSpreadPricer`:** in `[0, width]`; a narrow spread at the money is within ±20% of 0.5 × width (0.4–0.6 × width); at expiry equals the intrinsic value.
- **Comparison:** a test that pins both models' prices at N ∈ {0, 0.5, 1, 1.28} at 12:00, using the shipped parameters. It documents the gap and catches unintended changes to the formula or the file.
### Edge-case and property tests

These use inline parameters and fixed input grids, with no randomness, so failures always reproduce. Unless stated otherwise, the base case is SPX 5000, VIX 15, 10:00 and `ivMultiplier` 1.0, with expiration at 16:00.

**Expiry**
- **At expiration:** at exactly 16:00, both options and both spread models return the intrinsic value. An ATM option is worth 0.
- **After expiration:** at 16:30 the result is the same as at 16:00, with no negative time and no NaN.
- **One second before:** at 15:59:59 the price is finite and ≥ intrinsic, and within 0.5 points of intrinsic for an option 10 points OTM.
- **Converging to intrinsic:** for ITM, ATM and OTM strikes, the time value falls monotonically over the times 15:00, 15:30, 15:50, 15:59 and 16:00, and reaches 0 at 16:00.
- **Early close:** with `expiration` overridden to 13:00, the price at 13:00 equals the intrinsic value, and 12:00 has 60 minutes left.
- **Spreads at expiry:** for both models and both directions, a spread fully OTM is worth 0, fully ITM is worth the width, and with SPX between the strikes it is worth exactly `min(max(−D, 0), width)`.

**Deep ITM and OTM options**
- **Deep ITM** (strike 300 points ITM, about 7 EM at 10:00): the call and the put are worth intrinsic to within 1e-6, and never less than intrinsic.
- **Deep OTM** (strike 300 points OTM): the call and the put are worth between 0 and 1e-6, and never negative.
- **Extreme strikes and inputs:** strike 1, strike 10 × SPX, VIX 0.01, VIX 200 and 1 second left all give finite results with no NaN or infinity. Extreme d₁ and d₂ go through the normal distribution function without overflow.
- **No-arbitrage bounds at every input:** call ≤ SPX, put ≤ strike, call ≥ max(SPX − strike, 0) and put ≥ max(strike − SPX, 0). These hold with rate and dividend at 0.
- **Spreads:** a spread whose short strike is 5 EM OTM is worth < 1e-3 × width under both models. One whose long strike is 5 EM ITM is worth > (1 − 1e-3) × width under both models.

**Volatility sensitivity**
- **Option prices rise with VIX:** for ITM, ATM and OTM calls and puts, the price strictly increases over VIX 5, 10, 15, 25, 40 and 80.
- **Vega:** a numerical vega (bump of ±0.01 VIX) is positive, and is largest near the money. At 10:00 the ATM vega exceeds the vega 2 EM away.
- **Low-volatility limit:** at VIX 0.01, prices of strikes 10 points from SPX are within 1e-6 of intrinsic. (At the money, a tiny time value remains: the EM is still about 0.03 points.)
- **Effect of time on vega:** the same VIX bump moves an ATM price less at 15:30 than at 10:00, because less time is left.
- **Multiplier:** a multiplier of k equals pricing at VIX × k, at k ∈ {0.5, 1.0, 1.15, 2.0}.
- **Spreads under Black-Scholes:** when the short strike is OTM, the spread value rises with VIX. When the long strike is ITM, it falls with VIX, because there's more chance of moving back out of the money.
- **Spreads under the empirical model:** with the short strike OTM, the value rises with VIX, since higher VIX gives a larger EM, smaller N and more value.

**Spread price bounds** (a property test over a fixed grid)
- **Grid:**
  - SPX: 4000, 5000, 6000
  - VIX: 5, 15, 40, 80
  - Times: 09:30, 12:00, 15:30, 15:59, 16:00, 16:30
  - Width: 5, 25, 100
  - Short-strike distance: −3, −1, −0.25, 0, 0.25, 1, 3 EM
  - Both directions and both models
- **Every grid point:**
  - The price is finite and between 0 and the width.
  - The empirical price is also never below intrinsic. Black-Scholes is not held to that: the spread's payoff is capped at the width, so a European spread can be worth slightly less than intrinsic before expiry.
- **Moving SPX:** for both models, the value to close does not rise as SPX moves away from the short strike in the out-of-the-money direction. It falls for a bull put spread as SPX rises, and for a bear call spread as SPX falls.
- **Widening the spread:** moving the long strike further out never lowers the spread value. Each extra point of width adds at most 1 point of value.
- **Spread versus short leg (Black-Scholes):** the spread is worth no more than the short option alone.
- **At the strike:** the empirical model is continuous at N = 0 (as above). Black-Scholes changes by no more than 1e-3 × width between N = −1e-6 and N = +1e-6.

### Configuration tests

- **`PricingModels`:**
  - The shipped `config/pricing-models.json` loads with no errors. Its path comes from a surefire system property, the same way `examples/` is found.
  - Errors are reported, with paths, for: a `defaultModel` not in `models`, a non-positive `ivMultiplier`, a missing parameter, an unknown section or parameter, an unknown model, a non-finite value, `cMin > cMax`, a bad `expiration`, and a non-positive `tradingDaysPerYear`. Several errors are reported together.
- **`SpreadPricers`:**
  - Each model is chosen correctly, and a missing `model` or `pricing` block uses `defaultModel`.
  - Overrides are applied and validated with `settings.pricing...` paths.
  - The resolved parameters include the overrides.
  - A missing or invalid file fails with `InvalidConfig` only when a pricer is requested.
  - The file is read once and then cached.

## Out of scope

- A strategy class that uses these pricers.
- Volatility skew or smile.
- The overnight variance share.
- Re-fitting the empirical constants.
- Options with more than one day to expiry.
- REST or CLI pricing endpoints.
- Reloading the file while the server is running.
