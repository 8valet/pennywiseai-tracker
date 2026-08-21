package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.AccountLast4Role
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for STC Bank (Saudi Arabia).
 *
 * Handles English purchase / transfer formats such as:
 *   **0001 Purchase
 *   Via:0001
 *   Amount: 3 SAR
 *   From: SYNTHETIC MERCHANT
 *   At: 26/07/25 21:58
 *   STC Bank
 *
 * Sender examples: STC Bank, STCBank, STC-Bank, STC
 */
class STCBankParser : BankParser() {

    override fun getBankName() = "STC Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase().replace(Regex("[\\s\\-_]"), "")
        return normalized.contains("STCBANK") || normalized == "STC" || normalized == "STCPAY"
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        // The generic `stc` sender is also used by the telecom operator. Retain
        // it for historical financial delivery, but reject only messages with
        // clear Sawa/mobile-service semantics before the banking grammar runs.
        if (isGenericStcSender(sender) && isClearlyTelecomOnlyMessage(smsBody)) {
            return null
        }

        val parsed = super.parse(smsBody, sender, timestamp) ?: return null
        // A `Sender A/C` or a recipient A/C following an explicit `To`/`Bank`
        // block names the transfer counterparty. The historical outward-SARIE
        // layout additionally uses two `To` lines followed by `Account` and
        // `Fees`; its account belongs to the recipient at the destination bank.
        // These suffixes remain transaction metadata, never user-owned STC
        // account, card, or balance-owner evidence.
        val isCounterpartyAccount = COUNTERPARTY_ACCOUNT_FIELD.containsMatchIn(smsBody) ||
            OUTWARD_SARIE_DESTINATION_ACCOUNT.containsMatchIn(smsBody) ||
            FLATTENED_OUTWARD_SARIE_DESTINATION_ACCOUNT.containsMatchIn(smsBody)
        return if (parsed.accountLast4 != null && isCounterpartyAccount) {
            parsed.copy(accountLast4Role = AccountLast4Role.COUNTERPARTY)
        } else {
            parsed
        }
    }

    override fun extractAmount(message: String): BigDecimal? {
        FinancialMessageFields.sarAmount(message, listOf("Amount"))?.let { return it }

        // RCS can flatten a title and its labelled amount onto one line. This
        // retains STC's historical `Amount 12.34 SAR` grammar without turning it
        // into a cross-bank generic fallback.
        INLINE_AMOUNT.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        // Some RCS deliveries remove the newline after the title: "Purchase
        // Transaction Amount 500.85 SAR". This remains title-anchored rather
        // than becoming a generic SAR fallback.
        RCS_PURCHASE_AMOUNT.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        // Some older STC alerts omit the Amount label but retain a distinct
        // SAR-first amount. Keep this grammar local to STC rather than making a
        // generic fallback that could select a fee or balance in other banks.
        HISTORICAL_SAR_AMOUNT.find(message)?.let { match ->
            return parseAmount(match.groupValues[1])
        }

        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            // Funding the user's STC Bank account or wallet is an own-account
            // movement, whether labelled with the newer or historical wording.
            lower.contains("adding money to account") ||
                lower.contains("wallet topup") || lower.contains("wallet top-up") ||
                lower.contains("wallet top up") ||
                (lower.contains("transaction type") && lower.contains("wallet top")) ||
                (lower.contains("apple pay") &&
                    (lower.contains("top up") || lower.contains("top-up") || lower.contains("funding"))) -> TransactionType.TRANSFER

            // Successful return notifications must precede generic purchase/payment
            // words, which are commonly repeated in the alert body.
            lower.contains("refund") || lower.contains("reversal") || lower.contains("reverse transaction") -> TransactionType.INCOME

            // Directional internal transfers use an explicit labelled counterparty
            // rather than a generic verb. When both endpoints are present, the
            // movement is confirmed but direction is not; preserve it as TRANSFER.
            lower.contains("internal transfer") -> when (FinancialMessageFields.transferDirection(message)) {
                FinancialMessageFields.TransferDirection.OUTGOING -> TransactionType.EXPENSE
                FinancialMessageFields.TransferDirection.INCOMING -> TransactionType.INCOME
                null -> TransactionType.TRANSFER
            }

            lower.contains("purchase") -> TransactionType.EXPENSE
            lower.contains("withdrawal") || lower.contains("withdraw") -> TransactionType.EXPENSE
            lower.contains("payment") -> TransactionType.EXPENSE
            lower.contains("debit") -> TransactionType.EXPENSE
            lower.contains("transfer out") || lower.contains("sent to") ||
                (lower.contains("sarie") && (lower.contains("outward") || lower.contains("outgoing"))) -> TransactionType.EXPENSE
            lower.contains("refund") -> TransactionType.INCOME
            lower.contains("deposit") -> TransactionType.INCOME
            lower.contains("credit") && !lower.contains("credit card") -> TransactionType.INCOME
            lower.contains("received") -> TransactionType.INCOME
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // "From: MERCHANT NAME" — merchant for Purchase, sender for incoming
        val fromPattern = Regex(
            """From\s*:\s*([^\n]+?)(?:\n|At\s*:|$)""",
            RegexOption.IGNORE_CASE
        )
        fromPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // "To: RECIPIENT NAME" — recipient for outgoing transfers
        val toPattern = Regex(
            """To\s*:\s*([^\n]+?)(?:\n|At\s*:|$)""",
            RegexOption.IGNORE_CASE
        )
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // "**0001 Purchase" / "*0001 Purchase"
        val starPattern = Regex("""\*+(\d{4})\b""")
        starPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        // "Via:0001" / "Via: 0001"
        val viaPattern = Regex("""Via\s*:\s*(\d{4})""", RegexOption.IGNORE_CASE)
        viaPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        return super.extractAccountLast4(message)
    }

    override fun detectIsCard(message: String): Boolean {
        // Presence of masked card (**XXXX) or Via:XXXX indicates card transaction
        if (Regex("""\*+\d{4}""").containsMatchIn(message)) return true
        if (Regex("""Via\s*:\s*\d{4}""", RegexOption.IGNORE_CASE).containsMatchIn(message)) return true
        return super.detectIsCard(message)
    }

    private fun isGenericStcSender(sender: String): Boolean =
        sender.uppercase().replace(Regex("[\\s\\-_]"), "") == "STC"

    private fun isClearlyTelecomOnlyMessage(message: String): Boolean {
        val lower = message.lowercase()
        val sawa = lower.contains("sawa")
        val telecomServiceContext = lower.contains("sawa balance") ||
            lower.contains("mobile balance") ||
            lower.contains("telecom balance") ||
            lower.contains("recharge") ||
            lower.contains("mobile service") ||
            lower.contains("data package") ||
            lower.contains("service credit")
        val telecomRefund = lower.contains("vat refund") && (sawa || telecomServiceContext)

        // `Sawa` as a merchant in a normal bank purchase is not enough. A
        // generic-sender message must also carry balance, recharge, service, or
        // telecom-VAT semantics before it is treated as non-financial.
        return telecomRefund || (sawa && telecomServiceContext)
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (SaudiTransactionMessageGuards.isDeclinedOrFailed(message) ||
            SaudiTransactionMessageGuards.isPromotionalOrOperationalNotice(message)
        ) {
            return false
        }

        val lower = message.lowercase()

        if (FinancialMessageSafety.isSecurityCode(message)) return false

        val keywords = listOf(
            "purchase",
            "amount",
            "withdraw",
            "transfer",
            "payment",
            "refund",
            "reversal",
            "wallet top",
            "apple pay",
            "deposit",
            "debit",
            "credit",
            "sar"
        )
        return keywords.any { lower.contains(it) }
    }

    private fun parseAmount(raw: String): BigDecimal? = try {
        BigDecimal(raw.replace(",", ""))
    } catch (_: NumberFormatException) {
        null
    }

    private companion object {
        private val INLINE_AMOUNT = Regex(
            """\bAmount\s*:?\s*([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)\b""",
            RegexOption.IGNORE_CASE
        )
        private val RCS_PURCHASE_AMOUNT = Regex(
            """(?:online\s+)?purchase\s+transaction\s+amount\s+([0-9][0-9,]*(?:\.\d{1,2})?)\s*(?:SAR|SR)\b""",
            RegexOption.IGNORE_CASE
        )
        private val HISTORICAL_SAR_AMOUNT = Regex(
            """\b(?:SAR|SR)\s+([0-9][0-9,]*(?:\.\d{1,2})?)\b""",
            RegexOption.IGNORE_CASE
        )
        private val COUNTERPARTY_ACCOUNT_FIELD = Regex(
            """(?:Sender\s+A/C|To\s*:[^\n]*\n(?:Bank\s*:[^\n]*\n)?\s*A/C)\s*:?\s*\*+\d{4}\b""",
            RegexOption.IGNORE_CASE
        )
        private val OUTWARD_SARIE_DESTINATION_ACCOUNT = Regex(
            """(?is)\boutward\s+sarie\s+transfer\b.*?\nTo\s*:[^\n]+\nTo\s*:[^\n]+\nAccount\s*:\s*\*+\d{4}\b\s*\nFees\s*:"""
        )
        // Some historical RCS exports collapse or mix the line boundaries of
        // the same labelled outward-SARIE grammar. Requiring its title plus two
        // ordered `To` tokens, `Account`, and `Fees` keeps this distinct from
        // generic account use. The caller separately requires an already parsed
        // account suffix before assigning the counterparty role.
        private val FLATTENED_OUTWARD_SARIE_DESTINATION_ACCOUNT = Regex(
            """(?is)(?=.*outward)(?=.*sarie)(?=.*transfer)(?=.*\bto\b.*?\bto\b.*?\baccount\b.*?\bfees?\b).*"""
        )
    }
}
