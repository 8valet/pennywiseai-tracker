package com.pennywiseai.parser.core

import java.math.BigDecimal

/**
 * A bank-qualified account identity supplied only when a parser has explicit
 * message evidence for both the institution and account/service identifier.
 */
data class AccountIdentity(
    val bankName: String,
    val accountLast4: String
)

/**
 * The kind of financial entity whose running balance is explicitly reported by
 * a message. This is intentionally separate from the funding instrument used
 * to initiate the transaction.
 */
enum class BalanceOwnerKind {
    ACCOUNT,
    CARD,
    SERVICE_WALLET
}

/**
 * Role of the legacy [ParsedTransaction.accountLast4] suffix when a parser has
 * clear grammar evidence. Null preserves the behavior of older parsers that do
 * not yet emit an ownership assertion. UNKNOWN is deliberately distinct from
 * null: it means a parser opted into this contract but cannot attest that the
 * suffix belongs to the user.
 */
enum class AccountLast4Role {
    USER_OWNED,
    COUNTERPARTY,
    UNKNOWN
}

data class ParsedTransaction(
    val amount: BigDecimal,
    val type: TransactionType,
    val merchant: String?,
    val reference: String?,
    val accountLast4: String?,
    val balance: BigDecimal?,
    val creditLimit: BigDecimal? = null,
    val smsBody: String,
    val sender: String,
    val timestamp: Long,
    val bankName: String,
    val transactionHash: String? = null,
    val isFromCard: Boolean = false,
    val currency: String = "INR",
    val fromAccount: String? = null,
    val toAccount: String? = null,
    // Mobile-money wallet (e.g. eMola, M-Pesa Mozambique): the SMS carries a
    // running balance but no per-account number, because the whole wallet IS the
    // account. When true, the app derives a single service-level account row from
    // the balance instead of requiring an accountLast4.
    val isMobileWallet: Boolean = false,
    /**
     * The card/account explicitly named as the source of funding when it differs
     * from the account or wallet whose balance is reported. Existing parsers may
     * continue to use [accountLast4] for legacy compatibility.
     */
    val fundingInstrumentLast4: String? = null,
    val fundingInstrumentBankName: String? = null,
    /**
     * Explicit owner of [balance]. When absent, app consumers retain the legacy
     * accountLast4/isFromCard interpretation. SERVICE_WALLET identities use the
     * parser-core wallet marker below and are mapped to the app's local sentinel.
     */
    val balanceOwner: AccountIdentity? = null,
    val balanceOwnerKind: BalanceOwnerKind? = null,
    /**
     * Bank-qualified transfer legs. They are populated only when a parser has
     * explicit ownership-safe evidence for both the bank/service and identifier.
     * Legacy unqualified [fromAccount]/[toAccount] values remain unchanged.
     */
    val fromAccountIdentity: AccountIdentity? = null,
    val toAccountIdentity: AccountIdentity? = null,
    /**
     * Explicit semantic role of [accountLast4]. COUNTERPARTY and UNKNOWN must
     * never create cards/accounts or drive inferred balance updates; the suffix
     * remains transaction metadata only. Null retains legacy compatibility.
     */
    val accountLast4Role: AccountLast4Role? = null
) {
    fun generateTransactionId(): String {
        val normalizedAmount = amount.setScale(2, java.math.RoundingMode.HALF_UP)
        // Use SMS body hash for reliable deduplication across different timestamp sources
        // (BroadcastReceiver uses SC timestamp, ContentProvider uses device timestamp)
        val smsBodyHash = md5Hex(smsBody)
            .take(16) // First 16 chars of SMS body hash
        val data = "$sender|$normalizedAmount|$smsBodyHash"
        return md5Hex(data)
    }

    companion object {
        /** Platform-neutral service identity mapped to the app wallet sentinel. */
        const val SERVICE_WALLET_ACCOUNT_MARKER = "WALLET"
    }
}


