package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Saudi National Bank / Al Ahli Bank (SNB-AlAhli, Saudi Arabia).
 *
 * Handles Arabic POS purchase, withdrawal and transfer formats such as:
 *   شراء نقاط بيع SamsungPay
 *   بـSAR 19.45
 *   من SYNTHETIC MERCHANT
 *   مدى *0002
 *   في 07:53 03/04/26
 *
 * Sender examples: SNB-AlAhli, SNB, AlAhliBank, الأهلي
 */
class SNBAlAhliBankParser : BankParser() {

    override fun getBankName() = "Saudi National Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("SNB") ||
                normalized.contains("ALAHLI") ||
                normalized.contains("AL-AHLI") ||
                normalized.contains("AL AHLI") ||
                sender.contains("الأهلي")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val amountPatterns = listOf(
            // Foreign authorization alerts can state the final Saudi settlement in
            // parentheses. Prefer that explicit settled amount; never infer FX.
            Regex(
                """\(\s*(?:SAR|SR)\s*([0-9,]+(?:\.\d{1,2})?)\s*\)""",
                RegexOption.IGNORE_CASE
            ),
            // "بـSAR 19.45", "بSAR:19.45", "مبلغ: SAR 100"
            Regex(
                """(?:بـ?|مبلغ)[ \t]*:?[ \t]*(?:SAR|SR)[ \t]*:?[ \t]*([0-9,]+(?:\.\d{1,2})?)\b""",
                RegexOption.IGNORE_CASE
            ),
            // "بـ3900 SAR", "ب1500 SAR", "مبلغ 3900 SAR", "مبلغ:102.22SAR"
            Regex(
                """(?:بـ?|مبلغ)[ \t]*:?[ \t]*([0-9,]+(?:\.\d{1,2})?)[ \t]*:?[ \t]*(?:SAR|SR)\b""",
                RegexOption.IGNORE_CASE
            ),
            // Legacy loose fallback: "SAR 19.45"
            Regex(
                """(?:SAR|SR)[ \t]*:?[ \t]*([0-9,]+(?:\.\d{1,2})?)\b""",
                RegexOption.IGNORE_CASE
            ),
            // Safe fallback for transaction messages that place the number before SAR.
            Regex(
                """(?<![0-9/])([0-9][0-9,]*(?:\.\d{1,2})?)[ \t]*(?:SAR|SR)\b""",
                RegexOption.IGNORE_CASE
            )
        )

        for (pattern in amountPatterns) {
            pattern.find(message)?.let { return parseSarAmount(it.groupValues[1]) }
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
        return when {
            // Explicit return/reversal wording takes precedence over the original
            // purchase term that is often repeated in refund notifications.
            message.contains("استرجاع") || message.contains("مرتجع") ||
                message.contains("إرجاع") || message.contains("عكس العملية") ||
                message.contains("اعادة شراء") || message.contains("إعادة شراء") -> TransactionType.INCOME

            // This title is the bank's explicit reversal of an emergency-cash
            // withdrawal, so it returns the withdrawn funds to the user.
            message.contains("تصحيح") && message.contains("سحب") &&
                (message.contains("طوارئ") || message.contains("نقد")) -> TransactionType.INCOME

            // A payment to the user's SNB-issued credit card is an own-account
            // movement, not a second purchase expense.
            message.contains("سداد") &&
                (message.contains("بطاقة") || message.contains("ائتمان")) -> TransactionType.TRANSFER

            message.contains("واردة") -> TransactionType.INCOME          // incoming transfer
            message.contains("حوالة بين حساباتك") -> TransactionType.TRANSFER // own-account transfer
            message.contains("إيداع") -> TransactionType.INCOME          // existing cash-deposit convention
            message.contains("شراء") -> TransactionType.EXPENSE          // purchase
            message.contains("سحب") -> TransactionType.EXPENSE           // withdrawal
            message.contains("صادرة") -> TransactionType.EXPENSE         // outgoing transfer
            message.contains("خصم") -> TransactionType.EXPENSE           // deduction
            message.contains("سداد") -> TransactionType.EXPENSE          // bill payment
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // A purchase may list an account-only "من" line before the merchant. Keep
        // searching all such lines until a valid non-account value is found.
        val fromPattern = Regex("""(?:^|\n)من[ \t]*([^\n]+)""")
        for (match in fromPattern.findAll(message)) {
            val raw = match.groupValues[1].trim()
            if (raw.isBlank() || isAccountOnlyValue(raw)) {
                continue
            }

            val merchant = cleanMerchantName(
                raw.replaceFirst(Regex("""^(?:[0-9*]+)[ \t]*"""), "").trim()
            )
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // "الى: NAME" (to: recipient) for outgoing transfers
        val toPattern = Regex("""الى\s*:?\s*([^\n]+?)(?:\n|$)""")
        toPattern.find(message)?.let { match ->
            val merchant = cleanMerchantName(match.groupValues[1].trim())
            if (isValidMerchantName(merchant)) {
                return merchant
            }
        }

        // ATM fallback
        if (message.contains("صراف")) {
            return "ATM Withdrawal"
        }

        return null
    }

    private fun isAccountOnlyValue(value: String): Boolean =
        value.all { it == '*' || it.isDigit() || it.isWhitespace() }

    override fun extractAccountLast4(message: String): String? {
        // "مدى *0002", "مدى-ابل *0001*", or "*مدى-ابل*0001" (Mada / Apple Pay card)
        val madaPattern = Regex("""\*?\s*مدى(?:\s*-\s*ابل)?\s*\*+\s*(\d{3,4})\*?""")
        madaPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        // "بطاقة *0002" (card)
        val cardPattern = Regex("""بطاقة\s*\*+\s*(\d{3,4})""")
        cardPattern.find(message)?.let { return extractLast4Digits(it.groupValues[1]) }

        return super.extractAccountLast4(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        // "الرصيد: SAR 1234.56" or "الرصيد المتاح: SAR 1234.56"
        val balancePattern = Regex(
            """الرصيد(?:\s*المتاح)?\s*:?\s*(?:SAR|SR)\s*([0-9,]+(?:\.\d{1,2})?)""",
            RegexOption.IGNORE_CASE
        )
        balancePattern.find(message)?.let { return parseSarAmount(it.groupValues[1]) }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        if (message.contains("مدى") || message.contains("بطاقة") ||
            message.contains("نقاط بيع") || message.contains("SamsungPay", ignoreCase = true) ||
            message.contains("ApplePay", ignoreCase = true)
        ) {
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

        if (FinancialMessageSafety.isSecurityCode(message) ||
            message.contains("رمز") || message.contains("OTP", ignoreCase = true) ||
            message.contains("كلمة المرور")
        ) {
            return false
        }

        val keywords = listOf(
            "شراء",      // purchase
            "سحب",       // withdrawal
            "حوالة",     // transfer
            "خصم",       // deduction
            "سداد",      // payment
            "إيداع",     // deposit
            "SAR",
            "SR"
        )
        return keywords.any { message.contains(it) }
    }
}
