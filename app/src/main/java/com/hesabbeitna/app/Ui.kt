@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hesabbeitna.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable fun HouseRoot(model: AppModel, authenticated: Boolean, unlock: ()->Unit,
    canLock: ()->Boolean, export: (String,String,Finance.Period)->Unit, restore: (String)->Unit, notifications: ()->Unit) {
    val data by model.data.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val loadFailure by model.loadFailure.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val preview by model.restorePreview.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); model.clearMessage() } }
    var screen by rememberSaveable { mutableStateOf("home") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var payingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Transaction?>(null) }
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val snapshot = data
    val editing=snapshot?.transactions?.firstOrNull{it.id==editingId}
    val paying=snapshot?.dues?.firstOrNull{it.id==payingId}
    if(snapshot==null) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=MaterialTheme.colorScheme.background) { Page {
            Mascot(96.dp)
            Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineLarge)
            if(loadFailure==null) { CircularProgressIndicator(); Text("فتح بياناتك المحلية…") }
            else { ErrorText(loadFailure); Button(onClick={model.reload()}) {Text("إعادة المحاولة")}
                OutlinedButton(onClick={dialog="restore"}) {Text("استعادة نسخة احتياطية")}
                if(dialog=="restore") BackupForm(false,{dialog=null}) {password->dialog=null;restore(password)} }
        } }
        if(preview!=null) RestoreConfirmation(preview!!,busy,{model.cancelRestore()},{model.confirmRestore()})
        return
    }
    if(snapshot.prefs.lock&&!authenticated) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=MaterialTheme.colorScheme.background) { Column(Modifier.padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(painterResource(R.drawable.brand_cat),"شعار حساب بيتنا",Modifier.size(100.dp))
            Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp)); Text("بيانات بيتك في أمان",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(16.dp)); Button(onClick=unlock) {Text("فتح بالبصمة أو قفل الهاتف")}
        } }; return
    }
    if(!snapshot.prefs.ready) { SetupScreen(model); return }
    val base = snapshot.cycle()
    val selected = Finance.anchor(java.time.YearMonth.from(base.start).plusMonths(monthOffset.toLong()),snapshot.prefs.salaryDay)
    val period = snapshot.cycle(selected)
    BackHandler(enabled=screen!="home") {screen="home"}
    fun addExpense() { editingId=null;payingId=null;dialog="transaction" }
    Scaffold(snackbarHost={SnackbarHost(snack)},containerColor=MaterialTheme.colorScheme.background,
        topBar={BrandHeader(if(screen in listOf("budget","dues","accounts")) {{screen="home"}} else null)},
        bottomBar={GlassNavigation(screen){screen=it}},
        floatingActionButton={if(screen!="settings"&&screen!="home") ExtendedFloatingActionButton(onClick={addExpense()},
            modifier=Modifier.testTag("expense-fab"),containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,
            shape=Brand.Card,icon={ToolIcon("plus",MaterialTheme.colorScheme.onPrimary)},text={Text("إضافة مصروف")})}
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(screen in listOf("home","transactions","budget","analytics")) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={monthOffset--}) {Text("السابق")}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(periodLabel(period),fontSize=12.sp); if(monthOffset!=0) TextButton(onClick={monthOffset=0}) {Text("الدورة الحالية")}
                }
                TextButton(onClick={monthOffset++},enabled=monthOffset<0) {Text("التالي")}
            }
            when(screen) {
                "home" -> HomeScreen(snapshot,period,{screen="analytics"},{screen="dues"},{screen="budget"},{screen="accounts"},{addExpense()})
                "analytics" -> AnalyticsScreen(snapshot,period)
                "transactions" -> TransactionsScreen(snapshot,period,
                    edit={editingId=it.id;payingId=null;dialog=if(it.type==TxType.REFUND)"refund"else"transaction"},delete={deleting=it},refund={editingId=it.id;dialog="refund"})
                "budget" -> BudgetScreen(snapshot,period,model)
                "dues" -> DuesScreen(snapshot,model,{payingId=it.id;editingId=null;dialog="transaction"})
                "accounts" -> AccountsScreen(snapshot,model,{dialog="account"})
                "settings" -> SettingsScreen(snapshot,model,canLock,notifications,
                    accounts={screen="accounts"},budget={screen="budget"},dues={screen="dues"},backup={dialog="backup"},restore={dialog="restore"},csv={export("csv","",period)},pdf={export("pdf","",period)})
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    when(dialog) {
        "transaction" -> TransactionForm(snapshot,model,editing,paying,{dialog=null})
        "refund" -> editing?.let { RefundForm(snapshot,model,it,{dialog=null}) }
        "account" -> AccountForm(snapshot,model,{dialog=null})
        "backup" -> BackupForm(true,{dialog=null}) {password->dialog=null;export("backup",password,period)}
        "restore" -> BackupForm(false,{dialog=null}) {password->dialog=null;restore(password)}
    }
    deleting?.let { tx -> AlertDialog(onDismissRequest={deleting=null},title={Text("حذف العملية؟")},text={Text("${typeLabel(tx.type)} بقيمة ${money(tx.amount)}. ستُحدث الأرصدة والتحليلات.")},
        confirmButton={TextButton(onClick={model.deleteTransaction(tx.id);deleting=null},enabled=!busy) {Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}) {Text("إلغاء")}}) }
    preview?.let { RestoreConfirmation(it,busy,{model.cancelRestore()},{model.confirmRestore()}) }
}

