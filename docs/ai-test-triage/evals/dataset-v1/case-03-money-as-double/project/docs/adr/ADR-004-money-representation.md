# ADR-004: Representation of monetary amounts

## Status

Accepted.

## Decision

Monetary amounts are never stored or computed as `float` or `double`. Use `BigDecimal` with an
explicit scale of 2, or integer minor units (cents) in a `long`. Totals are summed in that
representation and rounded once, with `RoundingMode.HALF_EVEN`.

## Consequences

Binary floating point cannot represent most decimal fractions exactly, so sums such as
0.10 + 0.10 + 0.10 drift by a tiny amount. In invoices and payment reconciliation that drift
shows up as a cent of difference between the two parties.
