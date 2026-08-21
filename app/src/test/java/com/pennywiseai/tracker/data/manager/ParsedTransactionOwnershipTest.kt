package com.pennywiseai.tracker.data.manager

import com.pennywiseai.parser.core.AccountIdentity
import com.pennywiseai.parser.core.AccountLast4Role
import com.pennywiseai.parser.core.BalanceOwnerKind
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BarqParser
import com.pennywiseai.parser.core.bank.MobilyPayParser
import com.pennywiseai.parser.core.bank.STCBankParser
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType as EntityTransactionType
import com.pennywiseai.tracker.data.mapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ParsedTransactionOwnershipTest {

    // These ownership fixtures are deliberately synthetic and contain no corpus-derived values.

    @Test
    fun `normal card purchase keeps legacy card as reported balance owner`() {
        val parsed = transaction(
            type = TransactionType.EXPENSE,
            accountLast4 = "0007",
            balance = BigDecimal("99.99"),
            isFromCard = true
        )

        val entity = parsed.toEntity()
        val owner = parsed.reportedBalanceOwnerOrNull()

        assertEquals("0007", entity.accountNumber)
        assertNull(entity.balanceOwnerAccountLast4)
        assertEquals("Sample Bank", owner?.bankName)
        assertEquals("0007", owner?.accountLast4)
        assertEquals(BalanceOwnerKind.CARD, owner?.kind)
    }

    @Test
    fun `wallet top-up keeps funding card distinct from wallet reported balance`() {
        val parsed = MobilyPayParser().parse(
            """
                Mobily Pay Wallet Top-up
                Amount: 12.34 SAR
                From Card:0007*MADA
                Current Balance:99.99 SAR
            """.trimIndent(),
            "MobilyPay",
            0
        )!!

        val entity = parsed.toEntity()
        val owner = parsed.reportedBalanceOwnerOrNull()

        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertEquals("0007", parsed.accountLast4)
        assertEquals("0007", parsed.fundingInstrumentLast4)
        assertEquals(AccountBalanceEntity.WALLET_ACCOUNT_MARKER, owner?.accountLast4)
        assertEquals(BalanceOwnerKind.SERVICE_WALLET, owner?.kind)
        assertEquals(AccountBalanceEntity.WALLET_ACCOUNT_MARKER, entity.accountNumber)
        assertEquals("0007", entity.fundingInstrumentLast4)
        assertEquals("Mobily Pay", entity.balanceOwnerBankName)
        assertEquals(AccountBalanceEntity.WALLET_ACCOUNT_MARKER, entity.balanceOwnerAccountLast4)
        assertEquals(BalanceOwnerKind.SERVICE_WALLET.name, entity.balanceOwnerKind)
    }

    @Test
    fun `explicit bank-qualified transfer legs are eligible for two-leg routing`() {
        val parsed = transaction(
            type = TransactionType.TRANSFER,
            from = AccountIdentity("Source Bank", "0007"),
            to = AccountIdentity("Destination Bank", "0008")
        )

        val legs = parsed.toEntity().qualifiedTransferLegsOrNull()

        assertEquals("Source Bank", legs?.fromBankName)
        assertEquals("0007", legs?.fromAccountLast4)
        assertEquals("Destination Bank", legs?.toBankName)
        assertEquals("0008", legs?.toAccountLast4)
    }

    @Test
    fun `single-leg transfer is persisted without two-leg routing eligibility`() {
        val parsed = transaction(
            type = TransactionType.TRANSFER,
            from = AccountIdentity("Source Bank", "0007")
        )

        val entity = parsed.toEntity()

        assertEquals(EntityTransactionType.TRANSFER, entity.transactionType)
        assertEquals("0007", entity.fromAccount)
        assertEquals("Source Bank", entity.fromBankName)
        assertNull(entity.toBankName)
        assertNull(entity.qualifiedTransferLegsOrNull())
    }

    @Test
    fun `refund keeps income semantics and reported account balance ownership`() {
        val parsed = transaction(
            type = TransactionType.INCOME,
            accountLast4 = "0007",
            balance = BigDecimal("99.99"),
            isFromCard = true
        )

        val entity = parsed.toEntity()
        val owner = parsed.reportedBalanceOwnerOrNull()

        assertEquals(EntityTransactionType.INCOME, entity.transactionType)
        assertEquals("0007", entity.accountNumber)
        assertEquals(BalanceOwnerKind.CARD, owner?.kind)
    }

    @Test
    fun `ordinary transaction without explicit balance retains safe legacy account ownership`() {
        val parsed = transaction(
            type = TransactionType.EXPENSE,
            accountLast4 = "0007",
            balance = null,
            isFromCard = true
        )

        val entity = parsed.toEntity()
        val owner = parsed.reportedBalanceOwnerOrNull()

        assertNull(entity.balanceAfter)
        assertEquals("0007", entity.accountNumber)
        assertEquals(BalanceOwnerKind.CARD, owner?.kind)
    }

    @Test
    fun `incoming counterparty sender account is persisted as metadata but cannot own a balance`() {
        val parsed = BarqParser().parse(
            """
                Credit Transfer Internal
                Amount: 12.34 SAR
                From: SYNTHETIC SENDER
                Sender A/C: **0008
            """.trimIndent(),
            "barqapp",
            0
        )!!

        val entity = parsed.toEntity()

        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals(AccountLast4Role.COUNTERPARTY, parsed.accountLast4Role)
        assertEquals("0008", entity.accountNumber)
        assertEquals(AccountLast4Role.COUNTERPARTY.name, entity.accountLast4Role)
        assertNull(parsed.reportedBalanceOwnerOrNull())
    }

    @Test
    fun `outgoing destination account is retained as counterparty and cannot own a balance`() {
        val parsed = STCBankParser().parse(
            """
                Transfer Out
                Amount: 23.45 SAR
                To: SYNTHETIC RECIPIENT
                Bank: Other Bank
                A/C: **0008
            """.trimIndent(),
            "STC Bank",
            0
        )!!

        val entity = parsed.toEntity()

        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(AccountLast4Role.COUNTERPARTY, parsed.accountLast4Role)
        assertEquals("0008", entity.accountNumber)
        assertEquals(AccountLast4Role.COUNTERPARTY.name, entity.accountLast4Role)
        assertNull(parsed.reportedBalanceOwnerOrNull())
    }

    @Test
    fun `explicit user-owned suffix retains normal account balance eligibility`() {
        val parsed = transaction(
            type = TransactionType.EXPENSE,
            accountLast4 = "0007",
            balance = BigDecimal("99.99")
        ).copy(accountLast4Role = AccountLast4Role.USER_OWNED)

        val owner = parsed.reportedBalanceOwnerOrNull()

        assertEquals("0007", owner?.accountLast4)
        assertEquals(BalanceOwnerKind.ACCOUNT, owner?.kind)
    }

    @Test
    fun `explicitly unknown suffix cannot become a user account`() {
        val parsed = transaction(
            type = TransactionType.EXPENSE,
            accountLast4 = "0007",
            balance = BigDecimal("99.99")
        ).copy(accountLast4Role = AccountLast4Role.UNKNOWN)

        assertNull(parsed.reportedBalanceOwnerOrNull())
    }

    private fun transaction(
        type: TransactionType,
        accountLast4: String? = "0007",
        balance: BigDecimal? = null,
        isFromCard: Boolean = false,
        from: AccountIdentity? = null,
        to: AccountIdentity? = null
    ): ParsedTransaction = ParsedTransaction(
        amount = BigDecimal("12.34"),
        type = type,
        merchant = "SYNTHETIC MERCHANT",
        reference = null,
        accountLast4 = accountLast4,
        balance = balance,
        smsBody = "Synthetic test transaction",
        sender = "SampleSender",
        timestamp = 0,
        bankName = "Sample Bank",
        isFromCard = isFromCard,
        currency = "SAR",
        fromAccountIdentity = from,
        toAccountIdentity = to
    )
}
