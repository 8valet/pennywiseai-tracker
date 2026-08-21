package com.pennywiseai.tracker.worker

import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MmsRcsMessageSupportTest {

    // Transaction fixtures are synthetic; provider identifiers exercise only routing grammar.

    @Test
    fun `legacy decoded proto sender extraction remains unchanged`() {
        assertEquals(
            "Mobily Pay",
            MmsRcsMessageSupport.senderFromDecodedProtoPayload("mobily_pay_synthetic_agent@rbm.goog")
        )
    }

    @Test
    fun `non proto Samsung transaction ids are RCS candidates`() {
        assertTrue(MmsRcsMessageSupport.isRcsCandidate("T0000000"))
        assertFalse(MmsRcsMessageSupport.isProtoRcs("T0000000"))
        assertTrue(MmsRcsMessageSupport.isProtoRcs("proto:abc"))
        assertFalse(MmsRcsMessageSupport.isRcsCandidate(null))
        assertFalse(MmsRcsMessageSupport.isRcsCandidate("   "))
    }

    @Test
    fun `MobilyPay MMS FROM address is accepted while provider placeholders are rejected`() {
        assertEquals("MobilyPay", MmsRcsMessageSupport.validMmsSender("MobilyPay"))
        assertNull(MmsRcsMessageSupport.validMmsSender("insert-address-token"))
        assertNull(MmsRcsMessageSupport.validMmsSender("  INSERT-ADDRESS-TOKEN  "))
        assertNull(MmsRcsMessageSupport.validMmsSender(null))
        assertNull(MmsRcsMessageSupport.validMmsSender("   "))
    }

    @Test
    fun `plain text MMS part returns the complete body`() {
        val body = """
            Card Purchase
            Amount: 12.34 SAR
            At: SYNTHETIC STORE
            Card Number:VISA****0007
            On: 01-01-2030 00:00:00
            Current balance: 99.99 SAR
        """.trimIndent()

        assertEquals(body, MmsRcsMessageSupport.inlineTextPart("text/plain", body))
        assertNull(MmsRcsMessageSupport.inlineTextPart("image/jpeg", body))
    }

    @Test
    fun `MobilyPay MMS body reaches the registered parser`() {
        val body = """
            Card Purchase
            Amount: 12.34 SAR
            At: SYNTHETIC STORE
            Card Number:VISA****0007
            On: 01-01-2030 00:00:00
            Current balance: 99.99 SAR
        """.trimIndent()

        val parsed = BankParserFactory.parse(body, "MobilyPay", 0L)

        requireNotNull(parsed)
        assertEquals(BigDecimal("12.34"), parsed.amount)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("SYNTHETIC STORE", parsed.merchant)
        assertEquals("0007", parsed.accountLast4)
        assertEquals(BigDecimal("99.99"), parsed.balance)
    }
}
