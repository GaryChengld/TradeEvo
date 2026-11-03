# Real 0DTE SPX option quotes

Delayed (about 15 minutes) quotes from CBOE's public `delayed_quotes` feed, collected to check the
0DTE option pricers on branch `harden-0dte-pricers` against the market.

- One folder per trading day. Each `snapshot-HHMM.json` (HHMM = time taken, America/New_York) holds the SPXW
  contracts expiring that day with strikes within ±3% of spot, with every field CBOE publishes, plus the SPX and
  VIX quotes. `quoteTime` is the market time of the quote (ET); `spxTimestampUtc` is when CBOE served the file.
- `pricing-comparison.md` in each day folder compares that day's quotes with `ZeroDteOptionPricer`
  (expiry 16:00, time = minutes left / (252×390), sigma = VIX/100 × ivMultiplier).
- `pricing-comparison-all.md` compares all days together.
- The full unfiltered CBOE responses (about 13 MB each) are not stored here.
