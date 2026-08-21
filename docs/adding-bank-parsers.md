# Adding a Bank SMS Parser

Bank parsers live in the **`parser-core`** module (pure Kotlin, no Android
dependencies) so they can be reused across platforms. For anything more than a
trivial tweak, prefer the **`parser-author`** subagent, which owns this
end-to-end (read samples → write/extend parser → tests → register → run tests).

## Where parsers live
`parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`

## Base class — pick the right one
All parsers extend `BankParser`. But:

- **Indian banks** MUST extend `BaseIndianBankParser` to inherit centralized
  mandate, subscription, and balance-update logic.
- **UAE banks** MUST extend `UAEBankParser` for currency and transaction-type
  handling.
- Everything else extends `BankParser` directly.

## Key methods
- `getBankName()` — the bank's display name.
- `canHandle(sender: String)` — whether this parser handles SMS from a sender.
- `parse(smsBody, sender, timestamp)` — returns `ParsedTransaction` or `null`.

## Commonly overridden
- `extractAmount()` — bank-specific amount patterns.
- `extractMerchant()` — bank-specific merchant extraction.
- `extractTransactionType()` — only for special cases.

## Parser-quality principles

A parser should prove that a message is a completed transaction before extracting its fields. Treat a sender and a transaction-shaped title as initial evidence, then reject explicit declines, failures, security codes, operational notices, and promotional copy before considering an amount. If the evidence is ambiguous, return **no transaction** rather than a transaction with an incorrect amount or direction.

### Use field-aware extraction

Use `FinancialMessageFields` for repeated labelled English SMS mechanics such as `Amount:`, `Balance:`, `From:`, `To:`, and `At:`. The helper reads a value from the requested field only, so a value on `Fee:`, `Balance:`, or another unrelated line cannot become the transaction amount. Keep bank-specific wording, Arabic grammar, title recognition, account masks, and exceptional historical layouts in the bank parser rather than attempting to build a universal parser.

For foreign-currency notifications that include a separate SAR settlement value, use `sarSettlementOrAmount` with the bank's explicit amount field. The settled SAR value is the account-impacting amount; do not use the foreign authorization amount or infer a conversion. A parser that lacks clear evidence for a local settlement should return `null`.

### Apply transaction semantics consistently

A successful **refund**, **reversal**, or other confirmed return of funds is `INCOME`, even when the body repeats the original purchase vocabulary. A confirmed movement between the user's own funding source and wallet or account is `TRANSFER`, not expense or income. For a message already identified as a transfer, use structural `From:` / `To:` evidence to infer direction: an exclusive `From:` field is incoming and an exclusive `To:` field is outgoing. Do not infer direction from one generic keyword, and do not force a direction where both or neither structural fields exist.

### Test message families and safety invariants

Tests must use synthetic and anonymized fixtures. Do not place raw private SMS, personal names, phone numbers, account or card numbers, OTPs, references, or identifiable merchants in tracked source. Preserve the structural family instead: title, labels, amount placement, currency, direction fields, and expected semantic result.

Every parser change should add the relevant bank-specific message-family regression and should maintain the shared safety invariants. At minimum, verify that declined messages, OTPs, and promotions containing an amount return no transaction; that `Fee` and `Balance` cannot replace an explicit transaction amount; that SAR settlement takes precedence over a foreign amount; and that successful refunds remain income. Run the full `:parser-core:test` suite after a shared-mechanics change because several banks can rely on the same helper.

## Registration
Add the new parser to the `BankParserFactory.parsers` list in
`parser-core/.../bank/BankParserFactory.kt`.

## Return type & imports (parser-core)
Use `ParsedTransaction` from parser-core:
- `com.pennywiseai.parser.core.TransactionType`
- `com.pennywiseai.parser.core.ParsedTransaction`
- `java.math.BigDecimal` for amounts

## Using a parser result in the app
Convert with `com.pennywiseai.tracker.data.mapper.toEntity()`, which maps
`ParsedTransaction` → `TransactionEntity` and handles cross-module type
conversions.

## Tests
Parser tests MUST use the shared `ParserTestUtils` JUnit 5 helpers — see
[`docs/parser-test-standards.md`](parser-test-standards.md). Run them with:

```bash
./gradlew :parser-core:test          # or :parser-core:jvmTest
```

## Coverage
The full list of supported banks and transaction patterns lives in
[`docs/BANK_SUPPORT.md`](BANK_SUPPORT.md) and
[`docs/supported-banks.json`](supported-banks.json) — the authoritative source,
kept in sync with the parsers (do not maintain a hand-copied list elsewhere).
