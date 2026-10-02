package com.hesabbeitna.app

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun today(): String = LocalDate.now().toString()
fun userError(error: Exception): String {
    val text = error.message.orEmpty()
    return if (text.any { it in '\u0600'..'\u06ff' }) text else "راجع البيانات: التاريخ بصيغة YYYY-MM-DD، والمبلغ لا يزيد على خانتين عشريتين، والحساب والتصنيف صالحان"
}

@Serializable data class Account(val id: String = newId(), val name: String, val kind: String = "نقد",
    val opening: Long = 0, val archived: Boolean = false)
@Serializable data class Category(val id: String = newId(), val name: String,
    val income: Boolean = false, val archived: Boolean = false)
@Serializable enum class TxType { EXPENSE, INCOME, TRANSFER, REFUND }
@Serializable data class Transaction(val id: String = newId(), val type: TxType,
    val amount: Long, val date: String = today(), val accountId: String,
    val destinationId: String? = null, val categoryId: String? = null,
    val payment: String = "نقد", val note: String = "", val originalId: String? = null,
    val dueId: String? = null, val created: Long = System.currentTimeMillis()) {
    fun entry() = Finance.Entry(id, type.name, amount, date, accountId, destinationId, categoryId, dueId)
}
@Serializable data class Budget(val period: String, val amount: Long, val categoryId: String? = null)
@Serializable data class BillRule(val id: String = newId(), val title: String, val amount: Long,
    val categoryId: String, val start: String, val intervalMonths: Int = 1,
    val end: String? = null, val active: Boolean = true)
@Serializable data class Due(val id: String, val ruleId: String, val title: String,
    val categoryId: String, val date: String, val amount: Long)
@Serializable data class SavingGoal(val title: String = "", val target: Long = 0, val accountId: String? = null)
@Serializable data class Preferences(val ready: Boolean = false, val salaryDay: Int = 25,
    val trackingStart: String = today(), val defaultAccount: String? = null,
    val lock: Boolean = false, val goal: SavingGoal = SavingGoal())
