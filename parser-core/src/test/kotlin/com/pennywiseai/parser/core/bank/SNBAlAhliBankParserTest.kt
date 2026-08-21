package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import com.pennywiseai.parser.core.test.SimpleTestCase
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SNBAlAhliBankParserTest {

    // All fixtures use unmistakably synthetic identities, dates, suffixes, and values.
    private val parser = SNBAlAhliBankParser()

    @TestFactory
    fun `snb alahli parser handles key paths`(): List<DynamicTest> {
        val cases = listOf(
            ParserTestCase(
                name = "Legacy POS purchase with Samsung Pay and SAR-first amount",
                message = "شراء نقاط بيع SamsungPay\nبـSAR 12.34\nمن SYNTHETIC STORE\nمدى *0007\nفي 00:00 01/01/30",
                sender = "SNB-AlAhli",
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
                name = "Internet purchase with current amount order and account-only from line",
                message = "شراء انترنت\nبـ23.45 SAR\nمن 0008*\nمن SYNTHETIC WALLET\nمدى-ابل *0007\nفي 01/01/30 00:00",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("23.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC WALLET",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "POS purchase with Apple Pay Mada card without spacing",
                message = "شراء-POS\nبـ34.56 SAR\nمن SYNTHETIC SHOP\nمدى-ابل*0007\nفي 01/01/30 00:00",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("34.56"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC SHOP",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Incoming internal transfer remains income",
                message = "حوالة واردة داخلية ب45.67 SAR\nمن0008* مستلم تجريبي\n01/01/30 00:00",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "مستلم تجريبي"
                )
            ),
            ParserTestCase(
                name = "Own-account transfer is classified as transfer",
                message = "حوالة بين حساباتك\nمن 0008*\nمبلغ 56.78 SAR\nإلى 0009*\nفي 01/01/30 00:00",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER
                )
            ),
            ParserTestCase(
                name = "Decimal current amount is parsed",
                message = "شراء-POS\nبـ67.89 SAR\nمن SYNTHETIC STORE\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("67.89"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "No-space amount after amount label is parsed",
                message = "حوالة بين حساباتك\nمبلغ 78.90SAR\nمن 0008*\nإلى 0009*",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("78.90"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER
                )
            ),
            ParserTestCase(
                name = "Generic number before SAR fallback is parsed",
                message = "شراء-POS\n89.01 SAR\nمن SYNTHETIC STORE\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.01"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Refund is income before purchase wording",
                message = "استرجاع شراء\nبـ90.12 SAR\nمن SYNTHETIC STORE\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("90.12"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC STORE",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical online-purchase return is income",
                message = "اعادة شراء محلي عبر الانترنت\nبـ21.35 SAR\nمن SYNTHETIC RETURN MERCHANT\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("21.35"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SYNTHETIC RETURN MERCHANT",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Emergency cash withdrawal is an expense",
                message = "سحب نقدي طوارئ\nمبلغ 123.45 SAR\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.45"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Explicit emergency cash withdrawal correction returns funds",
                message = "تصحيح سحب نقدي طوارئ\nمبلغ 123.45 SAR\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.45"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical cash withdrawal correction returns funds without an emergency token",
                message = "تصحيح سحب نقدي\nمبلغ 123.45 SAR\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("123.45"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical foreign purchase prefers explicit SAR settlement",
                message = "شراء دولي\nUSD 20.00 (SAR 75.00)\nمن SYNTHETIC MERCHANT\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Historical ATM cash deposit with SR is income",
                message = "إيداع نقدي صراف آلي\nمبلغ SR 234.56",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("234.56"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "ATM Withdrawal"
                )
            ),
            ParserTestCase(
                name = "Government payment with SR is expense",
                message = "سداد حكومي\nمبلغ 45.67 SR",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.67"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Own credit card payment is transfer",
                message = "سداد بطاقة ائتمان\nمبلغ 56.78 SAR\nمدى *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("56.78"),
                    currency = "SAR",
                    type = TransactionType.TRANSFER,
                    accountLast4 = "0007",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "Declined foreign purchase is ignored despite settlement amount",
                message = "شراء دولي مرفوض\nUSD 20.00 (SAR 75.00)",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Declined Arabic purchase is ignored",
                message = "عملية مرفوضة\nشراء-POS\nبـ67.89 SAR\nرصيد غير كافي",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "OTP message is ignored",
                message = "رمز التحقق الخاص بك هو <CODE>. لا تشاركه مع أحد.",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Arabic online-purchase authentication is ignored despite merchant and amount",
                message = "الرقم السري لعملية شراء محلي عبر الانترنت: <CODE>\nبـ12.34 SAR\nمن SYNTHETIC MARKET",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Arabic authentication is ignored despite card suffix and SAR amount",
                message = "الرقم السري لتأكيد شراء عبر الانترنت: <CODE>\nمبلغ 56.78 SAR\nبطاقة *0007",
                sender = "SNB-AlAhli",
                shouldParse = false
            ),
            ParserTestCase(
                name = "Successful online purchase with similar structure remains an expense",
                message = "شراء محلي عبر الانترنت\nبـ12.34 SAR\nمن SYNTHETIC MARKET\nبطاقة *0007",
                sender = "SNB-AlAhli",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "SYNTHETIC MARKET",
                    accountLast4 = "0007",
                    isFromCard = true
                )
            )
        )

        return ParserTestUtils.runTestSuite(parser, cases)
    }

    @TestFactory
    fun `factory resolves snb alahli`(): List<DynamicTest> {
        val cases = listOf(
            SimpleTestCase(
                bankName = "Saudi National Bank",
                sender = "SNB-AlAhli",
                currency = "SAR",
                message = "شراء نقاط بيع SamsungPay\nبـSAR 12.34\nمن SYNTHETIC STORE\nمدى *0007\nفي 00:00 01/01/30",
                expected = ExpectedTransaction(
                    amount = BigDecimal("12.34"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE
                ),
                shouldHandle = true
            )
        )

        return ParserTestUtils.runFactoryTestSuite(cases, "Factory smoke tests")
    }
}
