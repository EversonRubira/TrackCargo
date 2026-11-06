# ADR-007: Free storage period at destination

## Status

Accepted on 2026-03-12. Supersedes the value of 7 days in the "Storage fees" section of docs/SPEC.md,
which has not been updated yet.

## Decision

The free storage period at the destination port is 10 days, as negotiated with the main
shipping lines. The fee starts on the 11th day.

## Consequences

Code and tests must use 10 days. The SPEC section is to be corrected in the next documentation pass.
