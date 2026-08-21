package com.pennywiseai.parser.core.bank

/**
 * Shared guards for Saudi transaction alerts. The patterns deliberately require
 * an explicit failed-attempt phrase rather than matching generic words such as
 * "refund" or "reversal", which may describe money successfully returned.
 */
internal object SaudiTransactionMessageGuards {

    fun isDeclinedOrFailed(message: String): Boolean {
        val lower = message.lowercase()

        if (FinancialMessageSafety.hasExplicitFailure(message, ARABIC_FAILED_ATTEMPT_PHRASES)) return true
        if (ENGLISH_FAILED_ATTEMPT_PHRASES.any { lower.contains(it) }) return true

        return ENGLISH_STATUS_WITH_TRANSACTION.containsMatchIn(lower) ||
            TRANSACTION_WITH_ENGLISH_STATUS.containsMatchIn(lower) ||
            ARABIC_TRANSACTION_WITH_DECLINE.containsMatchIn(message)
    }

    fun isPromotionalOrOperationalNotice(message: String): Boolean {
        val lower = message.lowercase()
        return FinancialMessageSafety.isOperationalOrPromotionalNotice(message) ||
            OPERATIONAL_NOTICE_PHRASES.any { lower.contains(it) }
    }

    private val OPERATIONAL_NOTICE_PHRASES = listOf(
        "successfully added to apple pay",
        "wallet limit",
        "your limit has gone up",
        "cashback offer",
        "exclusive offer",
        "special offer",
        "promotional message",
        "unsubscribe"
    )

    private val ARABIC_FAILED_ATTEMPT_PHRASES = listOf(
        "رصيد غير كافي",
        "عملية مرفوضة",
        "تم رفض العملية",
        "فشل العملية",
        "عملية فاشلة",
        "تعذر إتمام العملية",
        "لم تتم العملية",
        "عملية غير ناجحة"
    )

    private val ENGLISH_FAILED_ATTEMPT_PHRASES = listOf(
        "insufficient balance",
        "insufficient funds",
        "transaction declined",
        "transaction failed",
        "transaction rejected",
        "purchase declined",
        "purchase failed",
        "purchase rejected",
        "payment declined",
        "payment failed",
        "payment rejected",
        "transfer declined",
        "transfer failed",
        "transfer rejected"
    )

    private val ARABIC_TRANSACTION_WITH_DECLINE = Regex(
        """(?:شراء|حوالة|سداد|خصم|سحب|إيداع)(?:\s+دولي)?\s+مرفوض(?:ة)?"""
    )

    private val ENGLISH_STATUS_WITH_TRANSACTION = Regex(
        """\b(?:declined|failed|rejected)\s+(?:card\s+)?(?:transaction|purchase|payment|transfer)\b"""
    )

    private val TRANSACTION_WITH_ENGLISH_STATUS = Regex(
        """\b(?:card\s+)?(?:transaction|purchase|payment|transfer)\s+(?:was\s+|has\s+been\s+)?(?:declined|failed|rejected)\b"""
    )
}
