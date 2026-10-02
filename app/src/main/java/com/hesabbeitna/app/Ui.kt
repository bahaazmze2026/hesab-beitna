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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Green = Color(0xFF14675B)
private val Gold = Color(0xFFD6AD55)
@Composable fun HouseTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme=lightColorScheme(primary=Green,secondary=Gold,
            background=Color(0xFFF5F7F3),surface=Color.White,onSurface=Color(0xFF193731),error=Color(0xFFAF3D3D)),content=content)
    }
}
@Composable fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
}
@Composable fun Panel(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            if(title!=null) Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp)
            content()
        }
    }
}
@Composable fun Hint(text: String) { Text(text,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,lineHeight=21.sp) }
@Composable fun AmountLine(label: String, amount: Long, color: Color = Green) {
    Column { Hint(label); Text(money(amount),fontSize=23.sp,fontWeight=FontWeight.Bold,color=color) }
}
@Composable fun Empty(text: String = "لا توجد عمليات مسجلة في هذه الفترة") { Panel { Text(text); Hint("ابدأ ببياناتك الحقيقية؛ التطبيق لا يضيف بيانات تجريبية") } }
@Composable fun Field(value: String, change: (String)->Unit, label: String, numeric: Boolean=false, secret: Boolean=false) {
    OutlinedTextField(value=value,onValueChange=change,label={Text(label)},modifier=Modifier.fillMaxWidth(),
        singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(numeric) KeyboardType.Decimal else KeyboardType.Text),
        visualTransformation=if(secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)
}
@Composable fun Choice(label: String, current: String, options: List<Pair<String,String>>, select: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first==current }?.second ?: "اختر"}",modifier=Modifier.weight(1f)); Text("⌄")
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            options.forEach { option -> DropdownMenuItem(text={Text(option.second)},onClick={select(option.first);expanded=false}) }
        }
    }
}
@Composable fun DialogForm(title: String, dismiss: ()->Unit, content: @Composable ColumnScope.()->Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest=dismiss) {
        Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().heightIn(max=650.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(title,fontSize=21.sp,fontWeight=FontWeight.Bold)
                content()
                TextButton(onClick=dismiss,modifier=Modifier.align(Alignment.End)) { Text("إغلاق") }
            }
        }
    }
}
@Composable fun ErrorText(error: String?) { if(error!=null) Text(error,color=MaterialTheme.colorScheme.error,fontSize=13.sp) }

