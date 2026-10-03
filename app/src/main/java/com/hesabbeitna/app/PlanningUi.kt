package com.hesabbeitna.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

private fun planAmount(value: Long) = BigDecimal.valueOf(value, 2).toPlainString()

@Composable fun PlanningScreen(data: Household, period: Finance.Period, model: AppModel,
    pay: (Due) -> Unit, openDues: () -> Unit, openTemplates: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("month") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val states = rememberSaveableStateHolder()
    val saved = data.plan(period)
    Column(Modifier.fillMaxSize()) {
        GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("month" to "خطة الشهر", "calendar" to "المواعيد", "progress" to "المتابعة").forEach { (key, title) ->
                    FilterChip(selected = tab == key, onClick = { tab = key }, label = { Text(title) }, modifier = Modifier.testTag("plan-$key"))
                }
            }
        }
        states.SaveableStateProvider(tab) {
            when (tab) {
                "calendar" -> FinancialCalendar(data, period, pay, openDues)
                "progress" -> Page {
                    ScreenTitle("المخطط مقابل الفعلي", periodLabel(period))
                    if (saved == null) {
                        MeowMessage("لنبدأ بخطة", "احفظ خطة هذه الدورة لتتابع تقدمك.")
                        PrimaryAction("إنشاء خطة الشهر", { editing = true })
                    } else {
                        val actual = remember(data, period) { planProgress(data, period) }
                        PlanComparison("الدخل", saved.income, actual.income, true)
                        PlanComparison("المصروفات اليومية", saved.variable, actual.variable)
                        PlanComparison("سداد الالتزامات", saved.commitments, actual.commitments)
                        PlanComparison("إجمالي المصروفات", saved.spending, actual.spending)
                        Panel("هدف الادخار") {
                            AmountLine("الهدف المخطط", saved.saving)
                            AmountLine("الفائض الفعلي المسجل", actual.surplus)
                            MeowMessage(if (saved.saving > 0 && actual.surplus >= saved.saving) "الفائض بلغ الهدف" else "خطوة بخطوة",
                                "الفائض هو الدخل ناقص صافي الصرف؛ تأكد من اكتمال التسجيل. ليس إثباتًا لتحويل المال لحساب ادخار.")
                        }
                        if (LocalDate.parse(data.prefs.trackingStart) > period.start) Hint("بدأ التسجيل أثناء الدورة؛ المقارنة تغطي العمليات المتاحة فقط.")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                else -> Page {
                    ScreenTitle("خطة الشهر", "خطة محفوظة لدورة الراتب، تُراجع مع تغيّر احتياجاتك")
                    MeowMessage("Meow معاك في التخطيط", "وزّع الدخل بين مصاريف البيت والالتزامات والادخار واحتياطي الطوارئ.")
                    if (saved == null) {
                        PrimaryAction("إنشاء خطة الشهر", { editing = true }, modifier = Modifier.testTag("create-month-plan"))
                        Hint("الخطة لا تسجّل دخلًا أو مصروفًا ولا تغيّر أرصدة الحسابات.")
                    } else {
                        Panel("خطتك المحفوظة") {
                            AmountLine("الدخل المتوقع", saved.income)
                            Hint("موعد الدخل المخطط ${displayDate(saved.incomeDate)}")
                            MoneyDetail("المصروفات اليومية", saved.variable)
                            MoneyDetail("الالتزامات المحجوزة", saved.commitments)
                            MoneyDetail("هدف الادخار", saved.saving)
                            MoneyDetail("الاحتياطي", saved.reserve)
                            MoneyDetail("غير موزع", saved.income - saved.allocated)
                            if (saved.income < saved.allocated) ErrorText("الخطة تحتاج تمويلًا إضافيًا ${money(saved.allocated - saved.income)}. راجع التوزيع أو الرصيد المتاح.")
                            if (saved.note.isNotBlank()) Hint(saved.note)
                            TextButton(onClick = { editing = true }, modifier = Modifier.testTag("edit-month-plan")) { Text("تعديل الخطة") }
                        }
                        val known = commitmentPlan(data, period)
                        if (known != saved.commitments) Hint("الالتزامات المعروفة الآن ${money(known)}؛ الحجز المحفوظ لا يتغيّر تلقائيًا. راجع الخطة عند إضافة أو تعديل فاتورة.")
                        PrimaryAction("متابعة التنفيذ", { tab = "progress" })
                    }
                    QuickLink("تقويم الفواتير والدخل", "اختَر يومًا لمراجعة مواعيده وتسجيل السداد", "calendar") { tab = "calendar" }
                    QuickLink("القوالب السريعة", "مصروفات ودخل متكرر، بمراجعة قبل الحفظ", "star", openTemplates)
                    QuickLink("إدارة الالتزامات", "إضافة وتعديل الفواتير والأقساط", "bill", openDues)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
    if (editing) MonthlyPlanForm(data, period, model) { editing = false }
}

@Composable private fun PlanComparison(title: String, planned: Long, actual: Long, income: Boolean = false) {
    Panel(title) {
        AmountLine("الفعلي المسجل", actual)
        Hint("المخطط ${money(planned)} • ${if (income) "الفارق عن المتوقع" else "المتبقي من الحجز"} ${money(if (income) actual - planned else planned - actual)}")
        if (!income && actual > planned) ErrorText("تجاوز المخطط ${money(actual - planned)}")
        LinearProgressIndicator(progress = { if (planned > 0) (actual.toFloat() / planned).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun MonthlyPlanForm(data: Household, period: Finance.Period, model: AppModel, dismiss: () -> Unit) {
    val existing = data.plan(period)
    val known = remember(data, period) { commitmentPlan(data, period) }
    var income by rememberSaveable { mutableStateOf(planAmount(existing?.income ?: Finance.income(data.entries(), period))) }
    var variable by rememberSaveable { mutableStateOf(planAmount(existing?.variable ?: ((data.budget(period)?.amount ?: 0) - known).coerceAtLeast(0))) }
    var commitments by rememberSaveable { mutableStateOf(planAmount(existing?.commitments ?: known)) }
    var saving by rememberSaveable { mutableStateOf(planAmount(existing?.saving ?: 0)) }
    var reserve by rememberSaveable { mutableStateOf(planAmount(existing?.reserve ?: 0)) }
    var date by rememberSaveable { mutableStateOf(existing?.incomeDate ?: period.start.toString()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<MonthlyPlan?>(null) }
    var submitted by remember { mutableStateOf(false) }
    DialogForm(if (existing == null) "إنشاء خطة الشهر" else "تعديل خطة الشهر", dismiss) {
        Hint(periodLabel(period))
        Field(income, { income = it }, "الدخل المتوقع — جنيه", true)
        Field(date, { date = it }, "موعد الدخل YYYY-MM-DD")
        Field(variable, { variable = it }, "المصروفات اليومية — جنيه", true)
        Field(commitments, { commitments = it }, "حجز الالتزامات — جنيه", true)
        Hint("المعروف ${money(known)}: استحقاقات الدورة كاملة + المتبقي من المتأخرات. الحجز مبلغ في الخطة، وليس عملية دفع.")
        TextButton(onClick = { commitments = planAmount(known) }) { Text("استخدام الالتزامات المعروفة") }
        Field(saving, { saving = it }, "هدف الادخار — جنيه", true)
        Field(reserve, { reserve = it }, "احتياطي الطوارئ — جنيه", true)
        Field(note, { note = it.take(500) }, "ملاحظة للخطة")
        ErrorText(error)
        PrimaryAction("مراجعة الخطة", {
            try {
                val incomeDay = LocalDate.parse(date)
                require(period.contains(incomeDay)) { "موعد الدخل يجب أن يكون داخل الدورة المختارة" }
                val plan = MonthlyPlan(period.start.toString(), Finance.money(income), Finance.money(variable), Finance.money(commitments), Finance.money(saving), Finance.money(reserve), date, note.trim())
                data.savePlan(plan)
                review = plan; error = null
            } catch (e: Exception) { error = userError(e) }
        }, enabled = !submitted, modifier = Modifier.testTag("review-month-plan"))
    }
    review?.let { plan ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text("اعتماد الخطة المحفوظة؟") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الدخل ${money(plan.income)}\nالصرف المخطط ${money(plan.spending)}\nالادخار ${money(plan.saving)} • الاحتياطي ${money(plan.reserve)}")
                if (plan.allocated > plan.income) Text("هناك احتياج لتمويل إضافي ${money(plan.allocated - plan.income)}. يمكنك حفظه كخطة تحتاج مراجعة.", color = MaterialTheme.colorScheme.error)
                Text("ستُحفظ الخطة مع النسخ الاحتياطية. الأرصدة والعمليات والميزانيات الحالية لا تتغير.")
            } }, confirmButton = { TextButton(onClick = {
                submitted = true
                model.change("Meow • تم حفظ خطة الشهر", onResult = { success -> submitted = false; if (success) { review = null; dismiss() } }) { it.savePlan(plan) }
            }, enabled = !submitted, modifier = Modifier.testTag("confirm-month-plan")) { Text("حفظ الخطة") } },
            dismissButton = { TextButton(onClick = { review = null }, enabled = !submitted) { Text("رجوع للمراجعة") } })
    }
}

@Composable private fun FinancialCalendar(data: Household, period: Finance.Period, pay: (Due) -> Unit, openDues: () -> Unit) {
    var offset by rememberSaveable(period.start.toString()) { mutableIntStateOf(0) }
    var selected by rememberSaveable(period.start.toString()) { mutableStateOf(period.start.toString()) }
    val month = YearMonth.from(period.start).plusMonths(offset.toLong())
    val events = remember(data, month) { calendarEvents(data, month) }
    var limit by rememberSaveable(selected) { mutableIntStateOf(20) }
    Page {
        ScreenTitle("التقويم المالي", "تقويم ميلادي؛ تفاصيل اليوم تظهر عند الضغط عليه")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { offset--; selected = month.minusMonths(1).atDay(1).toString() }) { Text("السابق") }
            Text(month.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { offset++; selected = month.plusMonths(1).atDay(1).toString() }) { Text("التالي") }
        }
        Panel {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val gridWidth=maxOf(maxWidth,336.dp)
                val narrowGrid=maxWidth<336.dp
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Column(Modifier.width(gridWidth),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) { listOf("س", "ح", "ن", "ث", "ر", "خ", "ج").forEach { Text(it, modifier = Modifier.weight(1f)) } }
            val blank = month.atDay(1).dayOfWeek.value % 7 + 1 // Saturday first.
            val padding = blank % 7
            val cells = List(padding) { 0 } + (1..month.lengthOfMonth()).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    for (cell in week) {
                        if (cell == 0) Spacer(Modifier.weight(1f)) else {
                            val date = month.atDay(cell).toString()
                            val count = events.count { it.date == date }
                            Surface(onClick = { selected = date }, modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("calendar-$date")
                                .semantics { contentDescription = "${displayDate(date)}، $count مواعيد" }, shape = Brand.Input,
                                color = if (selected == date) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                                Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(cell.toString(), color = if (date == today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    Text(if (count > 0) "•" else " ", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
                        }
                    }
                    if(narrowGrid)Hint("اسحب الأسبوع أفقيًا لعرض بقية الأيام")
                }
            }
        }
        Hint("• يوجد موعد • الدخل المتوقع مخطط فقط • المدفوع يظهر للمراجعة")
        TextButton(onClick = { offset = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(period.start), YearMonth.now()).toInt(); selected = today() }) { Text("اليوم") }
        Panel("مواعيد ${displayDate(selected)}") {
            val dayEvents = events.filter { it.date == selected }
            if (dayEvents.isEmpty()) MeowMessage("يوم هادئ", "لا توجد مواعيد مسجلة لهذا اليوم.")
            dayEvents.take(limit).forEach { event ->
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Hint("${event.state} • ${money(event.amount)}")
                if (event.kind == "due" && event.amount > 0) {
                    val due = data.dues.first { it.id == event.id }
                    TextButton(onClick = { pay(due) }, modifier = Modifier.testTag("calendar-pay-${event.id}")) { Text("مراجعة وتسجيل السداد") }
                } else if (event.kind == "income") Hint("سجّل الدخل من زر الإضافة عند استلامه؛ هذا الموعد لا يزيد الرصيد تلقائيًا.")
                HorizontalDivider()
            }
            if (dayEvents.size > limit) TextButton(onClick = { limit += 20 }) { Text("عرض المزيد") }
        }
        QuickLink("إضافة موعد فاتورة أو قسط", "إدارة الالتزامات المتكررة", "bill", openDues)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable fun TemplatesScreen(data: Household, model: AppModel, use: (QuickTemplate) -> Unit) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<QuickTemplate?>(null) }
    Page {
        ScreenTitle("القوالب السريعة", "اختصار جاهز يفتح نموذج المراجعة؛ الحفظ بإيدك")
        MeowMessage("Meow يوفّر عليك الخطوات", "جهّز مثلًا مواصلات يومية أو مشتريات معتادة، وعدّل المبلغ عند كل استخدام.")
        PrimaryAction("إضافة قالب", { adding = true }, enabled = data.templates.size < 100, modifier = Modifier.testTag("add-template"))
        if (data.templates.isEmpty()) Hint("لا توجد قوالب محفوظة بعد. يمكنك حفظ العملية كقالب من نموذج الإضافة أيضًا.")
        data.templates.forEach { template ->
            Panel(template.title) {
                Hint("${typeLabel(template.type)} • ${money(template.amount)} • ${data.category(template.categoryId)} • ${data.account(template.accountId)}")
                val available = data.templateAvailable(template)
                if (!available) Hint("الحساب أو الصنف مؤرشف؛ عدّل القالب قبل استخدامه.")
                PrimaryAction("استخدام القالب", { use(template) }, enabled = available, modifier = Modifier.testTag("use-template-${template.id}"))
                Row { TextButton(onClick = { editingId = template.id }) { Text("تعديل") }; TextButton(onClick = { deleting = template }) { Text("حذف") } }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (adding || editingId != null) TemplateForm(data, model, data.templates.firstOrNull { it.id == editingId }) { adding = false; editingId = null }
    deleting?.let { template -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف قالب ${template.title}؟") }, text = { Text("العمليات السابقة تبقى كما هي.") },
        confirmButton = { TextButton(onClick = { model.change("تم حذف القالب") { it.copy(templates = it.templates.filterNot { t -> t.id == template.id }) }; deleting = null }) { Text("حذف القالب") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("إلغاء") } }) }
}

@Composable private fun TemplateForm(data: Household, model: AppModel, editing: QuickTemplate?, dismiss: () -> Unit) {
    val id = rememberSaveable { editing?.id ?: newId() }
    var title by rememberSaveable { mutableStateOf(editing?.title ?: "") }
    var amount by rememberSaveable { mutableStateOf(editing?.amount?.let { planAmount(it) } ?: "") }
    var type by rememberSaveable { mutableStateOf(editing?.type ?: TxType.EXPENSE) }
    var account by rememberSaveable { mutableStateOf(editing?.accountId ?: data.prefs.defaultAccount ?: data.accounts.first { !it.archived }.id) }
    var category by rememberSaveable { mutableStateOf(editing?.categoryId ?: data.categories.first { !it.income && !it.archived }.id) }
    var note by rememberSaveable { mutableStateOf(editing?.note ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    DialogForm("قالب سريع", dismiss) {
        Field(title, { title = it.take(60) }, "اسم القالب")
        Field(amount, { amount = it }, "مبلغ القالب — جنيه", true)
        ActionGrid(listOf(HubAction("template-expense", "مصروف", "cart"), HubAction("template-income", "دخل", "wallet")), "template-${type.name.lowercase()}") {
            type = if (it == "template-income") TxType.INCOME else TxType.EXPENSE
            category = data.categories.first { c -> c.income == (type == TxType.INCOME) && !c.archived }.id
        }
        CategoryChoice(data.categories.filter { it.income == (type == TxType.INCOME) && (!it.archived || it.id == category) }, category, data.transactions, model) { category = it }
        Choice("الحساب", account, data.accounts.filter { !it.archived || it.id == account }.map { it.id to it.name }) { account = it }
        Field(note, { note = it.take(500) }, "ملاحظة القالب")
        Hint("القالب لا ينفّذ أي عملية تلقائيًا ولا يغيّر الأرصدة.")
        ErrorText(error)
        PrimaryAction("حفظ القالب", {
            try {
                val template = QuickTemplate(id, title.trim(), type, Finance.money(amount), account, category, note.trim())
                data.copy(templates = data.templates.filterNot { it.id == id } + template).validate()
                submitted = true
                model.change("Meow • تم حفظ القالب", onResult = { submitted = false; if (it) dismiss() }) { old -> old.copy(schema = 2, templates = old.templates.filterNot { it.id == id } + template) }
            } catch (e: Exception) { error = userError(e) }
        }, enabled = !submitted, modifier = Modifier.testTag("save-template"))
    }
}
