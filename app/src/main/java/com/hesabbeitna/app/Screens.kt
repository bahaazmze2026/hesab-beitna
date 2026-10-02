@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.hesabbeitna.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

@Composable fun TransactionsScreen(data: Household,period: Finance.Period,edit: (Transaction)->Unit,
    delete: (Transaction)->Unit,refund: (Transaction)->Unit) {
    var search by rememberSaveable {mutableStateOf("")}
    var category by rememberSaveable {mutableStateOf("")}
    var account by rememberSaveable {mutableStateOf("")}
    var type by remember {mutableStateOf("")}
    var from by rememberSaveable(period.start) {mutableStateOf(period.start.toString())}
    var to by rememberSaveable(period.end) {mutableStateOf(period.end.minusDays(1).toString())}
    var filters by rememberSaveable {mutableStateOf(false)}
    var limit by rememberSaveable {mutableIntStateOf(100)}
    val dates=runCatching {Finance.Period(LocalDate.parse(from),LocalDate.parse(to).plusDays(1))}.getOrNull()
    val list=remember(data.transactions,search,category,account,type,from,to) {
        data.transactions.filter { t->
            dates!=null&&dates.contains(LocalDate.parse(t.date)) &&
                (category.isEmpty()||t.categoryId==category) && (account.isEmpty()||t.accountId==account||t.destinationId==account) &&
                (type.isEmpty()||t.type.name==type) &&
                (search.isBlank()||t.note.contains(search,true)||data.category(t.categoryId).contains(search,true)||data.account(t.accountId).contains(search,true))
        }.sortedWith(compareByDescending<Transaction> {it.date}.thenByDescending {it.created})
    }
    Page {
        ScreenTitle("سجل العمليات")
        Field(search,{search=it;limit=100},"ابحث في الملاحظة أو البند أو الحساب")
        TextButton(onClick={filters=!filters}) {Text(if(filters)"إخفاء الفلاتر"else"التصفية بالتاريخ والتصنيف والحساب")}
        if(filters) {
            Field(from,{from=it},"من YYYY-MM-DD");Field(to,{to=it},"حتى YYYY-MM-DD")
            Choice("التصنيف",category,listOf("" to "الكل")+data.categories.map {it.id to it.name}) {category=it}
            Choice("الحساب",account,listOf("" to "الكل")+data.accounts.map {it.id to it.name}) {account=it}
            Choice("نوع العملية",type,listOf("" to "الكل")+TxType.entries.map {it.name to typeLabel(it)}) {type=it}
        }
        if(dates==null||dates.days()<=0) ErrorText("راجع نطاق التاريخ")
        Hint("نطاق البحث: ${displayDate(from)} إلى ${displayDate(to)}")
        Hint("${list.size} عملية تطابق الاختيار")
        if(list.isEmpty()) Empty()
        var previousDate=""
        list.take(limit).forEach {t->
            if(previousDate!=t.date) {Text(displayDate(t.date),fontWeight=FontWeight.Bold);previousDate=t.date}
            Panel {
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text(if(t.type==TxType.TRANSFER)"${data.account(t.accountId)} ← ${data.account(t.destinationId)}"else data.category(t.categoryId),fontWeight=FontWeight.Bold)
                        Hint("${typeLabel(t.type)} • ${data.account(t.accountId)} • ${t.payment}")
                    }
                    Text(money(t.amount),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                }
                if(t.note.isNotBlank()) Text(t.note,fontSize=13.sp)
                if(t.dueId!=null) Hint("مرتبط بسداد التزام")
                androidx.compose.foundation.layout.FlowRow {
                    TextButton(onClick={edit(t)}) {Text("تعديل")}
                    TextButton(onClick={delete(t)}) {Text("حذف")}
                    if(t.type==TxType.EXPENSE && data.transactions.filter {it.originalId==t.id}.sumOf {it.amount}<t.amount) TextButton(onClick={refund(t)}) {Text("استرداد")}
                }
            }
        }
        if(list.size>limit) OutlinedButton(onClick={limit+=100},modifier=Modifier.fillMaxWidth()) {Text("عرض 100 عملية أخرى")}
        Spacer(Modifier.height(80.dp))
    }
}

