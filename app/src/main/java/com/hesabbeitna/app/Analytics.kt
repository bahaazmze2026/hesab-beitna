package com.hesabbeitna.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Derived, read-only analytics. Money stays in integer piastres; transfers never enter spending. */
data class CategoryAnalysis(val id:String,val gross:Long,val refunds:Long,val count:Int,val transactions:List<Transaction>) {
    val net get()=gross-refunds
    val average get()=if(count>0)Finance.average(gross,count.toLong())else 0L
    val largest get()=transactions.filter{it.type==TxType.EXPENSE}.maxByOrNull{it.amount}
}
data class CategoryChange(val id:String,val current:CategoryAnalysis,val previous:CategoryAnalysis) {
    val delta get()=current.net-previous.net
    val driver:String get()=when {
        previous.count==0&&current.count>0->"ظهر صرف مسجل في هذه المدة لم يظهر في المدة السابقة"
        current.gross==previous.gross&&current.refunds!=previous.refunds->"تغير الصافي بسبب اختلاف الاستردادات، مع ثبات المدفوعات"
        current.count>previous.count&&current.average>previous.average->"عدد العمليات ومتوسط قيمة العملية ارتفعا معًا"
        current.count>previous.count->"عدد العمليات ارتفع؛ راجع التكرار بجانب متوسط العملية"
        current.count<previous.count&&current.average>previous.average->"عدد العمليات انخفض لكن متوسط قيمة العملية ارتفع"
        current.count<previous.count->"عدد العمليات انخفض؛ راجع متوسط العملية والاستردادات أيضًا"
        current.average>previous.average->"متوسط قيمة العملية ارتفع مع ثبات عدد العمليات"
        current.average<previous.average->"متوسط قيمة العملية انخفض مع ثبات عدد العمليات"
        else->"العدد ومتوسط المدفوعات لم يتغيرا؛ راجع الاستردادات والصافي"
    }
}
data class SpendingSignal(val title:String,val explanation:String,val transactions:List<Transaction>)
data class AnalyticsCycle(val period:Finance.Period,val income:Long,val expense:Long)
data class SavingSimulation(val baseline:Long,val saving:Long,val simulatedExpense:Long,val simulatedSurplus:Long)
data class CyclePlan(val period:Finance.Period,val history:List<Finance.Period>,val variable:Long?,val income:Long?,val commitments:Long,val carryover:Long,val dues:List<Due>) {
    val obligations get()=commitments+carryover
}
class Analytics(val data:Household,val period:Finance.Period,val asOf:LocalDate=LocalDate.now()) {
    private val tracking=LocalDate.parse(data.prefs.trackingStart)
    val coveredStart=Finance.max(period.start,tracking)
    val coveredEnd=Finance.min(period.end,asOf.plusDays(1))
    val coveredDays=ChronoUnit.DAYS.between(coveredStart,coveredEnd).coerceAtLeast(0)
    val transactions=data.transactions.filter{period.contains(LocalDate.parse(it.date))&&!LocalDate.parse(it.date).isAfter(asOf)}
    val income=transactions.filter{it.type==TxType.INCOME}.sumOf{it.amount}
    val gross=transactions.filter{it.type==TxType.EXPENSE}.sumOf{it.amount}
    val refunds=transactions.filter{it.type==TxType.REFUND}.sumOf{it.amount}
    val expense=gross-refunds
    val surplus=income-expense
    val categories=stats(transactions).sortedByDescending{it.net}
    val windows=Finance.comparison(period,Finance.min(asOf,period.end.minusDays(1)),data.prefs.salaryDay)
    val comparable=windows[0].days()>0&&!windows[1].start.isBefore(tracking)&&!windows[0].start.isBefore(tracking)
    val changes:List<CategoryChange> = if(comparable) {
        val current=stats(data.transactions.filter{windows[0].contains(LocalDate.parse(it.date))}).associateBy{it.id}
        val prior=stats(data.transactions.filter{windows[1].contains(LocalDate.parse(it.date))}).associateBy{it.id}
        (current.keys+prior.keys).map{id->CategoryChange(id,current[id]?:emptyCategory(id),prior[id]?:emptyCategory(id))}.sortedByDescending{it.delta}
    } else emptyList()
    val outstanding=data.dues.filter{LocalDate.parse(it.date)<period.end}.sumOf{data.remaining(it)}
    val forecast:Long?=Finance.forecast(data.entries(),period,tracking,asOf,outstanding)
    val daysLeft=if(period.contains(asOf))ChronoUnit.DAYS.between(asOf.plusDays(1),period.end).coerceAtLeast(0)else 0L
    val availableDaily:Long?=data.budget(period)?.let {budget->if(daysLeft>0) ((budget.amount-expense-outstanding).coerceAtLeast(0)/daysLeft) else null}

