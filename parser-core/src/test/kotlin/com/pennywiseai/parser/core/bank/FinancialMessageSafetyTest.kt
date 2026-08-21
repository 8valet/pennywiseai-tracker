package com.pennywiseai.parser.core.bank

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FinancialMessageSafetyTest {

    @Test
    fun `Arabic PIN authentication phrase is security evidence`() {
        val message = "الرقم السري لتأكيد شراء عبر الانترنت: <CODE>\nمبلغ 12.34 SAR"

        assertTrue(FinancialMessageSafety.isSecurityCode(message))
    }

    @Test
    fun `generic Arabic code words alone are not security evidence`() {
        assertFalse(FinancialMessageSafety.isSecurityCode("رمز المنتج: SYNTHETIC"))
        assertFalse(FinancialMessageSafety.isSecurityCode("كود الفرع: SYNTHETIC"))
    }
}
