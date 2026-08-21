package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.AccountLast4Role
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class STCBankParserTest {

    // All fixtures use unmistakably synthetic identities, dates, suffixes, and values.
    private val parser = STCBankParser()

    @Test
    fun `outward SARIE destination account is counterparty metadata`() {
        val parsed = parser.parse(
            """
                Outward SARIE transfer
                Amount: 12.34 SAR
                To: SYNTHETIC RECIPIENT
                To: SYNTHETIC DESTINATION BANK
                Account: ****0008
                Fees: 0.50 SAR
            """.trimIndent(),
            "STCBank",
            0L
        )!!

        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(BigDecimal("12.34"), parsed.amount)
        assertEquals("0008", parsed.accountLast4)
        assertEquals(AccountLast4Role.COUNTERPARTY, parsed.accountLast4Role)
        assertEquals("SYNTHETIC RECIPIENT", parsed.merchant)
    }

    @Test
    fun `flattened outward SARIE destination account is counterparty metadata`() {
        val parsed = parser.parse(
            "Outward SARIE transfer Amount 12.34 SAR To SYNTHETIC RECIPIENT To SYNTHETIC DESTINATION BANK Account ****0008 Fees 0.50 SAR",
            "STCBank",
            0L
        )!!

        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(BigDecimal("12.34"), parsed.amount)
        assertEquals("0008", parsed.accountLast4)
        assertEquals(AccountLast4Role.COUNTERPARTY, parsed.accountLast4Role)
    }

    @Test
    fun `ordinary STC card purchase remains on legacy account role`() {
        val parsed = parser.parse(
            """
                **0007 Purchase
                Via:0007
                Amount: 12.34 SAR
                From: SYNTHETIC MERCHANT
            """.trimIndent(),
            "STCBank",
            0L
        )!!

        assertEquals("0007", parsed.accountLast4)
        assertNull(parsed.accountLast4Role)
    }

    @TestFactory
    fun `stc bank parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Synthetic card purchase",
                message = "**0007 Purchase\nVia:0007\nAmount: 12.34 SAR\nFrom: SYNTHETIC MERCHANT\nAt: 01/01/30 00:00",
                sender = "STC Bank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Card purchase with decimal amount",
                message = "**0007 Purchase\nVia:0007\nAmount: 23.45 SAR\nFrom: SYNTHETIC MARKET\nAt: 01/01/30 00:00",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKET",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical card purchase accepts SR amount alias",
                message = "**0007 Purchase\nVia:0007\nAmount: 34.56 SR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Synthetic flattened RCS online purchase",
                message = "Online Purchase Transaction Amount 45.67 SAR\nFrom: SYNTHETIC WALLET\nCard: *0007\nDate 01/01/30 00:00",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC WALLET",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing internal transfer uses recipient direction",
                message = "Internal transfer\nAmount:56.78SAR\nTo:SYNTHETIC RECIPIENT\nAt:01/01/30 00:00",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "Incoming internal transfer uses sender direction",
                message = "Internal transfer\nAmount:67.89SAR\nFrom:SYNTHETIC SENDER\nAt:01/01/30 00:00",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER"
                )
            ),
            ParserTestCase(
                name = "Adding money to account is own account transfer",
                message = "Adding money to account\nAmount: 78.90 SAR\nVia: *0007\nAt: 01/01/30 00:00",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("78.90"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER
                )
            ),
            ParserTestCase(
                name = "Credit gift is income",
                message = "Credit Gift\nAmount: 89.01 SAR\nFrom: SYNTHETIC SENDER\nAt: 01/01/30 00:00",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.01"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER"
                )
            ),
            ParserTestCase(
                name = "Historical wallet top-up is transfer",
                message = "Wallet Top-up\nTransaction type: Wallet Topup\nAmount: 90.12 SAR\nVia: 0007",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("90.12"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Successful purchase reversal is income",
                message = "Purchase Reversal\nAmount: 12.34 SAR\nFrom: SYNTHETIC MERCHANT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT"
                )
            ),
            ParserTestCase(
                name = "Outgoing SARIE transfer is expense",
                message = "Outward SARIE Transfer\nAmount: 23.45 SAR\nTo: SYNTHETIC RECIPIENT",
                sender = "STCBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "Generic STC Sawa VAT refund is ignored",
                message = "You have been credited with SR 12.34\nFor VAT refund on your purchase\nCheck your Sawa balance",
                sender = "stc",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Generic STC Sawa recharge status is ignored",
                message = "Sawa recharge service credit\nAmount: 12.34 SAR\nCheck your Sawa balance",
                sender = "stc",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined purchase is ignored",
                message = "Purchase Declined\nAmount: 12.34 SAR\nInsufficient balance",
                sender = "STCBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your STC Bank verification code is <CODE>. Do not share it.",
                sender = "STC Bank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Synthetic OTP with amount is ignored",
                message = "<CODE> is your OTP\nFor: SYNTHETIC MERCHANT\nAmount: USD 0.0\n*Do not share the code",
                sender = "STCBank",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves stc bank`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "STC Bank",
                currency = "SAR",
                message = "**0007 Purchase\nVia:0007\nAmount: 12.34 SAR\nFrom: SYNTHETIC MERCHANT\nAt: 01/01/30 00:00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "STCBank",
                currency = "SAR",
                message = "**0007 Purchase\nVia:0007\nAmount: 23.45 SAR\nFrom: SYNTHETIC MARKET\nAt: 01/01/30 00:00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "STCPAY",
                currency = "SAR",
                message = "Internal transfer\nAmount:67.89SAR\nFrom:SYNTHETIC SENDER\nAt:01/01/30 00:00",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "STC Bank",
                sender = "stc",
                currency = "SAR",
                message = "Purchase Reversal\nAmount: 34.56 SAR\nFrom: SYNTHETIC MERCHANT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.INCOME
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
