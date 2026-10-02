package com.hesabbeitna.app

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PlanningTest {
    private val start = LocalDate.now().withDayOfMonth(1)
    private val period = Finance.Period(start, start.plusMonths(1))
    private fun household() = Household(accounts = listOf(Account("cash", "نقد", opening = 100_000)), prefs = Preferences(ready = true, salaryDay = 1, trackingStart = start.minusMonths(1).toString(), defaultAccount = "cash"))
    private fun plan() = MonthlyPlan(start.toString(), 100_000, 40_000, 20_000, 20_000, 10_000, start.toString())
    @Test fun savingAndReplacingPlanNeverChangesLedgerOrBudget() {
        val original = household().copy(budgets = listOf(Budget(start.toString(), 9999)))
        val saved = original.savePlan(plan()).savePlan(plan().copy(saving = 25_000))
        assertEquals(1, saved.plans.size); assertEquals(25_000L, saved.plans.single().saving)
        assertEquals(original.transactions, saved.transactions); assertEquals(original.budgets, saved.budgets)
        assertEquals(original.balance(original.accounts.single()), saved.balance(saved.accounts.single()))
    }
    @Test fun jsonRoundTripPreservesPlansAndTemplatesAndLegacyDefaults() {
        val json = Json { encodeDefaults = true }
        val template = QuickTemplate("t", "مواصلات", amount = 1250, accountId = "cash", categoryId = "expense-5")
        val new = household().savePlan(plan()).copy(templates = listOf(template)).validate()
        assertEquals(new, json.decodeFromString<Household>(json.encodeToString(new)).validate())
        val old = json.decodeFromString<Household>("""{"schema":1}""").validate()
        assertTrue(old.plans.isEmpty()); assertTrue(old.templates.isEmpty()); assertEquals(1, old.schema)
    }
    @Test fun templateAlwaysMakesFreshReviewedDraftAndDoesNotModifyHousehold() {
        val t = QuickTemplate("t", "طعام", amount = 999, accountId = "cash", categoryId = "expense-0")
        val before = household().copy(templates = listOf(t)).validate()
        val a = t.draft(); val b = t.draft()
        assertNotEquals(a.id, b.id); assertEquals(today(), a.date); assertEquals(999L, a.amount)
        assertTrue(before.transactions.isEmpty()); assertEquals(100_000L, before.balance(before.accounts.single()))
        assertFalse(before.copy(accounts = listOf(before.accounts.single().copy(archived = true))).templateAvailable(t))
    }
    @Test fun billPaymentAndRefundUpdateProgressWithoutShrinkingPlanSnapshot() {
        val rule = BillRule("rule", "فاتورة", 5000, "expense-1", start.toString())
        val due = Due("due", "rule", "فاتورة", "expense-1", start.toString(), 5000)
        val before = household().copy(rules = listOf(rule), dues = listOf(due))
        assertEquals(5000L, commitmentPlan(before, period))
        val expense = Transaction("pay", TxType.EXPENSE, 3000, start.toString(), "cash", categoryId = "expense-1", dueId = "due")
        val refund = Transaction("refund", TxType.REFUND, 1000, start.toString(), "cash", categoryId = "expense-1", dueId = "due", originalId = "pay")
        val paid = before.copy(transactions = listOf(expense, refund)).savePlan(plan())
        assertEquals(5000L, commitmentPlan(paid, period)); assertEquals(3000L, paid.remaining(due))
        assertEquals(2000L, planProgress(paid, period).commitments)
        assertEquals(0L, planProgress(paid, period).variable)
        assertEquals(20_000L, paid.plans.single().commitments)
    }
    @Test fun calendarIsMonthBoundedAndExpectedIncomeNeverChangesBalance() {
        val data = household().savePlan(plan())
        val events = calendarEvents(data, YearMonth.from(start))
        assertEquals("income", events.single().kind); assertEquals("مخطط فقط", events.single().state)
        assertTrue(calendarEvents(data, YearMonth.from(start).plusMonths(1)).isEmpty())
        assertEquals(0L, planProgress(data, period).income); assertEquals(100_000L, data.balance(data.accounts.single()))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectDanglingTemplateAccount() {
        household().copy(templates = listOf(QuickTemplate(title = "قالب", amount = 100, accountId = "missing", categoryId = "expense-0"))).validate()
    }
    @Test(expected = IllegalArgumentException::class) fun rejectNegativePlanningAmount() { household().savePlan(plan().copy(saving = -1)) }
    @Test(expected = IllegalArgumentException::class) fun rejectFutureUnsupportedSchema() { household().copy(schema = 3).validate() }
    @Test fun incomeFundingGapCanBeSavedForExplicitReview() {
        val p = plan().copy(income = 1000)
        assertTrue(household().savePlan(p).plans.single().allocated > p.income)
    }
}
