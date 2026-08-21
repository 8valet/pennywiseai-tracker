package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Al Rajhi Bank (Saudi Arabia) SMS messages
 *
 * Supported formats (Arabic):
 * - Purchase: "شراء ... بـSAR 5.75 لـMERCHANT"
 * - Online purchase: "شراء انترنت ... بـSAR 140 لـMERCHANT"
 * - ATM withdrawal: "سحب:صراف آلي ... مبلغ:SAR 100 مكان السحب:LOCATION"
 * - Outgoing local transfer: "حوالة محلية صادرة ... مبلغ:SAR 100 الى:RECIPIENT"
 * - Incoming local transfer: "حوالة محلية واردة ... مبلغ:SAR 7714.80 من:SENDER"
 * - Outgoing internal transfer: "حوالة داخلية صادرة ... بـSAR 200"
 * - Incoming internal transfer: "حوالة داخلية واردة ... بـSAR 1170"
 * - Loan installment: "خصم: قسط تمويل ... القسط: 2304.58 SAR"
 * - Bill payment: "سداد فاتورة"
 *
 * Supported formats (English, multi-line PoS):
 * - "PoS Purchase\nBy:<digits>;<method>\nAmount:SR <number>\nAt:<merchant>\n<date>"
 *
 * Sender: AlRajhiBank
 */
class AlRajhiBankParser : BankParser() {

    override fun getBankName() = "Al Rajhi Bank"

    override fun getCurrency() = "SAR"

    /**
     * Detects the English multi-line PoS purchase format.
     */
    private fun isEnglishPosFormat(message: String): Boolean {
        return message.contains("PoS Purchase", ignoreCase = true) &&
                POS_DETECT.containsMatchIn(message)
    }

