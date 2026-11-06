# Internationalisation specification

## Amount formats

Importers send amounts as text in their own locale. The system must accept:

- pt-BR and es: thousands separated by a dot, decimals by a comma ("1.234,50");
- en: thousands separated by a comma, decimals by a dot ("1,234.50").

Amounts are stored in canonical form (BigDecimal, plain dot decimal, no thousands separator).
The importer's language is known from the company profile.

## Dates

Dates are always shown in the user's language and stored as ISO 8601.
