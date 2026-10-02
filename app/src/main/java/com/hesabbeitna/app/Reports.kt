package com.hesabbeitna.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.ByteArrayOutputStream

object Reports {
    fun csv(data: Household, period: Finance.Period): ByteArray {
        fun cell(value: String): String {
            // Prevent spreadsheet formula injection from notes/category/account names.
            val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
            return "\"${safe.replace("\"", "\"\"")}\""
        }
        val lines = mutableListOf(listOf("المعرف", "التاريخ", "النوع", "المبلغ بالجنيه", "الحساب", "الحساب المستلم", "التصنيف", "طريقة الدفع", "الملاحظة", "الاستحقاق", "العملية الأصلية"))
        data.transactions.filter { period.contains(java.time.LocalDate.parse(it.date)) }.sortedBy { it.date }.forEach { t ->
            lines += listOf(t.id,t.date,typeLabel(t.type),java.math.BigDecimal.valueOf(t.amount,2).toPlainString(),
                data.account(t.accountId),data.account(t.destinationId),data.category(t.categoryId),t.payment,t.note,t.dueId ?: "",t.originalId ?: "")
        }
        return ("\uFEFF" + lines.joinToString("\r\n") { row -> row.joinToString(",") { cell(it) } }).encodeToByteArray()
    }
    fun pdf(data: Household, period: Finance.Period): ByteArray {
        val lines = mutableListOf("حساب بيتنا — تقرير فعلي", periodLabel(period),
            "بداية المتابعة: ${data.prefs.trackingStart}", "يعكس السجلات المدخلة فقط؛ لا يثبت اكتمال التسجيل",
            "الدخل: ${money(Finance.income(data.entries(),period))}",
            "صافي المصروفات: ${money(Finance.expense(data.entries(),period))}",
            "المتبقي من الدخل: ${money(Finance.income(data.entries(),period)-Finance.expense(data.entries(),period))}",
            "التحويلات والأرصدة الافتتاحية مستبعدة من الدخل والمصروف", "توزيع المصروفات:")
        Finance.categories(data.entries(),period).entries.sortedByDescending { it.value }.forEach {
            lines += "${data.category(it.key)}: ${money(it.value)}"
        }
        lines += "العمليات في الفترة:"
        data.transactions.filter { period.contains(java.time.LocalDate.parse(it.date)) }.sortedBy { it.date }.forEach {
            lines += "${it.date} | ${typeLabel(it.type)} | ${money(it.amount)} | ${data.category(it.categoryId)} | ${data.account(it.accountId)}"
        }
        val doc = PdfDocument()
        val paint = TextPaint().apply { textSize = 13f; color = Color.rgb(25,55,49); typeface = Typeface.DEFAULT }
        var number = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595,842,number).create())
        var y = 35f
        try {
            lines.forEach { text ->
                val layout = StaticLayout.Builder.obtain(text,0,text.length,paint,525)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.RTL)
                    .setLineSpacing(3f,1f).build()
                if (y + layout.height > 797) {
                    doc.finishPage(page); page = doc.startPage(PdfDocument.PageInfo.Builder(595,842,++number).create()); y = 35f
                }
                page.canvas.save(); page.canvas.translate(35f,y); layout.draw(page.canvas); page.canvas.restore()
                y += layout.height + 10
            }
            doc.finishPage(page)
            return ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        } finally { doc.close() }
    }
}

fun typeLabel(type: TxType) = when (type) { TxType.EXPENSE -> "مصروف"; TxType.INCOME -> "دخل"; TxType.TRANSFER -> "تحويل"; TxType.REFUND -> "استرداد" }