    private companion object {
        // English PoS format patterns, compiled once.
        val POS_DETECT = Regex(
            """Amount\s*:\s*(?:(?:SAR|SR)\s|[0-9])""",
            RegexOption.IGNORE_CASE
        )
        val POS_AMOUNT = Regex(
            """Amount\s*:\s*(?:(?:SAR|SR)\s*([0-9,]+(?:\.\d{1,2})?)|([0-9,]+(?:\.\d{1,2})?)\s*(?:SAR|SR))""",
            RegexOption.IGNORE_CASE
        )
        val POS_CARD = Regex("""By:\s*(\d+)\s*;""")
        val POS_MERCHANT = Regex("""At:\s*([^\n]+)""")
    }

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("ALRAJHI") ||
                normalized.contains("RAJHI") ||
                sender.contains("الراجحي")
    }

    override fun extractAmount(message: String): BigDecimal? {
        // English PoS: "Amount:SR 2" / "Amount: SR 4.50" — gated to the PoS format
        // (like the other English overrides) so it can't match a future Arabic
        // SMS that happens to contain the same substring.
        if (isEnglishPosFormat(message)) {
            POS_AMOUNT.find(message)?.let { match ->
                return parseSarAmount(match.groupValues[1].ifBlank { match.groupValues[2] })
            }
        }

        // Historical English cashback alerts use the same explicit field but are
        // not PoS purchases. Keep this title-gated rather than admitting generic
        // English Amount fields for all Rajhi notifications.
        if (message.contains("cashback", ignoreCase = true)) {
            FinancialMessageFields.sarAmount(message, listOf("Amount"))?.let { return it }
        }

        // An international purchase may explicitly state the settled Saudi amount
        // in parentheses. Prefer that amount, never an inferred conversion.
        if (isPurchaseMessage(message)) {
            Regex("""\(\s*(?:SAR|SR)\s*([0-9,]+(?:\.\d{1,2})?)\s*\)""", RegexOption.IGNORE_CASE)
                .find(message)?.let { return parseSarAmount(it.groupValues[1]) }
        }

        // Pattern 1: "بـSAR 5.75" / "بـSR 5.75" (optional spacing)
        val bPattern = Regex(
            """بـ\s*(?:SAR|SR)\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        bPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        // Pattern 2: "مبلغ:SAR 100" / "مبلغ:SR 100" (optional colon/spacing)
        val amountPattern = Regex(
            """مبلغ\s*:?\s*(?:SAR|SR)\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        amountPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        // Pattern 3: historical Arabic number-first amount: "بـ5.75 SR" or
        // "مبلغ 100 SAR". The Arabic amount marker keeps this from becoming a
        // generic currency fallback.
        val numberFirstPattern = Regex(
            """(?:بـ|مبلغ)\s*:?\s*([0-9,]+(?:\.\d{1,2})?)\s*(?:SAR|SR)""",
            RegexOption.IGNORE_CASE
        )
        numberFirstPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        // Pattern 4: "القسط: 2304.58 SAR" / "القسط: 2304.58 SR" (loan installment)
        val installmentPattern = Regex(
            """القسط:\s*([0-9,]+(?:\.\d{1,2})?)\s*(?:SAR|SR)""",
            RegexOption.IGNORE_CASE
        )
        installmentPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        return null
    }

    private fun parseSarAmount(raw: String): BigDecimal? {
        val cleaned = raw.replace(",", "")
        return try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun extractTransactionType(message: String): TransactionType? {
        // English PoS purchase is always an expense
        if (isEnglishPosFormat(message)) {
            return TransactionType.EXPENSE
        }

        return when {
            // Return/reversal notices often repeat the original purchase word;
            // classify their explicit money-return wording first.
            (message.contains("كاش باك") || message.contains("cashback", ignoreCase = true)) &&
                (message.contains("عكس") || message.contains("reversal", ignoreCase = true)) -> TransactionType.EXPENSE
            message.contains("كاش باك") || message.contains("cashback", ignoreCase = true) -> TransactionType.INCOME

            message.contains("استرجاع") || message.contains("مرتجع") ||
                message.contains("إرجاع") || message.contains("عكس العملية") ||
                message.contains("refund", ignoreCase = true) ||
                message.contains("reversal", ignoreCase = true) -> TransactionType.INCOME

            // A payment from the user's Rajhi account to their own credit card is
            // an internal movement, not a second expense.
            message.contains("سداد") &&
                (message.contains("بطاقة") || message.contains("ائتمان")) -> TransactionType.TRANSFER
            message.contains("حوالة بين حساباتك") ||
                (message.contains("حوالة") && message.contains("من بطاقة") && message.contains("الى حساب")) -> TransactionType.TRANSFER

            // Incoming (واردة = incoming)
            message.contains("واردة") -> TransactionType.INCOME

            // Expense types
            isPurchaseMessage(message) -> TransactionType.EXPENSE           // purchase
            message.contains("سحب") -> TransactionType.EXPENSE            // withdrawal
            message.contains("صادرة") -> TransactionType.EXPENSE          // outgoing
            message.contains("خصم") -> TransactionType.EXPENSE            // deduction
            message.contains("سداد") -> TransactionType.EXPENSE           // payment/settlement

            else -> null
        }
    }

    private fun isPurchaseMessage(message: String): Boolean =
        message.contains("purchase", ignoreCase = true) ||
            message.contains("PoS", ignoreCase = true) ||
            message.contains("شراء")

    private fun isRefundOrReversalMessage(message: String): Boolean =
        message.contains("استرجاع") || message.contains("مرتجع") ||
            message.contains("إرجاع") || message.contains("عكس العملية") ||
            message.contains("refund", ignoreCase = true) ||
            message.contains("reversal", ignoreCase = true)

    override fun extractMerchant(message: String, sender: String): String? {
        // English PoS: merchant is the value after "At:" up to end of line
        if (isEnglishPosFormat(message)) {
            POS_MERCHANT.find(message)?.let { match ->
                var raw = match.groupValues[1].trim()
                // Strip a leading purely-numeric terminal id (e.g. "170658 riyadh" -> "riyadh")
                val stripped = raw.replaceFirst(Regex("""^\d+\s+"""), "").trim()
                if (stripped.isNotBlank() && stripped.any { it.isLetter() }) {
                    raw = stripped
                }
                val merchant = cleanMerchantName(raw)
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
            return null
        }

        // Historical purchase and refund alerts use explicit, labelled merchant
        // fields. Keep this title-gated so a location-like line in an unrelated
        // Rajhi notification cannot become a merchant.
        if (isPurchaseMessage(message) || isRefundOrReversalMessage(message)) {
            FinancialMessageFields.value(message, listOf("At", "لدى", "التاجر"))?.let { raw ->
                val merchant = cleanMerchantName(raw)
                if (isValidMerchantName(merchant)) return merchant
            }
        }

        // Pattern 1: "لـMERCHANT" (to/for merchant) — stop at newline or date pattern
        val toPattern = Regex(
            """لـ([^\n*]+?)(?:\n|\d{2}/\d|$)"""
        )
        toPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            // Skip if it looks like an account number (all *s and digits)
            if (raw.all { it == '*' || it.isDigit() || it == ';' || it.isWhitespace() }) {
                // Not a merchant, skip
            } else {
                // If contains ";", take the part after it (name after account)
                val merchant = if (raw.contains(";")) {
                    cleanMerchantName(raw.substringAfter(";").trim())
                } else {
                    cleanMerchantName(raw)
                }
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Pattern 2: "الى:MERCHANT" (to: recipient for transfers)
        val toColonPattern = Regex(
            """الى:([^\n]+?)(?:\n|الى:|الرسوم:|$)"""
        )
        toColonPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            if (!raw.all { it == '*' || it.isDigit() }) {
                val merchant = cleanMerchantName(raw)
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Pattern 3: "مكان السحب:LOCATION" (withdrawal location for ATM)
        val atmPattern = Regex(
            """مكان السحب:([^\n]+?)(?:\n|$)"""
        )
        atmPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // Pattern 4: "من:SENDER" for incoming transfers — extract who sent money
        val fromPattern = Regex(
            """من:([^\n*]+?)(?:\n|\d{2}/\d|$)"""
        )
        fromPattern.find(message)?.let { match ->
            val raw = match.groupValues[1].trim()
            if (raw.isNotBlank() && !raw.all { it == '*' || it.isDigit() }) {
                val merchant = cleanMerchantName(raw)
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }

        // Pattern 5: "من****;NAME" for incoming internal transfers
        val fromInlinePattern = Regex(
            """من\*+;(.+?)(?:\n|\d{2}/\d|$)"""
        )
        fromInlinePattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // ATM fallback
        if (message.contains("صراف آلي")) {
            return "ATM Withdrawal"
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        // English PoS: "By:<digits>;<method>" — the digits identify the card;
        // keep only the last 4 (the leading digits may be a longer card number).
        if (isEnglishPosFormat(message)) {
            POS_CARD.find(message)?.let { match ->
                extractLast4Digits(match.groupValues[1])?.let { return it }
            }
        }
        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // Pattern: "المبلغ المتبقي: SAR 13827.48" / "... SR 13827.48"
        val remainingPattern = Regex(
            """المبلغ المتبقي:\s*(?:SAR|SR)\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        remainingPattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        // Current balance form: "رصيد:1.55 SR" / "رصيد: 1.55 SAR".
        val balancePattern = Regex(
            """رصيد:\s*([0-9,]+(?:\.\d{1,2})?)\s*(?:SAR|SR)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { match ->
            return parseSarAmount(match.groupValues[1])
        }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        // English PoS purchase is a card transaction
        if (isEnglishPosFormat(message)) {
            return true
        }
        // مدى = Mada (Saudi debit card network)
        // بطاقة = card
        if (message.contains("مدى") || message.contains("بطاقة")) {
            return true
        }
        return super.detectIsCard(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (SaudiTransactionMessageGuards.isDeclinedOrFailed(message) ||
            SaudiTransactionMessageGuards.isPromotionalOrOperationalNotice(message)
        ) {
            return false
        }

        // Skip OTP / verification (Arabic + English)
        if (message.contains("رمز") || message.contains("OTP", ignoreCase = true) ||
            message.contains("كلمة المرور") ||
            message.contains("verification code", ignoreCase = true)
        ) {
            return false
        }

        // Accept the English multi-line PoS purchase format
        if (isEnglishPosFormat(message)) {
            return true
        }

        val keywords = listOf(
            "شراء",      // purchase
            "سحب",       // withdrawal
            "حوالة",     // transfer
            "خصم",       // deduction
            "سداد",       // payment/settlement
            "استرجاع",   // refund/return
            "مرتجع",     // returned purchase
            "عكس",       // reversal
            "refund",    // English refund
            "reversal",  // English reversal
            "cashback",  // explicit credit/reversal family
            "كاش باك",   // Arabic cashback
            "SAR",       // currency marker
            "SR"
        )
        return keywords.any { message.contains(it) }
    }
}
