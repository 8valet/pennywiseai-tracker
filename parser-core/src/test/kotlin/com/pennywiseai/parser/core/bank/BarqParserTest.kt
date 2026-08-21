package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BarqParserTest {

    // All fixtures use unmistakably synthetic identities, dates, suffixes, and values.

    @TestFactory
    fun `barq parser handles supplied transaction formats`(): List<DynamicTest> {
        val parser = BarqParser()
        val cases = listOf(
            ParserTestCase(
                name = "Incoming internal credit transfer",
                message = """
                    Credit Transfer Internal
                    Amount: 12.34 SAR
                    From: SYNTHETIC SENDER
                    Sender A/C: **0008
                    2030-01-01 00:00
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER",
                    accountLast4 = "0008"
                )
            ),
            ParserTestCase(
                name = "Foreign online purchase uses settled SAR conversion",
                message = """
                    Online Purchase
                    VISA card **0007
                    Amount 200.00 INR (23.45 SAR)
                    Balance 99.99
                    At SYNTHETIC MERCHANT
                    A/C **0008
                    2030-01-01 00:00
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical local online purchases plural form",
                message = """
                    Online Purchases:
                    **0007
                    Amount: 34.56 SAR
                    Balance: 99.99
                    SYNTHETIC MARKET
                    2030-01-01 00:00
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKET",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical direct SAR online purchase amount",
                message = """
                    Online Purchases
                    SAR 45.67
                    Balance: 99.99
                    SYNTHETIC MERCHANT
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Historical online purchases accept a number-first direct SAR line",
                message = """
                    Online Purchases
                    **0007
                    12.34 SAR
                    SYNTHETIC ONLINE MERCHANT
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical POS purchases accept a number-first direct SR line",
                message = """
                    POS Purchases
                    VISA card **0007
                    56.78 SR
                    SYNTHETIC POS MERCHANT
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing wallet transfer is an expense",
                message = """
                    Barq wallet transfer
                    Amount: 67.89 SAR
                    To: SYNTHETIC RECIPIENT
                    2030-01-01 00:00
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "Incoming wallet transfer is income",
                message = """
                    Barq wallet transfer
                    Amount: 78.90 SAR
                    From: SYNTHETIC SENDER
                    2030-01-01 00:00
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("78.90"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER"
                )
            ),
            ParserTestCase(
                name = "Card refund is income",
                message = """
                    Refund
                    Visa card: **0007
                    Amount: 89.01 SAR
                    At: SYNTHETIC REFUND MERCHANT
                    On: 2030-01-01
                """.trimIndent(),
                sender = "barqapp-AD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.01"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC REFUND MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Apple Pay card added notification is ignored",
                message = "Your Card ending with 0007 is successfully added to Apple Pay.",
                sender = "barqapp",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Wallet limit promotion is ignored",
                message = "Your limit has gone up\nYou can now increase your wallet limit on barq",
                sender = "barqapp",
                shouldParse = false
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "Your code is: <CODE>\nVerification code for Barq",
                sender = "barqapp",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Historical international POS purchase uses settled SAR",
                message = """
                    International POS Purchase
                    VISA card **0007
                    Amount: 200.00 UAH (12.34 SAR)
                    Fee: 0.00 SAR
                    At: SYNTHETIC MERCHANT
                    2030-01-01 00:00
                """.trimIndent(),
                sender = "barqapp",
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
                name = "Historical ATM withdrawal is expense",
                message = """
                    International ATM Withdrawal
                    VISA card **0007
                    Amount: 23.45 SAR
                    At: SYNTHETIC ATM
                    2030-01-01 00:00
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC ATM",
                    accountLast4 = "0007"
                )
            ),
            ParserTestCase(
                name = "Historical local outgoing transfer is expense",
                message = """
                    Outgoing Local Transfer
                    Amount: 34.56 SAR
                    To: SYNTHETIC RECIPIENT
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "Historical wallet funding is transfer",
                message = """
                    Money Added to your Barq wallet
                    Amount: 45.67 SAR
                    Balance: 99.99
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER,
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "Historical reversal returning money is income",
                message = """
                    Reverse Transaction
                    Amount: 56.78 SAR
                    At: SYNTHETIC MERCHANT
                """.trimIndent(),
                sender = "barqapp",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT"
                )
            ),
            ParserTestCase(
                name = "Declined card purchase is ignored before amount parsing",
                message = """
                    Card Purchase Declined
                    Amount: 12.34 SAR
                    At: SYNTHETIC MERCHANT
                    Insufficient balance
                """.trimIndent(),
                sender = "barqapp",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Purchase balance line cannot become transaction amount",
                message = """
                    Online Purchase
                    Balance: 99.99
                    At: SYNTHETIC MERCHANT
                """.trimIndent(),
                sender = "barqapp",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Transaction title without amount is ignored",
                message = "Online Purchase\nAt SYNTHETIC MERCHANT\n2030-01-01 00:00",
                sender = "barqapp",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            "barqapp" to true,
            "barqapp-AD" to true,
            "BARQAPP_AD" to true,
            "Barq payment promotions" to false,
            "AlRajhiBank" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = cases,
            handleCases = handleCases,
            suiteName = "Barq Parser Suite"
        )
    }
}