@Serializable data class Household(val schema: Int = 1, val accounts: List<Account> = emptyList(),
    val categories: List<Category> = defaults(), val transactions: List<Transaction> = emptyList(),
    val budgets: List<Budget> = emptyList(), val rules: List<BillRule> = emptyList(),
    val dues: List<Due> = emptyList(), val prefs: Preferences = Preferences()) {
    private val cachedEntries by lazy { transactions.map { it.entry() } }
    private val categoryNames by lazy { categories.associate { it.id to it.name } }
    private val accountNames by lazy { accounts.associate { it.id to it.name } }
    private val duePaid by lazy {
        val totals = mutableMapOf<String,Long>()
        transactions.forEach { t -> t.dueId?.let { id ->
            val signed = when(t.type) { TxType.EXPENSE -> t.amount; TxType.REFUND -> -t.amount; else -> 0L }
            totals[id] = (totals[id] ?: 0) + signed
        } }
        totals
    }
    fun entries() = cachedEntries
    fun balance(account: Account) = Finance.balance(account.id, account.opening, entries())
    fun category(id: String?) = categoryNames[id] ?: "—"
    fun account(id: String?) = accountNames[id] ?: "—"
    fun paid(due: Due) = duePaid[due.id] ?: 0L
    fun remaining(due: Due) = (due.amount - paid(due)).coerceAtLeast(0)
    fun cycle(date: LocalDate = LocalDate.now()) = Finance.period(date, prefs.salaryDay)
    fun budget(period: Finance.Period, categoryId: String? = null) =
        budgets.firstOrNull { it.period == period.start.toString() && it.categoryId == categoryId }
    fun validate(): Household {
        require(schema == 1) { "إصدار النسخة الاحتياطية غير مدعوم" }
        require(accounts.size <= 200 && categories.size <= 500 && transactions.size <= 100_000 && rules.size <= 1000 && dues.size <= 50_000 && budgets.size <= 20_000) { "الملف أكبر من الحدود المدعومة" }
        require(prefs.salaryDay in 1..31)
        LocalDate.parse(prefs.trackingStart)
        require(accounts.map { it.id }.distinct().size == accounts.size)
        require(categories.map { it.id }.distinct().size == categories.size)
        require(transactions.map { it.id }.distinct().size == transactions.size) { "معرفات عمليات مكررة" }
        require(rules.map { it.id }.distinct().size == rules.size)
        require(dues.map { it.id }.distinct().size == dues.size)
        require(budgets.map { it.period to it.categoryId }.distinct().size == budgets.size)
        require(accounts.all { it.name.isNotBlank() && it.opening in 0..100_000_000_000L })
        require(categories.all { it.name.isNotBlank() })
        if (prefs.ready) {
            require(accounts.any { !it.archived })
            require(categories.any { !it.archived && !it.income } && categories.any { !it.archived && it.income })
        }
        require(prefs.defaultAccount == null || accounts.any { it.id == prefs.defaultAccount && !it.archived })
        require(prefs.goal.target in 0..100_000_000_000L)
        require(prefs.goal.accountId == null || accounts.any { it.id == prefs.goal.accountId })
        val accountIds = accounts.map { it.id }.toSet()
        val catMap = categories.associateBy { it.id }
        val txMap = transactions.associateBy { it.id }
        val dueMap = dues.associateBy { it.id }
        val ruleIds = rules.map { it.id }.toSet()
        budgets.forEach { b ->
            LocalDate.parse(b.period); require(b.amount in 0..100_000_000_000L)
            require(b.categoryId == null || catMap[b.categoryId]?.income == false)
        }
        rules.forEach { r ->
            LocalDate.parse(r.start)
            require(r.title.isNotBlank() && r.amount in 1..100_000_000_000L && r.intervalMonths in 1..12)
            require(catMap[r.categoryId]?.income == false)
            if (r.end != null) require(!LocalDate.parse(r.end).isBefore(LocalDate.parse(r.start)))
        }
        dues.forEach { d ->
            LocalDate.parse(d.date)
            require(d.amount in 1..100_000_000_000L)
            require(d.ruleId in ruleIds && catMap[d.categoryId]?.income == false)
        }
        transactions.forEach { t ->
            require(t.amount in 1..100_000_000_000L)
            val date = LocalDate.parse(t.date)
            require(!date.isBefore(LocalDate.parse(prefs.trackingStart))) { "التاريخ يسبق بدء المتابعة" }
            require(!date.isAfter(LocalDate.now())) { "العملية الفعلية لا تكون في المستقبل؛ استخدم الالتزامات" }
            require(t.accountId in accountIds)
            if (t.type == TxType.TRANSFER) {
                require(t.destinationId != t.accountId && t.destinationId in accountIds)
                require(t.categoryId == null && t.originalId == null && t.dueId == null)
            } else {
                require(t.destinationId == null)
                require(catMap[t.categoryId]?.income == (t.type == TxType.INCOME))
            }
            if (t.type == TxType.REFUND) {
                val original = txMap[t.originalId]
                require(original != null && original.type == TxType.EXPENSE && original.categoryId == t.categoryId && original.dueId == t.dueId)
                require(!date.isBefore(LocalDate.parse(original.date)))
            } else require(t.originalId == null)
            if (t.dueId != null) require(dueMap[t.dueId]?.categoryId == t.categoryId && t.type in listOf(TxType.EXPENSE, TxType.REFUND))
        }
        val refunded = mutableMapOf<String,Long>()
        transactions.forEach { t -> t.originalId?.let { refunded[it] = (refunded[it] ?: 0) + t.amount } }
        transactions.filter { it.type == TxType.EXPENSE }.forEach { original ->
            require((refunded[original.id] ?: 0) <= original.amount) { "الاستردادات تتجاوز قيمة المصروف الأصلي" }
        }
        dues.forEach { require(paid(it) in 0..it.amount) { "السداد يتجاوز قيمة الالتزام" } }
        return this
    }
    /** Deterministic IDs prevent repeat materialization; original anchor day survives short months. */
    fun materialize(): Household {
        val existing = dues.associateBy { it.id }.toMutableMap()
        val horizon = LocalDate.now().plusMonths(13)
        rules.filter { it.active }.forEach { rule ->
            val first = LocalDate.parse(rule.start)
            val firstMonth = YearMonth.from(first)
            val elapsed = java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(LocalDate.parse(prefs.trackingStart))).coerceAtLeast(0)
            var month = firstMonth.plusMonths(elapsed / rule.intervalMonths * rule.intervalMonths)
            var count = 0
            while (count++ < 2400) {
                val date = month.atDay(minOf(first.dayOfMonth, month.lengthOfMonth()))
                if (date.isAfter(horizon) || (rule.end != null && date.isAfter(LocalDate.parse(rule.end)))) break
                if (!date.isBefore(LocalDate.parse(prefs.trackingStart))) {
                    val id = "${rule.id}:$date"
                    existing.putIfAbsent(id, Due(id, rule.id, rule.title, rule.categoryId, date.toString(), rule.amount))
                }
                month = month.plusMonths(rule.intervalMonths.toLong())
            }
        }
        return copy(dues = existing.values.sortedBy { it.date })
    }
    companion object {
        fun defaults() = listOf("الطعام", "الفواتير", "الإيجار", "التعليم", "الصحة", "المواصلات", "الترفيه", "الأقساط", "رسوم التحويل", "أخرى")
            .mapIndexed { i, name -> Category("expense-$i", name) } +
            listOf(Category("salary", "الراتب", true), Category("other-income", "دخل إضافي", true))
    }
}

