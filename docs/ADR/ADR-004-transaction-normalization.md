# ADR-004: Transaction normalization

## Status

Accepted for the project foundation.

## Context

Providers report transactions in different formats. The product must normalize provider-specific events into one canonical transaction model (PAR-04, section 7.1). Amounts use integer minor units in the currency's minor unit, never floating-point Double (section 7.1). The MVP aggregates in a single base currency; transactions in other currencies are stored with their original currency and flagged, and no exchange-rate conversion is performed.

## Decision

- A canonical transaction model with the fields in section 7.1: id, account, source, event, amountMinor, currency, direction (INFLOW/OUTFLOW), transactionType, merchant, description, occurredAt, category, status, fingerprint, parserVersion, originalJson, providerReference, relatedTransactionId, reportedBalanceMinor, timestamps.
- Monetary values use integer minor units; the currency exponent rule (for example IDR two decimals) is decided here and handled explicitly, not assumed to be two decimals.
- The classification table in section 4.6 is the single rule for how each combination of direction, type, and link status is reported in aggregates.
- Failed attempts are not transactions (PAR-09); refunds and reversals are INFLOW refunds netted against spending (PAR-10); adjustments are created only by the balance-correction flow (ACC-08); transfer fees are derived from a transfer pair, not a separate transaction (TRF-04).

## Date

2026-11-14
