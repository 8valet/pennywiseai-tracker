package com.pennywiseai.parser.core.bank

/**
 * Shared negative evidence for bank message parsers.
 *
 * A matching amount is not sufficient evidence of a completed transaction:
 * declined operations, OTPs, and operational notices commonly repeat the
 * original amount and merchant. Banks may add their own localized failure terms
 * through [hasExplicitFailure].
 */
internal object FinancialMessageSafety {

    fun hasExplicitFailure(message: String, additionalPhrases: Collection<String> = emptyList()): Boolean {
        val lower = message.lowercase()
        return GENERIC_FAILURE_PHRASES.any { lower.contains(it) } ||
            additionalPhrases.any { lower.contains(it.lowercase()) }
    }

    fun isSecurityCode(message: String): Boolean {
        val lower = message.lowercase()
        return SECURITY_CODE_PHRASES.any { lower.contains(it) }
    }

    fun isOperationalOrPromotionalNotice(message: String): Boolean {
        val lower = message.lowercase()
        return OPERATIONAL_OR_PROMOTIONAL_PHRASES.any { lower.contains(it) }
    }

    private val GENERIC_FAILURE_PHRASES = listOf(
        "declined",
        "decline",
        "failed",
        "failure",
        "not successful",
        "was not completed",
        "could not be completed",
        "rejected",
        "cancelled",
        "canceled",
        "reversed due to error"
    )

    private val SECURITY_CODE_PHRASES = listOf(
        "your code is",
        "verification code",
        "one time password",
        "one-time password",
        "otp",
        "الرقم السري"
    )

    private val OPERATIONAL_OR_PROMOTIONAL_PHRASES = listOf(
        "scheduled maintenance",
        "service interruption",
        "service is unavailable",
        "terms and conditions",
        "learn more",
        "exclusive offer",
        "cashback offer"
    )
}
