import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.AlRajhiBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import java.math.BigDecimal

class AlRajhiBankParserTest {

    // All fixtures use unmistakably synthetic identities, dates, suffixes, and values.

    @Test
    fun `historical purchase without explicit merchant field retains null merchant`() {
        val parsed = AlRajhiBankParser().parse(
            "شراء انترنت\nبـ12.34 SAR\nمرجع العملية: SYNTHETIC REFERENCE\nرصيد: 99.99 SAR",
            "AlRajhiBank",
            0L
        )!!

        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(BigDecimal("12.34"), parsed.amount)
        assertNull(parsed.merchant)
    }

    @TestFactory
    fun `al rajhi parser covers representative scenarios`(): List<DynamicTest> {
        val parser = AlRajhiBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Card purchase via Google Pay",
                message = "شراء\nعبر:****;مدى-جوجل باي\nبـSAR 12.34\nلـSYNTHETIC STORE\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC STORE",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Online purchase via Mada",
                message = "شراء انترنت\nعبر:****;مدى\nمن:****\nبـSAR 23.45\nلـSYNTHETIC WALLET\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC WALLET",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal",
                message = "سحب:صراف آلي\nبطاقة:****;مدى\nمبلغ:SAR 34.56\nمكان السحب:SYNTHETIC ATM\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC ATM",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Outgoing local transfer",
                message = "حوالة محلية صادرة\nمصرف:SYNTHETIC BANK\nمن:****\nمبلغ:SAR 45.67\nالى:SYNTHETIC RECIPIENT\nالى:****\nالرسوم:SAR 0.50\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC RECIPIENT"
                )
            ),
            ParserTestCase(
                name = "Incoming internal transfer",
                message = "حوالة داخلية واردة\nبـSAR 56.78\nلـ****\nمن****;SYNTHETIC SENDER\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER"
                )
            ),
            ParserTestCase(
                name = "Loan installment deduction",
                message = "خصم: قسط تمويل\nالقسط: 67.89 SAR\nمن: ****\nالمبلغ المتبقي: SAR 99.99\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "Incoming local transfer (salary)",
                message = "حوالة محلية واردة\nعبر:SYNTHETIC CLEARING BANK\nمبلغ:SAR 78.90\nالى:****\nمن:SYNTHETIC EMPLOYER\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("78.90"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC EMPLOYER"
                )
            ),
            ParserTestCase(
                name = "Outgoing internal transfer",
                message = "حوالة داخلية صادرة\nمن****\nبـSAR 89.01\nلـ****; SYNTHETIC RECIPIENT\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.01"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "English PoS purchase (alpha merchant)",
                message = "PoS Purchase\nBy:0007;mada(Google Pay)\nAmount:SR 12.34\nAt:SYNTHETIC STORE\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC STORE",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "English PoS purchase (terminal id + city)",
                message = "PoS Purchase\nBy:0007;mada(Google Pay)\nAmount:SR 23.45\nAt:000000 SYNTHETIC CITY\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC CITY",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Arabic internet purchase with SR amount and balance",
                message = "شراء انترنت بـSR 34.56\nعبر0007;فيزا\nلـSYNTHETIC MARKET\nرصيد:99.99 SR\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKET",
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "SR transfer amount with optional colon spacing",
                message = "حوالة محلية واردة\nمبلغ:SR 45.67\nمن:SYNTHETIC SENDER\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC SENDER"
                )
            ),
            ParserTestCase(
                name = "SAR current balance form",
                message = "شراء بـSAR 56.78\nلـSYNTHETIC MARKET\nرصيد: 99.99 SAR\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKET",
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "Refund is income even when purchase wording appears",
                message = "استرجاع شراء\nمبلغ:SR 67.89\nلـSYNTHETIC MERCHANT\nرصيد: 99.99 SR",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC MERCHANT",
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "Historical foreign purchase prefers settled SAR amount",
                message = "International Purchase\nAmount: USD 20.00 (SAR 75.00)\nAt: SYNTHETIC MERCHANT",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT"
                )
            ),
            ParserTestCase(
                name = "Arabic number-first SR internet purchase",
                message = "شراء انترنت\nبـ12.34 SR\nلـSYNTHETIC MERCHANT",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MERCHANT"
                )
            ),
            ParserTestCase(
                name = "Cashback credit is income",
                message = "Cashback Credit\nAmount: SR 12.34",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.INCOME
                )
            ),
            ParserTestCase(
                name = "Cashback reversal is expense",
                message = "Cashback Reversal\nAmount: SR 12.34",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Own credit card payment is transfer",
                message = "سداد بطاقة ائتمان\nمبلغ:SR 78.90\n30/1/1 00:00",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("78.90"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER
                )
            ),
            ParserTestCase(
                name = "Historical internet purchase extracts labelled Arabic merchant",
                message = "شراء انترنت\nبـ12.34 SR\nلدى: SYNTHETIC ONLINE MERCHANT\nرصيد: 99.99 SR",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC ONLINE MERCHANT",
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "Historical purchase accepts compact Arabic merchant label",
                message = "شراء\nبـ23.45 SAR\nلدى:SYNTHETIC MARKETPLACE",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKETPLACE"
                )
            ),
            ParserTestCase(
                name = "Historical purchase refund retains income and labelled merchant",
                message = "استرجاع شراء\nمبلغ:SR 34.56\nلدى: SYNTHETIC RETURN MERCHANT\nرصيد: 99.99 SR",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC RETURN MERCHANT",
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "Historical refund extracts explicit Arabic merchant field",
                message = "استرجاع شراء\nمبلغ:SR 34.56\nالتاجر: SYNTHETIC RETURN MERCHANT\nرصيد: 99.99 SR",
                sender = "AlRajhiBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC RETURN MERCHANT",
                    balance = BigDecimal("99.99")
                )
            ),
            ParserTestCase(
                name = "Arabic merchant field outside a supported transaction family is ignored",
                message = "إشعار خدمة\nالتاجر: SYNTHETIC INFORMATION\nمبلغ:SR 12.34",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined refund-looking message with Arabic merchant field is ignored",
                message = "عملية مرفوضة\nاسترجاع شراء\nمبلغ:SR 12.34\nالتاجر: SYNTHETIC DECLINED MERCHANT",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined historical purchase with labelled merchant is ignored",
                message = "عملية مرفوضة\nشراء انترنت\nبـSR 12.34\nلدى: SYNTHETIC DECLINED MERCHANT",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined foreign purchase is ignored despite settlement",
                message = "International Purchase Declined\nAmount: USD 20.00 (SAR 75.00)",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined Arabic purchase is ignored",
                message = "عملية مرفوضة\nشراء\nبـSR 12.34\nرصيد غير كافي",
                sender = "AlRajhiBank",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Arabic OTP is ignored even when it includes SR",
                message = "رمز التحقق <CODE>\nبـSR 12.34",
                sender = "AlRajhiBank",
                shouldParse = false
            )
        )

        val handleCases = listOf(
            "AlRajhiBank" to true,
            "ALRAJHI" to true,
            "الراجحي" to true,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Al Rajhi Bank Parser Suite"
        )
    }
}