@Composable fun BudgetScreen(data: Household,period: Finance.Period,model: AppModel) {
    var category by rememberSaveable {mutableStateOf("")}
    var amount by rememberSaveable {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    val existing=data.budget(period,category.ifBlank {null})
    LaunchedEffect(category,period.start,existing?.amount) { amount=existing?.amount?.let {java.math.BigDecimal.valueOf(it,2).toPlainString()}?:"" }
    val spent=Finance.categories(data.entries(),period)
    val budgets=data.budgets.filter {it.period==period.start.toString()}
    Page {
        ScreenTitle("ميزانية الدورة")
        Panel("تحديد الميزانية") {
            Choice("الميزانية",category,listOf("" to "البيت بالكامل")+data.categories.filter {!it.income&&!it.archived}.map {it.id to it.name}) {category=it}
            Field(amount,{amount=it},"المبلغ المحدد — جنيه",true)
            Hint("ميزانيات البنود جزء من ميزانية البيت؛ لا تُجمع مع الميزانية العامة عند احتساب الإنفاق.")
            ErrorText(error)
            Button(onClick={try {
                val b=Budget(period.start.toString(),Finance.money(amount),category.ifBlank {null})
                model.change {it.copy(budgets=it.budgets.filterNot {old->old.period==b.period&&old.categoryId==b.categoryId}+b)}
            }catch(e:Exception){error=userError(e)}},modifier=Modifier.fillMaxWidth()) {Text("حفظ الميزانية")}
        }
        val total=data.budget(period)
        val allocated=budgets.filter {it.categoryId!=null}.sumOf {it.amount}
        if(total!=null) {
            if(allocated>total.amount) ErrorText("مجموع ميزانيات البنود ${money(allocated)} يتجاوز ميزانية البيت ${money(total.amount)}")
            else Hint("ميزانية غير موزعة على البنود: ${money(total.amount-allocated)}")
        }
        if(budgets.isEmpty()) Empty("لم تُحدد ميزانيات لهذه الدورة")
        budgets.forEach {b->
            val value=if(b.categoryId==null)Finance.expense(data.entries(),period)else spent[b.categoryId]?:0L
            Panel(if(b.categoryId==null)"البيت بالكامل"else data.category(b.categoryId)) {
                Text("المحدد ${money(b.amount)} • المصروف ${money(value)}")
                LinearProgressIndicator(progress={if(b.amount>0)(value.toFloat()/b.amount).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth())
                if(value>b.amount) ErrorText("تجاوز ${money(value-b.amount)} (${percent(Finance.percent(value-b.amount,b.amount))})")
                else Hint("المتبقي ${money(b.amount-value)} • المستخدم ${percent(Finance.percent(value,b.amount))}")
                TextButton(onClick={model.change("أزيلت الميزانية") {it.copy(budgets=it.budgets.filterNot {old->old==b})}}) {Text("إزالة الميزانية")}
            }
        }
        if(budgets.isNotEmpty()) OutlinedButton(onClick={
            val next=data.cycle(period.end)
            model.change("نُسخت الميزانيات الناقصة للدورة التالية") { current->
                val additions=budgets.filter {b->current.budget(next,b.categoryId)==null}.map {it.copy(period=next.start.toString())}
                current.copy(budgets=current.budgets+additions)
            }
        },modifier=Modifier.fillMaxWidth()) {Text("نسخ للدورة التالية دون استبدال ميزانية موجودة")}
        Spacer(Modifier.height(80.dp))
    }
}

@Composable fun DuesScreen(data: Household,model: AppModel,pay: (Due)->Unit) {
    var adding by remember {mutableStateOf(false)}
    var editing by remember {mutableStateOf<BillRule?>(null)}
    var planned by remember {mutableStateOf<Due?>(null)}
    var showPaid by rememberSaveable {mutableStateOf(false)}
    var limit by rememberSaveable {mutableIntStateOf(100)}
    val cutoff=LocalDate.now().plusDays(60)
    val dues=data.dues.filter {(showPaid||data.remaining(it)>0)&&!LocalDate.parse(it.date).isAfter(cutoff)}.sortedBy {it.date}
    Page {
        ScreenTitle("الفواتير والأقساط")
        Button(onClick={adding=true},modifier=Modifier.fillMaxWidth()) {Text("＋ التزام متكرر")}
        Hint("القائمة تشمل المتأخرات والاستحقاقات خلال 60 يومًا. المبالغ المخططة لا تدخل الأرصدة أو المصروفات قبل السداد.")
        Row {Checkbox(checked=showPaid,onCheckedChange={showPaid=it});Text("إظهار المدفوع أيضًا",modifier=Modifier.padding(top=12.dp))}
        if(dues.isEmpty()) Empty("لا توجد استحقاقات مطابقة")
        dues.take(limit).forEach {d->
            val paid=data.paid(d);val remaining=data.remaining(d)
            val state=when {remaining==0L->"مدفوع";LocalDate.parse(d.date)<LocalDate.now()->"متأخر";paid>0->"مدفوع جزئيًا";else->"مستحق"}
            Panel(d.title) {
                Hint("${displayDate(d.date)} • ${data.category(d.categoryId)} • $state")
                Text("مخطط ${money(d.amount)} • مدفوع فعليًا ${money(paid)}")
                if(remaining>0) {AmountLine("المتبقي",remaining);Button(onClick={pay(d)}) {Text("تسجيل سداد")}}
                TextButton(onClick={planned=d}) {Text("تعديل مبلغ هذا الاستحقاق")}
            }
        }
        if(dues.size>limit)OutlinedButton(onClick={limit+=100}) {Text("عرض 100 استحقاق آخر")}
        Panel("إدارة التكرار") {
            if(data.rules.isEmpty()) Hint("أضف فاتورة أو قسطًا من الزر أعلاه")
            data.rules.forEach {rule->
                Text("${rule.title} • ${if(rule.active)"نشط"else"متوقف"} • ${money(rule.amount)}",fontWeight=FontWeight.Bold)
                Row {
                    TextButton(onClick={editing=rule}) {Text("تعديل")}
                    TextButton(onClick={model.change(if(rule.active)"أوقف التكرار المستقبلي"else"فُعل التكرار") {old->
                        old.copy(rules=old.rules.map {if(it.id==rule.id)it.copy(active=!rule.active)else it},
                            dues=if(rule.active)old.dues.filterNot {d->d.ruleId==rule.id&&LocalDate.parse(d.date)>LocalDate.now()&&old.transactions.none {it.dueId==d.id}}else old.dues)
                    }}) {Text(if(rule.active)"إيقاف"else"تفعيل")}
                }
            }
            Hint("الإيقاف يحذف الاستحقاقات المستقبلية غير المدفوعة لهذا التكرار؛ السجلات السابقة والسداد تبقى.")
        }
        Spacer(Modifier.height(80.dp))
    }
    if(adding) BillForm(data,model,{adding=false})
    editing?.let {BillForm(data,model,{editing=null},it)}
    planned?.let {DuePlanForm(data,model,it,{planned=null})}
}

@Composable fun AccountsScreen(data: Household,model: AppModel,add: ()->Unit) {
    var editing by remember {mutableStateOf<Account?>(null)}
    var goal by remember {mutableStateOf(false)}
    Page {
        ScreenTitle("حساباتي ومحافظي")
        AmountLine("إجمالي الأرصدة الحالية",data.accounts.sumOf {data.balance(it)})
        Button(onClick=add,modifier=Modifier.fillMaxWidth()) {Text("＋ حساب أو محفظة")}
        data.accounts.forEach {account->Panel(account.name) {
            Hint("${account.kind}${if(account.archived)" • مؤرشف"else""}")
            AmountLine("الرصيد الحالي",data.balance(account))
            Hint("افتتاحي ${money(account.opening)} في ${data.prefs.trackingStart}")
            Row {
                TextButton(onClick={editing=account}) {Text("تعديل")}
                if(!account.archived) TextButton(onClick={model.change {it.copy(prefs=it.prefs.copy(defaultAccount=account.id))}}) {Text(if(data.prefs.defaultAccount==account.id)"افتراضي ✓"else"اجعله افتراضيًا")}
            }
            TextButton(onClick={
                val active=data.accounts.filter {!it.archived&&it.id!=account.id}
                if(!account.archived&&active.isEmpty())model.message("احتفظ بحساب نشط واحد على الأقل")
                else model.change {it.copy(accounts=it.accounts.map {a->if(a.id==account.id)a.copy(archived=!a.archived)else a},
                    prefs=if(!account.archived&&it.prefs.defaultAccount==account.id)it.prefs.copy(defaultAccount=active.first().id)else it.prefs)}
            }) {Text(if(account.archived)"إلغاء الأرشفة"else"أرشفة الحساب")}
        }}
        Panel("هدف الادخار") {
            val current=data.prefs.goal
            if(current.target>0&&current.accountId!=null) {
                val account=data.accounts.firstOrNull {it.id==current.accountId}
                val amount=account?.let {data.balance(it)}?:0
                Text(current.title.ifBlank {"هدفي"},fontWeight=FontWeight.Bold)
                Text("${money(amount)} من ${money(current.target)} (${percent(Finance.percent(amount,current.target))})")
                Hint("التقدم هو الرصيد الحالي للحساب المحدد؛ اختر حساب ادخار مخصصًا. التحويل إليه ليس مصروفًا.")
            }else Hint("حدد هدفًا واربطه بحساب ادخار مخصص")
            OutlinedButton(onClick={goal=true}) {Text("تحديد أو تعديل الهدف")}
        }
        Hint("يسمح برصيد سالب عند تسجيل صرف أكبر من الرصيد؛ يظهر بوضوح كي تراجع السجلات. لا تُنشأ مداخيل وهمية لتسويته.")
        Spacer(Modifier.height(80.dp))
    }
    editing?.let {AccountForm(data,model,{editing=null},it)}
    if(goal) GoalForm(data,model,{goal=false})
}

@Composable fun GoalForm(data:Household,model:AppModel,dismiss:()->Unit) {
    var title by rememberSaveable {mutableStateOf(data.prefs.goal.title)}
    var amount by rememberSaveable {mutableStateOf(java.math.BigDecimal.valueOf(data.prefs.goal.target,2).toPlainString())}
    var account by rememberSaveable {mutableStateOf(data.prefs.goal.accountId?:data.accounts.first().id)}
    var error by remember {mutableStateOf<String?>(null)}
    DialogForm("هدف الادخار",dismiss) {
        Field(title,{title=it.take(80)},"اسم الهدف")
        Field(amount,{amount=it},"المبلغ المستهدف — 0 لإلغاء الهدف",true)
        Choice("حساب الادخار المخصص",account,data.accounts.map {it.id to it.name}) {account=it}
        Hint("افصل حساب الادخار عن الحساب المستخدم للمصروف اليومي كي يكون التقدم معبرًا عن الهدف.")
        ErrorText(error)
        Button(onClick={try {val target=Finance.money(amount);model.change(onResult={if(it)dismiss()}) {it.copy(prefs=it.prefs.copy(goal=SavingGoal(title,target,account)))}}catch(e:Exception){error=userError(e)}}) {Text("حفظ الهدف")}
    }
}

@Composable fun SettingsScreen(data:Household,model:AppModel,canLock:()->Boolean,notifications:()->Unit,
    accounts:()->Unit,budget:()->Unit,dues:()->Unit,backup:()->Unit,restore:()->Unit,csv:()->Unit,pdf:()->Unit) {
    var category by rememberSaveable {mutableStateOf(false)}
    var cycle by remember {mutableStateOf(false)}
    var exportKind by remember {mutableStateOf<String?>(null)}
    var undo by remember {mutableStateOf(false)}
    Page {
        ScreenTitle("الإعدادات والخصوصية")
        Panel("مظهر التطبيق") {
            val appearance=LocalAppearance.current
            Hint("اختيارك مستقل عن وضع الهاتف، ويُحفظ تلقائيًا")
            ThemeMode.entries.forEach { mode->
                Surface(onClick={appearance.chooseMode(mode)},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)
                    .testTag("theme-${mode.name.lowercase()}").semantics{selected=appearance.mode==mode;role=Role.RadioButton},shape=Brand.Input,
                    color=if(appearance.mode==mode)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(16.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text(mode.title,modifier=Modifier.weight(1f));if(appearance.mode==mode)ToolIcon("check")
                    }
                }
            }
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text("المظهر الزجاجي");Hint("زجاج مطفي وإضاءة ناعمة في أدوات التنقل والاختيار")}
                Switch(checked=appearance.glass,onCheckedChange={appearance.chooseGlass(it)},modifier=Modifier.testTag("glass-toggle"))
            }
        }
        Panel("إدارة البيت") {
            QuickLink("الحسابات وهدف الادخار","الأرصدة والمحافظ", "wallet",accounts)
            QuickLink("الميزانية","ميزانية البيت والبنود", "budget",budget)
            QuickLink("الفواتير والأقساط","التكرار والسداد", "calendar",dues)
        }
        Panel("بيتك") {
            Text("بداية الدورة: يوم ${data.prefs.salaryDay} • بدء المتابعة: ${data.prefs.trackingStart}")
            Hint("العربية • الجنيه المصري • أرقام 123 • شخص واحد")
            OutlinedButton(onClick={cycle=true},modifier=Modifier.fillMaxWidth()) {Text("تغيير يوم بداية دورة الراتب")}
            OutlinedButton(onClick={category=true},modifier=Modifier.fillMaxWidth()) {Text("إدارة التصنيفات")}
        }
        Panel("قفل التطبيق") {
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked=data.prefs.lock,onCheckedChange={enabled->
                    if(enabled&&!canLock())model.message("فعّل قفل الشاشة أو البصمة في إعدادات الهاتف أولًا")
                    else model.change {it.copy(prefs=it.prefs.copy(lock=enabled))}
                });Column(Modifier.weight(1f).padding(8.dp)) {Text("البصمة أو قفل الهاتف");Hint("يعاد القفل عند مغادرة التطبيق")}
            }
            Hint("البيانات المحلية مشفرة. لقطات الشاشة ومعاينة المهام محجوبة لحماية تفاصيل الحساب.")
        }
        Panel("النسخ الاحتياطي والاستعادة") {
            Button(onClick=backup,modifier=Modifier.fillMaxWidth()) {Text("حفظ نسخة مشفرة")}
            OutlinedButton(onClick=restore,modifier=Modifier.fillMaxWidth()) {Text("استعادة نسخة مشفرة")}
            TextButton(onClick={undo=true}) {Text("الرجوع إلى نسخة الأمان قبل آخر استعادة")}
            Hint("احتفظ بنسخة خارج الهاتف. حذف التطبيق يحذف بياناته ومفتاح تشفيرها المحلي؛ النسخة بكلمة مرور يمكن فتحها على هاتف آخر. لا توجد مزامنة سحابية تلقائية.")
        }
        Panel("تصدير الدورة المختارة") {
            OutlinedButton(onClick={exportKind="csv"},modifier=Modifier.fillMaxWidth()) {Text("عمليات CSV")}
            OutlinedButton(onClick={exportKind="pdf"},modifier=Modifier.fillMaxWidth()) {Text("تقرير PDF")}
            Hint("التصدير يستخدم الدورة التي اخترتها من الرئيسية. ملف التقرير ليس بديلًا عن النسخة الاحتياطية.")
        }
        Panel("التنبيهات") {Button(onClick=notifications) {Text("تفعيل إذن تذكير الفواتير")};Hint("تذكير يومي تقريبي؛ قد تؤخره إدارة بطارية الهاتف. لا يعرض تفاصيل مالية على شاشة القفل.")}
        Panel("عن حساب بيتنا") {Text("الإصدار 1.3.0 • هوية القطة والمحفظة");Hint("تطبيق محلي دون إذن الإنترنت. التوصيات حسابية ومفسرة، والتوقعات منفصلة عن النتائج الفعلية.")}
        Spacer(Modifier.height(32.dp))
    }
    if(category) CategoriesForm(data,model,{category=false})
    if(cycle) CycleForm(data,model,{cycle=false})
    if(undo)AlertDialog(onDismissRequest={undo=false},title={Text("الرجوع للبيانات السابقة؟")},
        text={Text("ستُستبدل البيانات الحالية بنسخة الأمان قبل آخر استعادة. إذا سجلت عمليات جديدة، احفظ نسخة احتياطية أولًا.")},
        confirmButton={TextButton(onClick={undo=false;model.undoRestore()}) {Text("الرجوع")}},dismissButton={TextButton(onClick={undo=false}) {Text("إلغاء")}})
    exportKind?.let {kind->AlertDialog(onDismissRequest={exportKind=null},title={Text("تصدير بيانات مالية")},
        text={Text("الملف غير مشفر ويحتوي تفاصيل مالية. احفظه في مكان تثق به.")},
        confirmButton={TextButton(onClick={exportKind=null;if(kind=="csv")csv()else pdf()}) {Text("اختيار مكان الحفظ")}},dismissButton={TextButton(onClick={exportKind=null}) {Text("إلغاء")}})}
}

