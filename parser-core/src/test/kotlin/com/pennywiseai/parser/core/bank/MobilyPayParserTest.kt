package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class MobilyPayParserTest {

    // All fixtures use unmistakably synthetic identities, dates, suffixes, and values.
    private val parser = MobilyPayParser()

    @TestFactory
    fun `mobily pay parser handles card purchases wallet funding and security messages`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Synthetic card purchase",
                message = "Card Purchase\nAmount: 12.34 SAR\nAt: SYNTHETIC STORE\nCard Number:VISA****0007\nOn: 01-01-2030 00:00:00\nCurrent balance: 99.99 SAR",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC STORE",
                    accountLast4 = "0007",
                    balance = BigDecimal("99.99"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Second synthetic card purchase",
                message = "Card Purchase\nAmount: 23.45 SAR\nAt: SYNTHETIC MARKET\nCard Number:VISA****0007\nOn: 01-01-2030 00:00:00\nCurrent balance: 99.99 SAR",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKET",
                    accountLast4 = "0007",
                    balance = BigDecimal("99.99"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Wallet top-up is an own-account transfer",
                message = "Mobily Pay Wallet Top-up\nAmount:34.56 SAR\nFrom Card:0007*MADA\nOn:01/01/2030 00:00:00\nCurrent Balance:99.99 SAR",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER,
                    merchant = "Mobily Pay Wallet",
                    accountLast4 = "0007",
                    balance = BigDecimal("99.99"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Amount SAR no-space variant is parsed",
                message = "Card Purchase\nAmount:SAR 45.67\nAt: SYNTHETIC VARIANT STORE\nCard Number:VISA****0007\nCurrent balance: 99.99 SAR",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC VARIANT STORE",
                    accountLast4 = "0007",
                    balance = BigDecimal("99.99"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Amount SAR spaced variant is parsed",
                message = "Card Purchase\nAmount: SAR 56.78\nAt: SYNTHETIC SPACED STORE\nCard Number:VISA****0007\nCurrent balance: 99.99 SAR",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC SPACED STORE",
                    accountLast4 = "0007",
                    balance = BigDecimal("99.99"),
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Purchase reversal is income",
                message = "Purchase Reversal\nAmount: 67.89 SAR\nAt: SYNTHETIC MERCHANT\nCard Number:VISA****0007",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Incoming local transfer is income",
                message = "Incoming Local Transfer\nAmount: 78.90 SAR\nFrom: SYNTHETIC SENDER",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("78.90"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER"
                )
            ),
            ParserTestCase(
                name = "Outgoing local transfer is expense",
                message = "Outgoing Local Transfer\nAmount: 89.01 SAR\nTo: SYNTHETIC RECIPIENT",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.01"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "Historical local transfer uses explicit To direction",
                message = "Local Transfer\nAmount: 90.12 SAR\nTo: SYNTHETIC RECIPIENT",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("90.12"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "Credit adjustment is income",
                message = "Credit Adjustment\nAmount: 12.34 SAR",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.INCOME
                )
            ),
            ParserTestCase(
                name = "Historical adjustment credit word order is income",
                message = "Adjustment Credit\nAmount: 23.45 SAR",
                sender = "MobilyPay",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.INCOME
                )
            ),
            ParserTestCase(
                name = "Generic adjustment is ignored without direction",
                message = "Account Adjustment\nAmount: 34.56 SAR",
                sender = "MobilyPay",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined card purchase is ignored",
                message = "Card Purchase Declined\nAmount: 12.34 SAR\nInsufficient balance",
                sender = "MobilyPay",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Security code with amount is ignored",
                message = "Your code is:<CODE>\nFor online purchase\nAmount:SAR 12.34",
                sender = "MobilyPay",
                shouldParse = false
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "OTP: <CODE>\nCard Purchase\nAmount: 12.34 SAR",
                sender = "MobilyPay",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Verification code message is ignored",
                message = "Your verification code is <CODE>\nAmount: 12.34 SAR",
                sender = "MobilyPay",
                shouldParse = false
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves mobily pay safely`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Mobily Pay",
                sender = "MobilyPay",
                currency = "SAR",
                message = "Card Purchase\nAmount: 23.45 SAR\nAt: SYNTHETIC MARKET\nCard Number:VISA****0007\nCurrent balance: 99.99 SAR",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            ),
            SimpleTestCase(
                bankName = "Mobily Pay",
                sender = "Mobily Payment Offers",
                currency = "SAR",
                message = "Card Purchase\nAmount: 23.45 SAR\nAt: SYNTHETIC MARKET",
                shouldParse = false
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory sender checks")
    }
}