fun percent(value: Double?): String = value?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "غير متاح"
fun money(value: Long) = "${Finance.format(value)} ج.م"
fun periodLabel(period: Finance.Period) = "${period.start} إلى ${period.end.minusDays(1)}"

fun recommendations(data: Household, period: Finance.Period): List<String> {
    val output = mutableListOf<String>()
    val spent = Finance.categories(data.entries(), period)
    data.budgets.filter { it.period == period.start.toString() }.forEach { b ->
        val value = if (b.categoryId == null) Finance.expense(data.entries(), period) else spent[b.categoryId] ?: 0L
        if (value > b.amount) output += "${if (b.categoryId == null) "ميزانية البيت" else data.category(b.categoryId)} تجاوزت الميزانية بـ${money(value - b.amount)} (${percent(Finance.percent(value - b.amount, b.amount))}). راجع العمليات قبل زيادة الميزانية."
    }
    val counts = Finance.counts(data.entries(), period)
    counts.maxByOrNull { it.value }?.let { top ->
        val total = spent[top.key] ?: 0
        if (top.value >= 10 && total > 0) output += "${data.category(top.key)}: ${top.value} عملية بصافي ${money(total)}. خفض إجمالي هذا البند 10% يوفر تقريبًا ${money(total / 10)}؛ النسبة هدف مقترح تختاره أنت."
    }
    data.rules.filter { it.active }.sortedByDescending { it.amount }.take(2).forEach { r ->
        val yearly = r.amount * (12 / r.intervalMonths)
        output += "راجع «${r.title}»: ${money(r.amount)} كل ${r.intervalMonths} شهر، بإجمالي تقريبي ${money(yearly)} في سنة كاملة. المراجعة لا تعني أن الالتزام غير ضروري."
    }
    val previous = mutableListOf<Finance.Period>()
    var cursor = period.start.minusDays(1)
    repeat(3) {
        val p = data.cycle(cursor)
        if (!p.start.isBefore(LocalDate.parse(data.prefs.trackingStart)) && data.transactions.any {it.type==TxType.EXPENSE&&p.contains(LocalDate.parse(it.date))}) previous += p
        cursor = p.start.minusDays(1)
    }
    if (previous.isNotEmpty()) {
        val variable = previous.map { p -> Finance.expense(data.entries().filter { it.due == null }, p).coerceAtLeast(0) }.sum() / previous.size
        val next = data.cycle(period.end)
        val commitments = data.dues.filter { next.contains(LocalDate.parse(it.date)) }.sumOf { data.remaining(it) }
        output += "مقترح الدورة التالية: ${money(variable + commitments)} = متوسط الإنفاق غير المتكرر ${money(variable)} في ${previous.size} دورة مكتملة زمنيا + التزامات متبقية ${money(commitments)}. لا يشمل احتياطيًا للطوارئ؛ اكتمال التسجيل مسؤولية المستخدم."
        val surplus = previous.map { Finance.income(data.entries(), it) - Finance.expense(data.entries(), it) }.sum() / previous.size
        if (surplus > 0) output += "متوسط الفائض المسجل ${money(surplus)}؛ هدف ادخار أولي بنصفه ${money(surplus / 2)}، قابل للتعديل حسب الالتزامات."
    } else output += "لا توجد دورة سابقة مكتملة زمنيا وبها مصروفات مسجلة؛ حدد ميزانية مبدئية بنفسك. لا يمكن استنتاج ميزانية موثوقة من سجل ناقص."
    return output
}
