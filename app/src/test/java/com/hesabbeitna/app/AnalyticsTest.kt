package com.hesabbeitna.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AnalyticsTest {
    private val cash=Account("cash","النقد")
    private fun tx(id:String,amount:Long,date:String="2026-03-03",cat:String="expense-0",type:TxType=TxType.EXPENSE,due:String?=null)=Transaction(id,type,amount,date,"cash",categoryId=cat,dueId=due,created=id.hashCode().toLong())
    private fun house(rows:List<Transaction>,tracking:String="2026-01-01",salary:Int=1)=Household(accounts=listOf(cash),transactions=rows,prefs=Preferences(ready=true,salaryDay=salary,trackingStart=tracking,defaultAccount="cash"))
    private val march=Finance.Period(LocalDate.parse("2026-03-01"),LocalDate.parse("2026-04-01"))
    private val endMarch=LocalDate.parse("2026-03-31")
    @Test fun separateGrossRefundsAndNetWithoutCountingTransfers() {
        val rows=listOf(tx("a",10_000),tx("b",20_000),tx("refund",5_000,type=TxType.REFUND),tx("salary",90_000,type=TxType.INCOME),Transaction("move",TxType.TRANSFER,50_000,"2026-03-03","cash",destinationId="wallet"))
        val report=Analytics(house(rows),march,endMarch)
        assertEquals(30_000L,report.gross);assertEquals(5_000L,report.refunds);assertEquals(25_000L,report.expense);assertEquals(65_000L,report.surplus)
        assertEquals(2,report.categories.single().count);assertEquals(15_000L,report.categories.single().average)
        assertEquals("b",report.categories.single().largest!!.id)
    }
    @Test fun comparisonHasEqualCoverageAndDecomposesTotalChange() {
        val p=Finance.Period(LocalDate.parse("2026-03-25"),LocalDate.parse("2026-04-25"))
        val rows=listOf(tx("prev",10_000,"2026-02-25"),tx("outside",99_000,"2026-03-04"),tx("now",20_000,"2026-03-26"),tx("other",5_000,"2026-03-27",cat="expense-1"))
        val report=Analytics(house(rows,salary=25),p,LocalDate.parse("2026-03-28"))
        assertTrue(report.comparable);assertEquals(4L,report.windows[0].days());assertEquals(4L,report.windows[1].days())
        assertEquals(15_000L,report.changes.sumOf{it.delta});assertEquals(10_000L,report.changes.first{it.id=="expense-0"}.delta)
    }
    @Test fun partialPreviousTrackingSuppressesComparison() {
        assertFalse(Analytics(house(listOf(tx("a",1000)),tracking="2026-02-10"),march,endMarch).comparable)
    }
    @Test fun refundOnlyPeriodHasNegativeNetAndNoFalseSaving() {
        val data=house(listOf(tx("refund",5000,type=TxType.REFUND)))
        val report=Analytics(data,march,endMarch);val sim=report.simulate("expense-0",50)
        assertEquals(-5000L,report.expense);assertEquals(0L,sim.saving);assertEquals(-5000L,sim.simulatedExpense)
        assertNull(report.categories.single().largest)
    }
    @Test fun simulationRoundsToPiastresAndNeverMutatesRecords() {
        val data=house(listOf(tx("a",10001)));val report=Analytics(data,march,endMarch)
        val sim=report.simulate("expense-0",15)
        assertEquals(1500L,sim.saving);assertEquals(8501L,sim.simulatedExpense);assertEquals(-8501L,sim.simulatedSurplus)
        assertEquals(10001L,data.transactions.single().amount)
        assertEquals(0L,report.simulate("missing",15).saving)
        assertEquals(0L,report.simulate("expense-0",0).saving)
        assertEquals(5001L,report.simulate("expense-0",50).saving)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsInvalidSimulationRate(){Analytics(house(emptyList()),march,endMarch).simulate("expense-0",51)}
    @Test fun bucketZerosCoverOnlyTrackedElapsedDays() {
        val report=Analytics(house(listOf(tx("a",4000,"2026-03-03")),tracking="2026-03-02"),march,LocalDate.parse("2026-03-04"))
        assertEquals(listOf("2026-03-02" to 0L,"2026-03-03" to 4000L,"2026-03-04" to 0L),report.buckets("day"))
        assertEquals(listOf("a"),report.bucketTransactions("2026-03-03","day").map{it.id})
        assertEquals(3L,report.coveredDays)
    }
    @Test fun futureCycleDoesNotFabricateCoverageOrComparisons() {
        val p=Finance.Period(LocalDate.parse("2026-05-01"),LocalDate.parse("2026-06-01"));val report=Analytics(house(emptyList()),p,endMarch)
        assertEquals(0L,report.coveredDays);assertTrue(report.buckets("day").isEmpty());assertFalse(report.comparable);assertNull(report.forecast)
    }
    @Test fun nextPlanSeparatesVariableCommitmentsAndCarryover() {
        val rows=listOf(tx("variable",20_000,"2026-03-10"),tx("paid",1000,"2026-03-10",due="overdue"),tx("income",100_000,"2026-03-01",type=TxType.INCOME))
        val data=house(rows,tracking="2026-03-01").copy(dues=listOf(Due("overdue","r","قديم","expense-0","2026-03-10",3000),Due("next","r","قادم","expense-0","2026-05-02",5000),Due("later","r","لاحق","expense-0","2026-06-02",7000)))
        val plan=Analytics.nextPlan(data,LocalDate.parse("2026-04-15"))
        assertEquals("2026-05-01",plan.period.start.toString());assertEquals(20_000L,plan.variable);assertEquals(100_000L,plan.income)
        assertEquals(2000L,plan.carryover);assertEquals(5000L,plan.commitments);assertEquals(7000L,plan.obligations);assertEquals(2,plan.dues.size)
    }
    @Test fun noHistoryMeansUnknownBaselineRatherThanZero() {
        val plan=Analytics.nextPlan(house(emptyList(),tracking="2026-04-15"),LocalDate.parse("2026-04-15"))
        assertNull(plan.variable);assertNull(plan.income);assertTrue(plan.history.isEmpty())
    }
    @Test fun similarPaymentsAreReviewSignalsNotDeleted() {
        val rows=listOf(tx("a",1000),tx("b",1000),tx("other",1000,cat="expense-1"),tx("income",1000,type=TxType.INCOME))
        val report=Analytics(house(rows),march,endMarch);val signal=report.signals().single()
        assertEquals(setOf("a","b"),signal.transactions.map{it.id}.toSet());assertEquals(4,report.data.transactions.size)
    }
    @Test fun unusuallyLargeExpenseNeedsFivePriorVariablePayments() {
        val past=(1..5).map{tx("history-$it",1000,"2026-02-${10+it}")}
        val high=tx("high",3000,"2026-03-03")
        assertTrue(Analytics(house(past.take(4)+high),march,endMarch).signals().isEmpty())
        assertEquals("high",Analytics(house(past+high),march,endMarch).signals().single().transactions.single().id)
        assertTrue(Analytics(house(past+high.copy(dueId="bill")),march,endMarch).signals().isEmpty())
    }
    @Test fun dailyAllowanceReservesUnpaidObligations() {
        val data=house(listOf(tx("a",20_000,"2026-03-03"))).copy(budgets=listOf(Budget("2026-03-01",100_000)),dues=listOf(Due("due","r","فاتورة","expense-0","2026-03-20",10_000)))
        val report=Analytics(data,march,LocalDate.parse("2026-03-10"))
        assertEquals(21L,report.daysLeft);assertEquals(3333L,report.availableDaily)
    }
    @Test fun spikesUseMedianSoOneOldLargePurchaseDoesNotSkewBaseline() {
        val past=(1..5).map{tx("p-$it",if(it==5)100_000 else 1000,"2026-02-${10+it}")}
        assertEquals(1,Analytics(house(past+tx("new",4000)),march,endMarch).signals().size)
    }
}
