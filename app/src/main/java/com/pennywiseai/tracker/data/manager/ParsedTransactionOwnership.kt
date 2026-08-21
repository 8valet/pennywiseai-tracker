package com.pennywiseai.tracker.data.manager

import com.pennywiseai.parser.core.AccountLast4Role
import com.pennywiseai.parser.core.BalanceOwnerKind
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType

/**
 * App-side interpretation of explicit parser ownership evidence.
 *
 * Parser-core supplies facts stated by the message. This file decides only how
 * those facts may safely drive account-balance persistence. It intentionally
 * returns null rather than guessing a missing bank or account identity.
 */
internal data class ReportedBalanceOwner(
    val bankName: String,
    val accountLast4: String,
    val kind: BalanceOwnerKind
)

internal data class QualifiedTransferLegs(
    val fromBankName: String,
    val fromAccountLast4: String,
    val toBankName: String,
    val toAccountLast4: String
)

internal fun ParsedTransaction.reportedBalanceOwnerOrNull(): ReportedBalanceOwner? {
    balanceOwner?.let { explicit ->
        return ReportedBalanceOwner(
            bankName = explicit.bankName,
            accountLast4 = explicit.accountLast4,
            kind = balanceOwnerKind ?: BalanceOwnerKind.ACCOUNT
        )
    }

    if (isMobileWallet) {
        return ReportedBalanceOwner(
            bankName = bankName,
            accountLast4 = AccountBalanceEntity.WALLET_ACCOUNT_MARKER,
            kind = BalanceOwnerKind.SERVICE_WALLET
        )
    }

    // A parser that opts into the role contract has positively identified this
    // suffix as non-user-owned (or cannot establish ownership). Preserve it in
    // transaction metadata, but block all legacy card/account balance effects.
    if (accountLast4Role != null && accountLast4Role != AccountLast4Role.USER_OWNED) {
        return null
    }

    val legacyAccount = accountLast4 ?: return null
    return ReportedBalanceOwner(
        bankName = bankName,
        accountLast4 = legacyAccount,
        kind = if (isFromCard) BalanceOwnerKind.CARD else BalanceOwnerKind.ACCOUNT
    )
}

/**
 * Returns legs only for a persisted transfer whose parsers supplied both
 * bank-qualified identities. Equal source and destination identities are not a
 * meaningful movement and are deliberately rejected.
 */
internal fun TransactionEntity.qualifiedTransferLegsOrNull(): QualifiedTransferLegs? {
    if (transactionType != TransactionType.TRANSFER) return null

    val fromBank = fromBankName?.takeIf { it.isNotBlank() } ?: return null
    val fromAccount = fromAccount?.takeIf { it.isNotBlank() } ?: return null
    val toBank = toBankName?.takeIf { it.isNotBlank() } ?: return null
    val toAccount = toAccount?.takeIf { it.isNotBlank() } ?: return null

    if (fromBank.equals(toBank, ignoreCase = true) && fromAccount == toAccount) return null

    return QualifiedTransferLegs(fromBank, fromAccount, toBank, toAccount)
}
