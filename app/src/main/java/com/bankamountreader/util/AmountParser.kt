package com.bankamountreader.util

import java.text.DecimalFormat

/**
 * AmountParser — parse ยอดเงินจากข้อความ notification ธนาคาร
 *
 * รองรับ KBank (K PLUS) และ TrueMoney Wallet:
 *   "รับโอน 1,500.00 บาท"
 *   "รับเงิน ฿ 10.00 ผ่าน พร้อมเพย์"
 *   "บัญชี xxx-x-x5815-x จำนวนเงิน 10.00 บาท"
 *   "โอนเข้า 250.50 THB"
 */
object AmountParser {

    private val fmt = DecimalFormat("#,##0.00")

    private val AMOUNT_REGEX = Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")

    private val RECEIVE_KEYWORDS = listOf(
        "รับโอน", "รับเงิน", "received", "receive",
        "โอนเข้า", "เข้าบัญชี", "credit", "จำนวนเงิน"
    )

    fun parse(text: String): Double? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        if (RECEIVE_KEYWORDS.none { lower.contains(it) }) return null

        return AMOUNT_REGEX.findAll(text)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .filter { it > 0.0 }
            .maxOrNull()
    }

    fun format(amount: Double): String = "฿${fmt.format(amount)}"
}