@Composable fun CategoriesForm(data:Household,model:AppModel,dismiss:()->Unit) {
    var name by rememberSaveable {mutableStateOf("")}
    var type by remember {mutableStateOf("expense")}
    var editing by remember {mutableStateOf<String?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    DialogForm("التصنيفات",dismiss) {
        Field(name,{name=it.take(50)},"اسم التصنيف")
        if(editing==null)Choice("نوع التصنيف",type,listOf("expense" to "مصروف","income" to "دخل")) {type=it}
        ErrorText(error)
        Button(onClick={try {
            require(name.isNotBlank()) {"أدخل الاسم"}
            require(data.categories.none {it.id!=editing&&it.name==name.trim()&&it.income==(type=="income")}) {"التصنيف موجود"}
            val id=editing?:newId();val existing=data.categories.firstOrNull {it.id==id}
            val cat=Category(id,name.trim(),existing?.income?: (type=="income"),existing?.archived?:false)
            model.change {it.copy(categories=it.categories.filterNot {old->old.id==id}+cat)}
            editing=null;name=""
        }catch(e:Exception){error=userError(e)}}) {Text(if(editing==null)"إضافة"else"حفظ الاسم")}
        if(editing!=null)TextButton(onClick={editing=null;name=""}) {Text("إلغاء تعديل الاسم")}
        data.categories.forEach {cat->
            Text("${cat.name} • ${if(cat.income)"دخل"else"مصروف"}${if(cat.archived)" • مؤرشف"else""}")
            Row {
                TextButton(onClick={editing=cat.id;name=cat.name;type=if(cat.income)"income"else"expense"}) {Text("تعديل")}
                TextButton(onClick={
                    val active=data.categories.count {!it.archived&&it.income==cat.income}
                    if(!cat.archived&&active<=1)model.message("احتفظ بتصنيف نشط واحد على الأقل لكل نوع")
                    else model.change {it.copy(categories=it.categories.map {c->if(c.id==cat.id)c.copy(archived=!c.archived)else c})}
                }) {Text(if(cat.archived)"إلغاء الأرشفة"else"أرشفة")}
            }
        }
        Hint("الأرشفة تُخفي التصنيف من الإدخال الجديد وتُبقي تاريخه وتحليلاته سليمة.")
    }
}

@Composable fun CycleForm(data:Household,model:AppModel,dismiss:()->Unit) {
    var day by rememberSaveable {mutableStateOf(data.prefs.salaryDay.toString())}
    var error by remember {mutableStateOf<String?>(null)}
    DialogForm("بداية دورة الراتب",dismiss) {
        Field(day,{day=it},"يوم الراتب 1–31",true)
        Hint("ستُعاد تجميع التحليلات حسب اليوم الجديد. الميزانيات السابقة تبقى مربوطة بتواريخ بدايتها القديمة؛ حدد ميزانية للدورات الجديدة. العمليات وتواريخها لا تتغير.")
        ErrorText(error)
        Button(onClick={try {val value=day.toInt();require(value in 1..31) {"اليوم من 1 إلى 31"};model.change(onResult={if(it)dismiss()}) {it.copy(prefs=it.prefs.copy(salaryDay=value))}}catch(e:Exception){error=userError(e)}}) {Text("حفظ يوم الراتب")}
    }
}
