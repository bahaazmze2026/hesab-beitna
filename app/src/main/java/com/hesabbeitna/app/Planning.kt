package com.hesabbeitna.app

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** Plans and templates never enter the ledger until a user reviews and saves a transaction. */
@Serializable data class MonthlyPlan(val period: String, val income: Long, val variable: Long,
    val commitments: Long, val saving: Long, val reserve: Long, val incomeDate: String,
    val note: String = "") {
    val spending: Long get() = variable + commitments
    val allocated: Long get() = spending + saving + reserve
}
@Serializable data class QuickTemplate(val id: String = newId(), val title: String,
    val type: TxType = TxType.EXPENSE, val amount: Long, val accountId: String,
    val categoryId: String, val note: String = "")

fun validatePlanning(data: Household) {
    require(data.plans.map { it.period }.distinct().size == data.plans.size) { "خطة مكررة لنفس الدورة" }
    require(data.templates.map { it.id }.distinct().size == data.templates.size)
    data.plans.forEach { p ->
        val start = LocalDate.parse(p.period)
        val date = LocalDate.parse(p.incomeDate)
        require(!date.isBefore(start) && date.isBefore(start.plusMonths(1).plusDays(4))) { "موعد الدخل خارج نطاق الخطة" }
        require(listOf(p.income, p.variable, p.commitments, p.saving, p.reserve).all { it in 0..100_000_000_000L })
        require(p.note.length <= 500)
    }
    data.templates.forEach { t ->
        require(t.title.isNotBlank() && t.title.length <= 60 && t.note.length <= 500)
        require(t.type in listOf(TxType.EXPENSE, TxType.INCOME))
        require(t.amount in 1..100_000_000_000L)
        require(data.accounts.any { it.id == t.accountId })
        require(data.categories.any { it.id == t.categoryId && it.income == (t.type == TxType.INCOME) })
    }
}
fun Household.plan(period: Finance.Period) = plans.firstOrNull { it.period == period.start.toString() }
fun Household.templateAvailable(template: QuickTemplate) = accounts.any { it.id == template.accountId && !it.archived } &&
    categories.any { it.id == template.categoryId && !it.archived }
fun QuickTemplate.draft() = Transaction(type = type, amount = amount, accountId = accountId,
    categoryId = categoryId, note = note) // New ID/date each use; no automatic saving.

fun Household.savePlan(plan: MonthlyPlan): Household = copy(schema = 2,
    plans = plans.filterNot { it.period == plan.period } + plan).validate()

/** A snapshot of known due amounts, so paying a bill does not shrink the saved spending plan. */
fun commitmentPlan(data: Household, period: Finance.Period): Long = data.dues.sumOf { due ->
    val date = LocalDate.parse(due.date)
    when { period.contains(date) -> due.amount; date < period.start -> data.remaining(due); else -> 0L }
}
data class PlanProgress(val income: Long, val variable: Long, val commitments: Long) {
    val spending get() = variable + commitments
    val surplus get() = income - spending
}
fun planProgress(data: Household, period: Finance.Period) = PlanProgress(
    Finance.income(data.entries(), period),
    Finance.expense(data.entries().filter { it.due == null }, period),
    Finance.expense(data.entries().filter { it.due != null }, period))

data class CalendarEvent(val id: String, val date: String, val title: String, val amount: Long,
    val kind: String, val state: String)
fun calendarEvents(data: Household, month: java.time.YearMonth): List<CalendarEvent> {
    val bills = data.dues.filter { java.time.YearMonth.from(LocalDate.parse(it.date)) == month }.map { due ->
        CalendarEvent(due.id, due.date, due.title, data.remaining(due), "due",
            when { data.remaining(due) == 0L -> "مدفوع"; LocalDate.parse(due.date) < LocalDate.now() -> "متأخر";
                data.paid(due) > 0L -> "سداد جزئي"; else -> "مستحق" })
    }
    val income = data.plans.filter { java.time.YearMonth.from(LocalDate.parse(it.incomeDate)) == month }.map {
        CalendarEvent(it.period, it.incomeDate, "دخل الخطة المتوقع", it.income, "income", "مخطط فقط")
    }
    return (bills + income).sortedWith(compareBy({ it.date }, { it.kind }, { it.id }))
}