@Composable fun HouseRoot(model: AppModel, authenticated: Boolean, unlock: ()->Unit,
    canLock: ()->Boolean, export: (String,String,Finance.Period)->Unit, restore: (String)->Unit, notifications: ()->Unit) {
    val data by model.data.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val loadFailure by model.loadFailure.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val preview by model.restorePreview.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); model.clearMessage() } }
    var screen by remember { mutableStateOf("home") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var paying by remember { mutableStateOf<Due?>(null) }
    var deleting by remember { mutableStateOf<Transaction?>(null) }
    var monthOffset by remember { mutableIntStateOf(0) }
    val snapshot = data
    if(snapshot==null) {
        Surface(Modifier.fillMaxSize()) { Page {
            Text("حساب بيتنا",fontSize=28.sp,fontWeight=FontWeight.Bold)
            if(loadFailure==null) { CircularProgressIndicator(); Text("فتح بياناتك المحلية…") }
            else { ErrorText(loadFailure); Button(onClick={model.reload()}) {Text("إعادة المحاولة")}
                OutlinedButton(onClick={dialog="restore"}) {Text("استعادة نسخة احتياطية")}
                if(dialog=="restore") BackupForm(false,{dialog=null}) {password->dialog=null;restore(password)} }
        } }
        if(preview!=null) RestoreConfirmation(preview!!,busy,{model.cancelRestore()},{model.confirmRestore()})
        return
    }
    if(snapshot.prefs.lock&&!authenticated) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(painterResource(R.drawable.logo),"شعار حساب بيتنا",Modifier.size(100.dp))
            Spacer(Modifier.height(24.dp)); Text("بيانات بيتك في أمان",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(16.dp)); Button(onClick=unlock) {Text("فتح بالبصمة أو قفل الهاتف")}
        } }; return
    }
    if(!snapshot.prefs.ready) { SetupScreen(model); return }
    val base = snapshot.cycle()
    val selected = Finance.anchor(java.time.YearMonth.from(base.start).plusMonths(monthOffset.toLong()),snapshot.prefs.salaryDay)
    val period = snapshot.cycle(selected)
    BackHandler(enabled=screen!="home") {screen="home"}
    Scaffold(snackbarHost={SnackbarHost(snack)},
        topBar={TopAppBar(title={Column {Text("حساب بيتنا",fontWeight=FontWeight.Bold); Text("بيتك، وحسابك واضح",fontSize=12.sp)}},
            navigationIcon={androidx.compose.foundation.Image(painterResource(R.drawable.logo),"اللوجو",Modifier.padding(10.dp).size(36.dp))},
            actions={TextButton(onClick={screen="settings"}) {Text("الإعدادات")}})},
        bottomBar={NavigationBar {
            listOf(Triple("home","الرئيسية","⌂"),Triple("transactions","العمليات","≡"),Triple("budget","الميزانية","▤"),Triple("dues","الالتزامات","◷"),Triple("accounts","حساباتي","◫")).forEach { (key,label,symbol)->
                NavigationBarItem(selected=screen==key,onClick={screen=key},icon={Text(symbol,fontSize=24.sp)},label={Text(label,fontSize=11.sp)})
            }
        }},
        floatingActionButton={if(screen!="settings") ExtendedFloatingActionButton(onClick={editing=null;paying=null;dialog="transaction"},containerColor=Green,contentColor=Color.White) {Text("＋ تسجيل عملية")}}
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(screen in listOf("home","transactions","budget","analytics")) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={monthOffset--}) {Text("السابق")}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(periodLabel(period),fontSize=12.sp); if(monthOffset!=0) TextButton(onClick={monthOffset=0}) {Text("الدورة الحالية")}
                }
                TextButton(onClick={monthOffset++},enabled=monthOffset<0) {Text("التالي")}
            }
            when(screen) {
                "home" -> HomeScreen(snapshot,period,{screen="analytics"},{screen="dues"})
                "analytics" -> AnalyticsScreen(snapshot,period)
                "transactions" -> TransactionsScreen(snapshot,period,
                    edit={editing=it;paying=null;dialog=if(it.type==TxType.REFUND)"refund"else"transaction"},delete={deleting=it},refund={editing=it;dialog="refund"})
                "budget" -> BudgetScreen(snapshot,period,model)
                "dues" -> DuesScreen(snapshot,model,{paying=it;editing=null;dialog="transaction"})
                "accounts" -> AccountsScreen(snapshot,model,{dialog="account"})
                "settings" -> SettingsScreen(snapshot,model,canLock,notifications,
                    backup={dialog="backup"},restore={dialog="restore"},csv={export("csv","",period)},pdf={export("pdf","",period)})
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
    var day by remember {mutableStateOf("25")}
    var start by remember {mutableStateOf(today())}
    var name by remember {mutableStateOf("النقد")}
    var opening by remember {mutableStateOf("0")}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by remember {mutableStateOf(false)}
    Surface(Modifier.fillMaxSize()) { Page {
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.Image(painterResource(R.drawable.logo),"شعار حساب بيتنا",Modifier.size(80.dp))
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

@Composable fun HomeScreen(data: Household,period: Finance.Period,analytics: ()->Unit,dues: ()->Unit) {
    val entries=remember(data.transactions){data.entries()}
    val income=Finance.income(entries,period); val expense=Finance.expense(entries,period)
    val budget=data.budget(period)
    val categories=Finance.categories(entries,period)
    val top=categories.maxByOrNull {it.value}
    val count=Finance.counts(entries,period).maxByOrNull {it.value}
    Page {
        Panel("ملخص الدورة") {
            AmountLine("الدخل الفعلي",income)
            AmountLine("صافي المصروفات",expense)
            HorizontalDivider(); AmountLine("المتبقي من دخل الدورة",income-expense,if(income>=expense) Green else MaterialTheme.colorScheme.error)
            Hint("المتبقي هنا لا يساوي رصيد الحسابات؛ الأرصدة الافتتاحية والتحويلات لا تدخل هذه الأرقام.")
        }
        Panel("ميزانية البيت") {
            if(budget==null) Hint("لم تُحدد ميزانية لهذه الدورة") else {
                AmountLine("المتبقي من الميزانية",budget.amount-expense)
                LinearProgressIndicator(progress={if(budget.amount>0)(expense.toFloat()/budget.amount).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                Hint("المحدد ${money(budget.amount)} • المستخدم ${percent(Finance.percent(expense,budget.amount))}")
            }
        }
        Panel("أين تذهب الأموال؟") {
            if(top==null||top.value<=0) Hint("لا توجد مصروفات كافية لترتيب البنود") else Text("الأكبر بالمبلغ: ${data.category(top.key)} — ${money(top.value)} (${percent(Finance.percent(top.value,expense))})")
            count?.let {Text("الأكثر تكرارًا: ${data.category(it.key)} — ${it.value} عملية")}
            Hint("كثرة العمليات تختلف عن كبر المبلغ؛ الاستردادات لا تزيد عدد عمليات المصروف.")
            OutlinedButton(onClick=analytics,modifier=Modifier.fillMaxWidth()) {Text("التحليلات والتوقع والتوصيات")}
        }
        Panel("أرصدة الحسابات الآن") { AmountLine("إجمالي الأرصدة",data.accounts.sumOf {data.balance(it)}); Hint("يشمل الأرصدة السابقة وكل الحركات حتى الآن، وليس الفترة المختارة وحدها") }
        Panel("التزامات قادمة أو متأخرة") {
            val upcoming=data.dues.filter {data.remaining(it)>0&&LocalDate.parse(it.date)<=LocalDate.now().plusDays(7)}.sortedBy {it.date}.take(3)
            if(upcoming.isEmpty()) Hint("لا توجد التزامات غير مدفوعة خلال 7 أيام")
            upcoming.forEach {Text("${it.title} • ${it.date} • المتبقي ${money(data.remaining(it))}")}
            TextButton(onClick=dues) {Text("عرض الالتزامات")}
        }
        if(LocalDate.parse(data.prefs.trackingStart)>period.start) Hint("بدأ التسجيل بعد بداية هذه الدورة؛ الأرقام تغطي السجل المتاح فقط.")
        Spacer(Modifier.height(80.dp))
    }
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
        Text("تحليلات واضحة",fontSize=25.sp,fontWeight=FontWeight.Bold)
        Panel("البنود حسب إجمالي المبلغ") {
            if(ranked.isEmpty()) Hint("لا توجد بيانات")
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
                buckets.forEach { (date,amount)->Text("$date: ${money(amount)}",fontSize=13.sp) }
            }
            Hint("القيم الصفرية تعني عدم وجود مصروف مسجل. الأعمدة الحمراء تعني صافي استردادات سالبًا. الأسابيع تُنسب إلى تاريخ الأحد؛ بداية ونهاية الدورة قد تحتويان أسبوعًا جزئيًا. التجميع الشهري هنا للأشهر الميلادية الواقعة في الدورة المختارة.")
        }
        Panel("خطوات للتخطيط والتوفير") { recommendations(data,period).forEach {Text("• $it",lineHeight=23.sp)} }
        Spacer(Modifier.height(80.dp))
    }
}
@Composable fun BarChart(values: List<Long>) {
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val max=values.maxOfOrNull {kotlin.math.abs(it).toDouble()}?.coerceAtLeast(1.0)?:1.0
        val width=size.width/values.size.coerceAtLeast(1)
        values.forEachIndexed {i,value->
            val height=(kotlin.math.abs(value)/max*size.height).toFloat()
            drawRect(if(value>=0) Green else Color(0xFFAF3D3D),topLeft=androidx.compose.ui.geometry.Offset(size.width-(i+1)*width,size.height-height),size=androidx.compose.ui.geometry.Size(width*0.72f,height))
        }
    }
}
