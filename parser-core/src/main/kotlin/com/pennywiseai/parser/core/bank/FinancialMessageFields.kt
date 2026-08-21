package com.pennywiseai.parser.core.bank

import java.math.BigDecimal

/**
 * Conservative mechanics for financial messages that use labelled English lines.
 *
 * This deliberately does not decide whether a message is a transaction, which
 * account owns a funding source, or what a bank-specific title means. Parsers
 * supply their own accepted labels and transaction semantics. Field anchoring
 * prevents a fee, balance, date, or unrelated number from becoming the amount.
 */
internal object FinancialMessageFields {

    enum class TransferDirection {
        INCOMING,
        OUTGOING
    }

    fun normalizedLines(message: String): List<String> = message
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lineSequence()
        .map { it.trim().replace(Regex("[\\t ]+"), " ") }
        .filter { it.isNotEmpty() }
        .toList()

    fun value(message: String, labels: Collection<String>): String? {
        val labelPattern = labels
            .map { Regex.escape(it).replace("\\ ", "\\s+") }
            .joinToString("|")
        val field = Regex("""^(?:$labelPattern)\s*:?\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
        return normalizedLines(message)
            .firstNotNullOfOrNull { line -> field.matchEntire(line)?.groupValues?.get(1)?.trim() }
    }

    fun hasField(message: String, labels: Collection<String>): Boolean = value(message, labels) != null

    /**
     * Extracts a SAR/SR amount only from an accepted transaction field. The
     * method never falls back to an unlabelled amount, fee, or balance line.
     */
    fun sarAmount(message: String, labels: Collection<String>): BigDecimal? {
        val fieldValue = value(message, labels) ?: return null
        return sarAmountFromFieldValue(fieldValue)
    }

    /**
     * For an explicitly labelled transaction field such as
     * `Amount: TRY 300.00 (SAR 25.00)`, prefer the stated SAR settlement amount.
     * If no settlement amount exists, accept only a direct SAR/SR amount. A
     * foreign-only amount remains unsupported rather than inventing a rate.
     */
    fun sarSettlementOrAmount(message: String, labels: Collection<String>): BigDecimal? {
        val fieldValue = value(message, labels) ?: return null
        settlementSarAmount(fieldValue)?.let { return it }
        return sarAmountFromFieldValue(fieldValue)
    }

    fun numericValue(message: String, labels: Collection<String>): BigDecimal? {
        val fieldValue = value(message, labels) ?: return null
        val number = NUMBER.find(fieldValue)?.groupValues?.get(1) ?: return null
        return parse(number)
    }

    /**
     * Only use this in a parser after that parser has established that the title
     * describes a transfer. `From`/`To` in a purchase can refer to a merchant.
     */
    fun transferDirection(message: String): TransferDirection? {
        val hasFrom = hasField(message, listOf("From"))
        val hasTo = hasField(message, listOf("To"))
        return when {
            hasFrom && !hasTo -> TransferDirection.INCOMING
            hasTo && !hasFrom -> TransferDirection.OUTGOING
            else -> null
        }
    }

    private fun sarAmountFromFieldValue(value: String): BigDecimal? {
        SAR_FIRST.matchEntire(value)?.let { return parse(it.groupValues[1]) }
        SAR_LAST.matchEntire(value)?.let { return parse(it.groupValues[1]) }
        return null
    }

    private fun settlementSarAmount(value: String): BigDecimal? {
        SETTLEMENT_SAR_FIRST.find(value)?.let { return parse(it.groupValues[1]) }
        SETTLEMENT_SAR_LAST.find(value)?.let { return parse(it.groupValues[1]) }
        return null
    }

    private fun parse(raw: String): BigDecimal? = try {
        BigDecimal(raw.replace(",", ""))
    } catch (_: NumberFormatException) {
        null
    }

    private val NUMBER = Regex("""([0-9][0-9,]*(?:\.\d{1,2})?)""")
    private val SAR_FIRST = Regex("""^(?:SAR|SR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)$""", RegexOption.IGNORE_CASE)
    private val SAR_LAST = Regex("""^([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)$""", RegexOption.IGNORE_CASE)
    private val SETTLEMENT_SAR_FIRST = Regex(
        """\(\s*(?:SAR|SR)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*\)""",
        RegexOption.IGNORE_CASE
    )
    private val SETTLEMENT_SAR_LAST = Regex(
        """\(\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)\s*\)""",
        RegexOption.IGNORE_CASE
    )
}