@Composable fun RestoreConfirmation(preview: Household,busy: Boolean,dismiss: ()->Unit,confirm: ()->Unit) {
    AlertDialog(onDismissRequest=dismiss,title={Text("استعادة النسخة؟")},
        text={Text("تم فحص الملف: ${preview.transactions.size} عملية و${preview.accounts.size} حساب. ستُستبدل البيانات الحالية بالكامل دون دمج، مع حفظ نسخة أمان محلية للرجوع إليها.")},
        confirmButton={TextButton(onClick=confirm,enabled=!busy) {Text("استبدال واستعادة")}},dismissButton={TextButton(onClick=dismiss) {Text("إلغاء")}})
}

@Composable fun SetupScreen(model: AppModel) {
    var day by rememberSaveable {mutableStateOf("25")}
    var start by rememberSaveable {mutableStateOf(today())}
    var name by rememberSaveable {mutableStateOf("النقد")}
    var opening by rememberSaveable {mutableStateOf("0")}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by remember {mutableStateOf(false)}
    Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=MaterialTheme.colorScheme.background) { Page {
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.Image(painterResource(R.drawable.brand_cat),"شعار حساب بيتنا",Modifier.size(120.dp))
        Text("أهلًا في حساب بيتنا",fontSize=28.sp,fontWeight=FontWeight.Bold)
        Hint("ابدأ بتحديد دورة الراتب وأول حساب. بياناتك على الهاتف، دون إنترنت.")
        Field(day,{day=it},"يوم نزول الراتب (1–31)",true)
        Field(start,{start=it},"تاريخ بدء المتابعة YYYY-MM-DD")
        Field(name,{name=it},"اسم الحساب الأول")
        Field(opening,{opening=it},"رصيده في تاريخ بدء المتابعة — جنيه",true)
        Hint("الرصيد الافتتاحي ليس دخلًا. الأرقام تعرض بصيغة 123. إذا لم يوجد يوم الراتب في شهر نستخدم آخر يوم فيه.")
        ErrorText(error)
        Button(onClick={try {
            val salary=day.toInt(); require(salary in 1..31) {"يوم الراتب من 1 إلى 31"}
            val date=LocalDate.parse(start); require(!date.isAfter(LocalDate.now())) {"ابدأ من اليوم أو تاريخ سابق"}
            require(!date.isBefore(LocalDate.now().minusYears(20))) {"تاريخ المتابعة بعيد جدًا"}
            require(name.isNotBlank()) {"أدخل اسم الحساب"}
            val account=Account(name=name.trim(),opening=Finance.money(opening))
            submitted=true
            model.change("تم إعداد بيتك",onResult={if(!it){submitted=false;error="تعذر الحفظ. أعد المحاولة"}}) { it.copy(accounts=listOf(account),prefs=Preferences(ready=true,salaryDay=salary,trackingStart=start,defaultAccount=account.id)) }
        }catch(e:Exception){error=userError(e)}},enabled=!submitted,modifier=Modifier.fillMaxWidth()) {Text("ابدأ حساب بيتنا")}
    } }
}

