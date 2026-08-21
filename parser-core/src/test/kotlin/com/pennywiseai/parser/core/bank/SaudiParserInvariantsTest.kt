package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Cross-parser safety invariants derived from recurring Saudi SMS failure modes.
 * Fixtures are deliberately synthetic: they preserve only message structure,
 * labels, and transaction semantics.
 */
class SaudiParserInvariantsTest {

    private data class ParserFixture(
        val name: String,
        val parser: BankParser,
        val sender: String
    )

    private val parsers = listOf(
        ParserFixture("SNB", SNBAlAhliBankParser(), "SNB"),
        ParserFixture("Al Rajhi", AlRajhiBankParser(), "ALRAJHI"),
        ParserFixture("STC Bank", STCBankParser(), "STCBank"),
        ParserFixture("D360 Bank", D360BankParser(), "D360Bank"),
        ParserFixture("Barq", BarqParser(), "barqapp"),
        ParserFixture("Mobily Pay", MobilyPayParser(), "MobilyPay")
    )

    @Test
    fun `declined alert with a labelled amount is never parsed as a completed transaction`() {
        parsers.forEach { fixture ->
            val message = """
                Card Purchase Declined
                Amount: 12.34 SAR
                At: SYNTHETIC MERCHANT
                Transaction declined
            """.trimIndent()

            assertNull(
                fixture.parser.parse(message, fixture.sender, 0),
                "${fixture.name} must reject a declined alert before amount parsing"
            )
        }
    }

    @Test
    fun `security code with an amount is never parsed as a transaction`() {
        parsers.forEach { fixture ->
            val message = """
                Your code is: <CODE>
                Card Purchase
                Amount: 12.34 SAR
            """.trimIndent()

            assertNull(
                fixture.parser.parse(message, fixture.sender, 0),
                "${fixture.name} must reject an OTP even when it includes a labelled amount"
            )
        }
    }

    @Test
    fun `promotional SAR value is never parsed as a transaction`() {
        parsers.forEach { fixture ->
            val message = """
                Exclusive offer
                Receive 12.34 SAR cashback on your next purchase.
            """.trimIndent()

            assertNull(
                fixture.parser.parse(message, fixture.sender, 0),
                "${fixture.name} must reject a promotional SAR value"
            )
        }
    }

    @Test
    fun `a fee or balance line cannot replace the explicit transaction amount`() {
        val d360 = D360BankParser()
        val d360Message = """
            International Online Purchase
            Amount: SAR 12.34
            Fee: SAR 0.50
            Current Balance: SAR 99.99
            At: SYNTHETIC STORE
        """.trimIndent()
        assertEquals(BigDecimal("12.34"), d360.parse(d360Message, "D360Bank", 0)?.amount)

        val mobilyPay = MobilyPayParser()
        val mobilyMessage = """
            Card Purchase
            Amount: 12.34 SAR
            Fee: 0.50 SAR
            Current Balance: 99.99 SAR
            At: SYNTHETIC STORE
        """.trimIndent()
        assertEquals(BigDecimal("12.34"), mobilyPay.parse(mobilyMessage, "MobilyPay", 0)?.amount)
    }

    @Test
    fun `SAR settlement takes precedence over foreign authorization amount`() {
        val barqMessage = """
            Online Purchase
            Amount: 200.00 USD (12.34 SAR)
            Fee: 0.50 SAR
            At: SYNTHETIC STORE
        """.trimIndent()
        assertEquals(BigDecimal("12.34"), BarqParser().parse(barqMessage, "barqapp", 0)?.amount)

        val d360Message = """
            International Online Purchase
            Amount: USD 200.00 (SAR 12.34)
            Fee: SAR 0.50
            At: SYNTHETIC STORE
        """.trimIndent()
        assertEquals(BigDecimal("12.34"), D360BankParser().parse(d360Message, "D360Bank", 0)?.amount)
    }

    @Test
    fun `successful refund is income even when original purchase language is repeated`() {
        val message = """
            Purchase Reversal
            Amount: 12.34 SAR
            At: SYNTHETIC STORE
            Original card purchase returned successfully
        """.trimIndent()

        assertEquals(TransactionType.INCOME, MobilyPayParser().parse(message, "MobilyPay", 0)?.type)
        assertEquals(TransactionType.INCOME, STCBankParser().parse(message, "STCBank", 0)?.type)
        assertEquals(TransactionType.INCOME, BarqParser().parse(message, "barqapp", 0)?.type)
    }

    @Test
    fun `transfer direction relies on an exclusive labelled counterparty`() {
        assertEquals(
            FinancialMessageFields.TransferDirection.INCOMING,
            FinancialMessageFields.transferDirection("Internal Transfer\nFrom: SYNTHETIC SENDER")
        )
        assertEquals(
            FinancialMessageFields.TransferDirection.OUTGOING,
            FinancialMessageFields.transferDirection("Internal Transfer\nTo: SYNTHETIC RECIPIENT")
        )
        assertNull(
            FinancialMessageFields.transferDirection("Internal Transfer\nFrom: SYNTHETIC SENDER\nTo: SYNTHETIC RECIPIENT")
        )
    }
}
