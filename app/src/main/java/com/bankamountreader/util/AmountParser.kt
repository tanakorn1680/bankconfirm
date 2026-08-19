package com.bankamountreader.util

import java.text.DecimalFormat

/**
 * AmountParser — parse ยอดเงินจาก notification ธนาคาร
 *
 * KBank:      "บัญชี xxx จำนวนเงิน 1.00 บาท วันที่..."
 * TrueMoney:  "คุณได้รับเงิน ฿ 10.00 ผ่าน พร้อมเพย์"
 *             "คุณได้รับเงิน 10.00 บาท จาก..."  (ช่องทางอื่น)
 */
object AmountParser {

    private val fmt = DecimalFormat("#,##0.00")

    // ตัวเลขที่มีหรือไม่มี comma + decimal 1-2 ตำแหน่ง
    private val AMOUNT_REGEX = Regex("""(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")

    // ─── KBank ────────────────────────────────────────────────────────────────
    // "จำนวนเงิน 1.00 บาท"
    private val KBANK_AMOUNT = Regex("""จำนวนเงิน\s+([\d,]+(?:\.\d{1,2})?)\s+บาท""")

    // ─── TrueMoney ────────────────────────────────────────────────────────────
    // ยืดหยุ่น: ขอแค่ "ได้รับ" หรือ "รับเงิน" อยู่ใน text
    // แล้วจับตัวเลขที่ตามมาด้วย ฿ หรือ บาท
    private val TMN_RECEIVE_KEYWORDS = listOf("ได้รับ", "รับเงิน")
    private val TMN_AMOUNT_BAHT  = Regex("""฿\s*([\d,]+(?:\.\d{1,2})?)""")
    private val TMN_AMOUNT_TEXT  = Regex("""([\d,]+(?:\.\d{1,2})?)\s*บาท""")

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * parseKBank — เรียกเฉพาะเมื่อ package = KBank
     * คืน null ถ้า parse ไม่ได้
     */
    fun parseKBank(title: String, text: String): Double? {
        // KBank title ที่ต้องการ: มีคำว่า "เงินเข้า"
        if (!title.contains("เงินเข้า")) return null

        val fullText = "$title $text"
        return KBANK_AMOUNT.find(fullText)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }
            // fallback: เอาตัวเลขใหญ่สุดในข้อความ
            ?: extractLargest(fullText)
    }

    /**
     * parseTrueMoney — เรียกเฉพาะเมื่อ package = TrueMoney
     * ไม่ยึด title เพราะ TrueMoney ปรับได้
     * ยึด keyword "ได้รับ"/"รับเงิน" + มีตัวเลขยอด
     */
    fun parseTrueMoney(title: String, text: String): Double? {
        val fullText = "$title $text".lowercase()

        // ต้องมี keyword รับเงิน
        if (TMN_RECEIVE_KEYWORDS.none { fullText.contains(it) }) return null

        val raw = "$title $text"

        // ลอง ฿ X.XX ก่อน
        TMN_AMOUNT_BAHT.find(raw)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }

        // ลอง X.XX บาท
        TMN_AMOUNT_TEXT.find(raw)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { return it }

        return null
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun extractLargest(text: String): Double? =
        AMOUNT_REGEX.findAll(text)
            .mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .filter { it > 0 }
            .maxOrNull()

    fun format(amount: Double): String = "฿${fmt.format(amount)}"
}