@Composable fun HomeScreen(data:Household,period:Finance.Period,analytics:()->Unit,dues:()->Unit,budgetOpen:()->Unit,accounts:()->Unit,add:()->Unit) {
    val entries=remember(data.transactions){data.entries()}
    val income=Finance.income(entries,period);val expense=Finance.expense(entries,period)
    val budget=data.budget(period)
    val top=Finance.categories(entries,period).maxByOrNull{it.value}
    Page {
        ScreenTitle("كل شيء أوضح", "نظرة هادئة على أموال بيتك")
        Surface(shape=Brand.Card,color=MaterialTheme.colorScheme.surface,
            border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("المتبقي من دخل الدورة",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(income-expense),style=MaterialTheme.typography.displaySmall,color=if(income>=expense)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                HorizontalDivider(color=MaterialTheme.colorScheme.primary.copy(alpha=.2f))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val largeText=LocalDensity.current.fontScale>1.3f
                    if(maxWidth<320.dp||largeText) Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        CycleMetric("الدخل الفعلي",income);CycleMetric("صافي المصروفات",expense)
                    } else Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)){CycleMetric("الدخل الفعلي",income)}
                        Column(Modifier.weight(1f)){CycleMetric("صافي المصروفات",expense)}
                    }
                }
                Hint("هذا المتبقي يخص الدورة فقط، ولا يساوي أرصدة الحسابات.")
            }
        }
        PrimaryAction("إضافة مصروف",add,modifier=Modifier.testTag("expense-fab"))
        Panel("ميزانية البيت") {
            if(budget==null)Hint("حدد ميزانية لتعرف المتاح للصرف") else {
                AmountLine("المتبقي من الميزانية",budget.amount-expense,if(budget.amount>=expense)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                LinearProgressIndicator(progress={if(budget.amount>0)(expense.toFloat()/budget.amount).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth())
                Hint("المحدد ${money(budget.amount)} • المستخدم ${percent(Finance.percent(expense,budget.amount))}")
            }
            TextButton(onClick=budgetOpen){Text("إدارة الميزانية")}
        }
        Panel("أين تذهب الأموال؟") {
            if(top==null||top.value<=0)Hint("أضف أول مصروف ليظهر توزيع الإنفاق") else {
                Text(data.category(top.key),style=MaterialTheme.typography.titleMedium)
                AmountLine("أكبر بند في هذه الفترة",top.value)
                Hint("يمثل ${percent(Finance.percent(top.value,expense))} من صافي المصروفات")
            }
            TextButton(onClick=analytics){Text("عرض التحليلات والتوصيات")}
        }
        QuickLink("حساباتي ومحافظي", "إجمالي الأرصدة ${money(data.accounts.sumOf{data.balance(it)})}","wallet",accounts)
        QuickLink("الفواتير والأقساط", "راجع السداد والاستحقاقات القادمة", "calendar",dues)
        Panel("خلال الأيام السبعة القادمة") {
            val upcoming=data.dues.filter{data.remaining(it)>0&&LocalDate.parse(it.date)<=LocalDate.now().plusDays(7)}.sortedBy{it.date}.take(3)
            if(upcoming.isEmpty())Hint("لا توجد التزامات غير مدفوعة خلال 7 أيام")
            upcoming.forEach{Text(it.title,style=MaterialTheme.typography.titleMedium);Hint("${displayDate(it.date)} • المتبقي ${money(data.remaining(it))}")}
            TextButton(onClick=dues){Text("عرض الالتزامات")}
        }
        if(data.transactions.isEmpty())Empty("أول خطوة لتنظيم حسابات البيت")
        if(LocalDate.parse(data.prefs.trackingStart)>period.start)Hint("بدأ التسجيل بعد بداية هذه الدورة؛ الأرقام تغطي السجل المتاح فقط.")
        Spacer(Modifier.height(80.dp))
    }
}