    fun buckets(unit:String):List<Pair<String,Long>> {
        require(unit in listOf("day","week","month"))
        val raw=Finance.buckets(transactions.map{it.entry()},period,unit).toSortedMap()
        var cursor=coveredStart
        while(cursor<coveredEnd){
            val key=when(unit){"month"->YearMonth.from(cursor).toString();"week"->cursor.minusDays((cursor.dayOfWeek.value%7).toLong()).toString();else->cursor.toString()}
            raw.putIfAbsent(key,0L);cursor=cursor.plusDays(1)
        }
        return raw.entries.map{it.key to it.value}
    }
    fun bucketTransactions(key:String,unit:String)=transactions.filter {
        val date=LocalDate.parse(it.date)
        val bucket=when(unit){"month"->YearMonth.from(date).toString();"week"->date.minusDays((date.dayOfWeek.value%7).toLong()).toString();else->date.toString()}
        bucket==key&&it.type in listOf(TxType.EXPENSE,TxType.REFUND)
    }
    fun simulate(categoryId:String,reduction:Int):SavingSimulation {
        require(reduction in 0..50)
        val base=categories.firstOrNull{it.id==categoryId}?.net?.coerceAtLeast(0)?:0L
        val saving=BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(reduction.toLong())).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).longValueExact()
        return SavingSimulation(base,saving,expense-saving,surplus+saving)
    }
    fun cycleHistory():List<AnalyticsCycle> {
        val periods=mutableListOf(period)
        var cursor=period.start.minusDays(1)
        repeat(5){val p=data.cycle(cursor);if(!p.start.isBefore(tracking))periods.add(p);cursor=p.start.minusDays(1)}
        return periods.reversed().map{p->val entries=data.entries().filter{!it.date.isAfter(asOf)};AnalyticsCycle(p,Finance.income(entries,p),Finance.expense(entries,p))}
    }
    private val signalCache by lazy {detectSignals()}
    fun signals():List<SpendingSignal> = signalCache
    private fun detectSignals():List<SpendingSignal> {
        val expenses=transactions.filter{it.type==TxType.EXPENSE}
        val duplicates=expenses.groupBy{listOf(it.date,it.accountId,it.categoryId,it.amount)}.values.filter{it.size>1}
        val out=duplicates.take(3).map{group->SpendingSignal("عمليات متشابهة تستحق المراجعة","${group.size} عمليات بنفس اليوم والحساب والصنف والمبلغ. قد تكون مشتريات مستقلة؛ راجعها دون حذف تلقائي.",group)}.toMutableList()
        val spikes=mutableListOf<Pair<Transaction,SpendingSignal>>()
        val currentIds=expenses.map{it.id}.toHashSet()
        data.transactions.filter{it.type==TxType.EXPENSE&&it.dueId==null}.groupBy{it.categoryId}.values.forEach {group->
            val previous=java.util.ArrayDeque<Transaction>()
            group.sortedWith(compareBy<Transaction>{it.date}.thenBy{it.created}.thenBy{it.id}).forEach {tx->
                val before=previous.filter{it.date<tx.date||(it.date==tx.date&&it.created<tx.created)}
                if(tx.id in currentIds&&before.size>=5) {
                    val sorted=before.map{it.amount}.sorted()
                    val median=if(sorted.size%2==1)sorted[sorted.size/2]else Finance.average(sorted[sorted.size/2-1]+sorted[sorted.size/2],2)
                    if(median>0&&tx.amount>=median*3) {
                        spikes+=tx to SpendingSignal("مصروف أكبر من نمطك المعتاد","${data.category(tx.categoryId)}: ${money(tx.amount)} مقابل وسيط ${money(median)} لآخر ${before.size} عمليات سابقة. إشارة للمراجعة، وليست حكمًا أن الصرف غير ضروري.",listOf(tx))
                        spikes.sortByDescending{it.first.amount}
                        if(spikes.size>3)spikes.removeAt(spikes.lastIndex)
                    }
                }
                previous.addLast(tx);if(previous.size>20)previous.removeFirst()
            }
        }
        out+=spikes.map{it.second}
        return out
    }
    companion object {
        fun emptyCategory(id:String)=CategoryAnalysis(id,0,0,0,emptyList())
        fun stats(rows:List<Transaction>)=rows.filter{it.type in listOf(TxType.EXPENSE,TxType.REFUND)&&it.categoryId!=null}.groupBy{it.categoryId!!}.map{(id,tx)->
            CategoryAnalysis(id,tx.filter{it.type==TxType.EXPENSE}.sumOf{it.amount},tx.filter{it.type==TxType.REFUND}.sumOf{it.amount},tx.count{it.type==TxType.EXPENSE},tx.sortedWith(compareByDescending<Transaction>{it.date}.thenByDescending{it.created}))
        }
        fun nextPlan(data:Household,asOf:LocalDate=LocalDate.now()):CyclePlan {
            val current=data.cycle(asOf);val next=data.cycle(current.end)
            val history=mutableListOf<Finance.Period>();var cursor=current.start.minusDays(1)
            repeat(3){val p=data.cycle(cursor);if(!p.start.isBefore(LocalDate.parse(data.prefs.trackingStart))&&data.transactions.any{it.type==TxType.EXPENSE&&p.contains(LocalDate.parse(it.date))})history.add(p);cursor=p.start.minusDays(1)}
            val variable=if(history.isEmpty())null else Finance.average(history.sumOf{Finance.expense(data.entries().filter{it.due==null},it).coerceAtLeast(0)},history.size.toLong())
            val income=if(history.isEmpty())null else Finance.average(history.sumOf{Finance.income(data.entries(),it)},history.size.toLong())
            val dues=data.dues.filter{LocalDate.parse(it.date)<next.end&&data.remaining(it)>0}
            val commitments=dues.filter{next.contains(LocalDate.parse(it.date))}.sumOf{data.remaining(it)}
            val carryover=dues.filter{LocalDate.parse(it.date)<next.start}.sumOf{data.remaining(it)}
            return CyclePlan(next,history,variable,income,commitments,carryover,dues)
        }
    }
}
