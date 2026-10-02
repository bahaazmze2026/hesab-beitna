package com.hesabbeitna.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HouseholdTest {
    private val start=LocalDate.now().withDayOfMonth(1).toString()
    private fun house()=Household(accounts=listOf(Account("cash","النقد",opening=100_000),Account("wallet","المحفظة",kind="محفظة")),
        prefs=Preferences(ready=true,salaryDay=1,trackingStart=start,defaultAccount="cash"))
    private fun expense(id:String="tx",amount:Long=10_000,due:String?=null)=Transaction(id,TxType.EXPENSE,amount,start,"cash",categoryId="expense-0",dueId=due)
    private fun rejects(block:()->Unit) { var failed=false;try{block()}catch(_:IllegalArgumentException){failed=true};assertTrue(failed) }
    @Test fun manualThemeOverridesPhoneInBothDirections() {
        assertFalse(resolveDark(ThemeMode.LIGHT,true))
        assertFalse(resolveDark(ThemeMode.LIGHT,false))
        assertTrue(resolveDark(ThemeMode.DARK,false))
        assertTrue(resolveDark(ThemeMode.DARK,true))
        assertTrue(resolveDark(ThemeMode.SYSTEM,true))
        assertFalse(resolveDark(ThemeMode.SYSTEM,false))
    }
    @Test fun transferIsNotIncomeOrExpense() {
        val data=house().copy(transactions=listOf(Transaction("transfer",TxType.TRANSFER,50_000,start,"cash",destinationId="wallet"))).validate()
        assertEquals(50_000L,data.balance(data.accounts[0]));assertEquals(50_000L,data.balance(data.accounts[1]))
        assertEquals(0L,Finance.income(data.entries(),data.cycle(LocalDate.parse(start))))
        assertEquals(0L,Finance.expense(data.entries(),data.cycle(LocalDate.parse(start))))
    }
    @Test fun materializingIsIdempotentAndPaidPartial() {
        val rule=BillRule("rent","إيجار",90_000,"expense-0",start)
        val data=house().copy(rules=listOf(rule)).materialize()
        assertEquals(data.dues,data.materialize().dues)
        val due=data.dues.first()
        val paid=data.copy(transactions=listOf(expense(amount=40_000,due=due.id))).validate()
        assertEquals(50_000L,paid.remaining(due))
        rejects {paid.copy(transactions=paid.transactions+expense("excess",60_000,due.id)).validate()}
    }
    @Test fun refundsMustReferenceValidExpenseAndCannotOverRefund() {
        val source=expense()
        val refund=Transaction("refund",TxType.REFUND,2_000,start,"wallet",categoryId="expense-0",originalId=source.id)
        val data=house().copy(transactions=listOf(source,refund)).validate()
        assertEquals(8_000L,Finance.expense(data.entries(),data.cycle(LocalDate.parse(start))))
        rejects { data.copy(transactions=listOf(refund)).validate() }
        rejects { data.copy(transactions=listOf(source,refund.copy(amount=10_001))).validate() }
        rejects { data.copy(transactions=listOf(source,refund.copy(categoryId="expense-1"))).validate() }
    }
    @Test fun duplicateIdsAndMissingAccountsAreRejected() {
        val tx=expense()
        rejects {house().copy(transactions=listOf(tx,tx)).validate()}
        rejects {house().copy(transactions=listOf(tx.copy(accountId="missing"))).validate()}
        rejects {house().copy(transactions=listOf(tx.copy(amount=0))).validate()}
        rejects {house().copy(transactions=listOf(tx.copy(date=LocalDate.now().plusDays(1).toString()))).validate()}
    }
    @Test fun archivedCategoriesKeepHistory() {
        val data=house().copy(transactions=listOf(expense()),categories=Household.defaults().map {if(it.id=="expense-0")it.copy(archived=true)else it}).validate()
        assertEquals("الطعام",data.category("expense-0"))
        assertEquals(90_000L,data.balance(data.accounts.first()))
    }
    @Test fun backupRoundTripPreservesFullTopology() {
        val data=house().copy(transactions=listOf(expense()),budgets=listOf(Budget(start,100_000))).validate()
        val pass="independent-backup-test".toCharArray()
        val bytes=Backup.encrypt(data,pass)
        assertEquals(data,Backup.decrypt(bytes,pass))
        var rejected=false;try{Backup.decrypt(bytes,"wrong-password".toCharArray())}catch(_:Exception){rejected=true}
        assertTrue(rejected)
    }
    @Test fun repeatedSalaryPeriodsAndEqualWindow() {
        for(day in 1..31) for(month in 1..12) {
            val date=LocalDate.of(2026,month,15);val p=Finance.period(date,day)
            assertTrue(p.contains(date));assertTrue(p.days() in 28..31)
            val cmp=Finance.comparison(p,date,day);assertEquals(cmp[0].days(),cmp[1].days())
        }
    }
    @Test fun csvEscapesFormulaAndPreservesArabic() {
        val data=house().copy(transactions=listOf(expense().copy(note="=SUM(A1)")))
        val text=Reports.csv(data,data.cycle(LocalDate.parse(start))).decodeToString()
        assertTrue(text.startsWith("\uFEFF"));assertTrue(text.contains("'=SUM(A1)"));assertTrue(text.contains("الطعام"))
    }
}