@Composable private fun CycleMetric(label:String,amount:Long) {
    Hint(label)
    Text(money(amount),style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface)
}

@Composable fun AnalyticsScreen(data: Household,period: Finance.Period) {
    val entries=remember(data.transactions){data.entries()}
    val expense=Finance.expense(entries,period)
    val ranked=Finance.categories(entries,period).entries.sortedByDescending {it.value}
    val counts=Finance.counts(entries,period).entries.sortedByDescending {it.value}
    val now=Finance.min(LocalDate.now(),period.end.minusDays(1))
    val windows=Finance.comparison(period,now,data.prefs.salaryDay)
    val comparable=!windows[1].start.isBefore(LocalDate.parse(data.prefs.trackingStart))
    val current=Finance.expense(entries,windows[0]); val previous=Finance.expense(entries,windows[1])
    val coveredStart=Finance.max(period.start,LocalDate.parse(data.prefs.trackingStart))
    val coveredEnd=Finance.min(period.end,LocalDate.now().plusDays(1))
    val days=ChronoUnit.DAYS.between(coveredStart,coveredEnd).coerceAtLeast(0)
    val outstanding=data.dues.filter {LocalDate.parse(it.date)<period.end}.sumOf {data.remaining(it)}
    val forecast=Finance.forecast(entries,period,LocalDate.parse(data.prefs.trackingStart),LocalDate.now(),outstanding)
    var unit by remember {mutableStateOf("day")}
    Page {
        ScreenTitle("تحليلات واضحة", "الفترة: ${periodLabel(period)}")
        AmountLine("صافي المصروفات في الفترة",expense)
        Panel("البنود حسب إجمالي المبلغ") {
            if(ranked.isEmpty()) Empty("لم تُسجّل مصروفات لهذه الفترة")
            val max=ranked.maxOfOrNull {it.value.coerceAtLeast(0)}?:1L
            ranked.forEach { (id,amount)->
                Text("${data.category(id)} — ${money(amount)} — ${percent(Finance.percent(amount,expense))}")
                LinearProgressIndicator(progress={if(max>0)(amount.toFloat()/max).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth())
            }
            if(expense<=0) Hint("النسب غير متاحة لأن إجمالي صافي المصروفات صفري أو سالب")
        }
        Panel("البنود حسب تكرار المصروف") {
            if(counts.isEmpty()) Hint("لا توجد عمليات مصروف")
            counts.forEach { (id,n)->
                val gross=data.transactions.filter {it.type==TxType.EXPENSE&&it.categoryId==id&&period.contains(LocalDate.parse(it.date))}.sumOf {it.amount}
                Text("${data.category(id)}: $n عملية • متوسط العملية ${money(gross/n)}")
            }
            Hint("العدد للمصروفات فقط. المتوسط يستخدم المدفوعات قبل طرح الاستردادات؛ ترتيب المبالغ أعلاه يستخدم صافي المصروفات.")
        }
        Panel("مقارنة بفترة متساوية") {
            Hint("الحالية: ${periodLabel(windows[0])}\nالسابقة: ${periodLabel(windows[1])}")
            if(comparable) {
                Text("الحالية ${money(current)} • السابقة ${money(previous)}")
                AmountLine("الفرق",current-previous)
                Text("التغير: ${percent(Finance.change(current,previous))}")
                if(previous<=0) Hint("لا تُحسب نسبة تغير عند صافي سابق صفري أو سالب")
            }else Hint("الفترة السابقة تسبق بدء المتابعة؛ لا توجد مقارنة مكتملة")
            Hint("تساوي الأيام لا يثبت اكتمال إدخال العمليات في الفترتين.")
        }
        Panel("الوتيرة اليومية والتوقع") {
            if(days>0) AmountLine("متوسط الإنفاق في $days يومًا تقويميًا",Finance.average(expense,days)) else Hint("لا توجد أيام مغطاة بالتسجيل")
            if(forecast==null) Hint("التوقع متاح للدورة الحالية بعد 7 أيام من التسجيل على الأقل") else {
                AmountLine("توقع تقريبي لنهاية الدورة",forecast)
                Hint("الفعلي + متوسط الإنفاق غير المتكرر × الأيام المتبقية + الالتزامات المتبقية ${money(outstanding)}. نفترض سداد المتأخرات هذا الشهر. المصروفات المرتبطة بالفواتير لا تدخل المتوسط مرة أخرى. هذا توقع محدود الثقة وليس مبلغًا فعليًا.")
            }
            Hint("أيام بلا عملية تدخل المتوسط؛ تعني عدم وجود صرف مسجل، ولا تثبت عدم الصرف.")
        }
        Panel("اتجاه الإنفاق") {
            Choice("التجميع",unit,listOf("day" to "يومي","week" to "أسبوعي — يبدأ الأحد","month" to "شهري")){unit=it}
            val raw=Finance.buckets(entries,period,unit)
            val buckets=raw.toSortedMap()
            var cursor=coveredStart
            while(cursor<coveredEnd) {
                val key=when(unit) {"month"->java.time.YearMonth.from(cursor).toString();"week"->cursor.minusDays((cursor.dayOfWeek.value%7).toLong()).toString();else->cursor.toString()}
                buckets.putIfAbsent(key,0L);cursor=cursor.plusDays(1)
            }
            if(raw.isEmpty()) Hint("لا توجد عمليات مسجلة للرسم") else {
                BarChart(buckets.values.toList())
                buckets.forEach { (date,amount)->Text("${displayDate(date)}: ${money(amount)}",fontSize=13.sp) }
            }
            Hint("القيم الصفرية تعني عدم وجود مصروف مسجل. الأعمدة الحمراء تعني صافي استردادات سالبًا. الأسابيع تُنسب إلى تاريخ الأحد؛ بداية ونهاية الدورة قد تحتويان أسبوعًا جزئيًا. التجميع الشهري هنا للأشهر الميلادية الواقعة في الدورة المختارة.")
        }
        Panel("خطوات للتخطيط والتوفير") { recommendations(data,period).forEach {Text("• $it",lineHeight=23.sp)} }
        Spacer(Modifier.height(80.dp))
    }
}
@Composable fun BarChart(values: List<Long>) {
    val chartColor=MaterialTheme.colorScheme.primary
    val errorColor=MaterialTheme.colorScheme.error
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val max=values.maxOfOrNull {kotlin.math.abs(it).toDouble()}?.coerceAtLeast(1.0)?:1.0
        val width=size.width/values.size.coerceAtLeast(1)
        values.forEachIndexed {i,value->
            val height=(kotlin.math.abs(value)/max*size.height).toFloat()
            drawRoundRect(if(value>=0) chartColor else errorColor,topLeft=androidx.compose.ui.geometry.Offset(size.width-(i+1)*width,size.height-height),size=androidx.compose.ui.geometry.Size(width*0.72f,height),cornerRadius=androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
        }
    }
}
