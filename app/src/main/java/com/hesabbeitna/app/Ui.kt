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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    LaunchedEffect(message) { message?.let { if(it.isNotBlank())snack.showSnackbar(it); model.clearMessage() } }
    var screen by rememberSaveable { mutableStateOf("home") }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var payingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Transaction?>(null) }
    var undoing by remember { mutableStateOf<Transaction?>(null) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    var initialType by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    var searchHit by remember { mutableStateOf<SearchHit?>(null) }
    var searchOrigin by rememberSaveable { mutableStateOf("home") }
    val screenState=rememberSaveableStateHolder()
    LaunchedEffect(undoing){undoing?.let{tx->
        if(snack.showSnackbar("تم حذف العملية",actionLabel="تراجع",duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed)model.restoreTransaction(tx)
        undoing=null
    }}
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(screen) { if(screen!="plan"&&monthOffset>0)monthOffset=0 }
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
            androidx.compose.foundation.Image(painterResource(R.drawable.brand_cat),"شعار Meow Budget",Modifier.size(100.dp))
            Spacer(Modifier.height(24.dp)); Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(12.dp)); Text("بيانات بيتك في أمان",fontSize=25.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(16.dp)); Button(onClick=unlock) {Text("فتح بالبصمة أو قفل الهاتف")}
        } }; return
    }
    if(!snapshot.prefs.ready) { SetupScreen(model); return }
    val base = snapshot.cycle()
    val selected = Finance.anchor(java.time.YearMonth.from(base.start).plusMonths(monthOffset.toLong()),snapshot.prefs.salaryDay)
    val period = snapshot.cycle(selected)
    BackHandler(enabled=screen!="home") {screen=if(screen=="search")searchOrigin else "home"}
    fun addTransaction(type:TxType) { templateId=null;editingId=null;payingId=null;initialType=type;dialog="transaction" }
    fun addExpense() {addTransaction(TxType.EXPENSE)}
    fun openSearch(){if(screen!="search")searchOrigin=screen;screen="search"}
    fun editTransaction(tx:Transaction){templateId=null;searchHit=null;editingId=tx.id;payingId=null;dialog=if(tx.type==TxType.REFUND)"refund"else"transaction"}
    fun payDue(due:Due){templateId=null;searchHit=null;payingId=due.id;editingId=null;initialType=TxType.EXPENSE;dialog="transaction"}
    Scaffold(snackbarHost={SnackbarHost(snack)},containerColor=MaterialTheme.colorScheme.background,
        topBar={BrandHeader(if(screen in listOf("budget","dues","accounts","settings","search","templates")) {{screen=if(screen=="search")searchOrigin else "more"}} else null,search={openSearch()})},
        bottomBar={GlassNavigation(screen){screen=it}},
        floatingActionButton={FloatingActionButton(onClick={quickAdd=true},modifier=Modifier.testTag("quick-add").semantics{contentDescription="إضافة عملية"},
            containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,shape=Brand.Input){ToolIcon("plus",MaterialTheme.colorScheme.onPrimary);}}
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding)) {
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(screen in listOf("home","transactions","budget","analytics","plan")) Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={monthOffset--}) {Text("السابق")}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(periodLabel(period),fontSize=12.sp); if(monthOffset!=0) TextButton(onClick={monthOffset=0}) {Text("الدورة الحالية")}
                }
                TextButton(onClick={monthOffset++},enabled=monthOffset<0||(screen=="plan"&&monthOffset<12)) {Text("التالي")}
            }
            screenState.SaveableStateProvider(screen){when(screen) {
                "home" -> HomeScreen(snapshot,period,{screen="analytics"},{screen="dues"},{screen="budget"},{screen="accounts"},{addExpense()},incomeAdd={addTransaction(TxType.INCOME)},transferAdd={addTransaction(TxType.TRANSFER)})
                "analytics" -> AnalyticsScreen(snapshot,period,model)
                "plan" -> PlanningScreen(snapshot,period,model,{payDue(it)},{screen="dues"},{screen="templates"})
                "templates" -> TemplatesScreen(snapshot,model){template->templateId=template.id;editingId=null;payingId=null;initialType=template.type;dialog="transaction"}
                "more" -> MoreScreen{key->when(key){
                    "more-categories"->dialog="categories";"more-backup"->dialog="backup";"more-restore"->dialog="restore"
                    "more-export"->dialog="export";else->screen=key.removePrefix("more-")
                }}
                "search" -> SearchScreen(snapshot,{hit->if(hit.kind=="action"){
                    if(hit.id in listOf("categories","backup","restore","export"))dialog=hit.id else screen=hit.id
                }else searchHit=hit},{screen=it})
                "transactions" -> TransactionsScreen(snapshot,period,
                    edit={editTransaction(it)},delete={deleting=it},refund={editingId=it.id;dialog="refund"})
                "budget" -> BudgetScreen(snapshot,period,model)
                "dues" -> DuesScreen(snapshot,model,{payDue(it)})
                "accounts" -> AccountsScreen(snapshot,model,{dialog="account"})
                "settings" -> SettingsScreen(snapshot,model,canLock,notifications,
                    accounts={screen="accounts"},budget={screen="budget"},dues={screen="dues"},backup={dialog="backup"},restore={dialog="restore"},csv={export("csv","",period)},pdf={export("pdf","",period)})
            }}
            Spacer(Modifier.height(4.dp))
        }
    }
    when(dialog) {
        "transaction" -> TransactionForm(snapshot,model,editing,paying,{dialog=null;templateId=null},initialType,snapshot.templates.firstOrNull{it.id==templateId})
        "categories" -> CategoriesForm(snapshot,model,{dialog=null})
        "export" -> AlertDialog(onDismissRequest={dialog=null},title={Text("تصدير الدورة المختارة")},
            text={Text("${periodLabel(period)}\nالملفات غير مشفرة وتحتوي بيانات مالية. اختَر مكان حفظ تثق به.")},
            confirmButton={Row{TextButton(onClick={dialog=null;export("csv","",period)}){Text("CSV")};TextButton(onClick={dialog=null;export("pdf","",period)}){Text("PDF")}}},
            dismissButton={TextButton(onClick={dialog=null}){Text("إلغاء")}})
        "refund" -> editing?.let { RefundForm(snapshot,model,it,{dialog=null}) }
        "account" -> AccountForm(snapshot,model,{dialog=null})
        "backup" -> BackupForm(true,{dialog=null}) {password->dialog=null;export("backup",password,period)}
        "restore" -> BackupForm(false,{dialog=null}) {password->dialog=null;restore(password)}
    }
    deleting?.let { tx -> AlertDialog(onDismissRequest={deleting=null},title={Text("حذف العملية؟")},text={Text("${typeLabel(tx.type)} بقيمة ${money(tx.amount)}. ستُحدث الأرصدة والتحليلات.")},
        confirmButton={TextButton(onClick={model.deleteTransaction(tx.id,""){success->if(success)undoing=tx};deleting=null},enabled=!busy) {Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}) {Text("إلغاء")}}) }
    if(quickAdd)QuickAddSheet({quickAdd=false}){key->quickAdd=false;when(key){"add-income"->addTransaction(TxType.INCOME);"add-transfer"->addTransaction(TxType.TRANSFER);"add-payment"->screen="dues";"add-template"->screen="templates";else->addExpense()}}
    searchHit?.let{SearchDetail(snapshot,it,{searchHit=null},{editTransaction(it)},{payDue(it)})}
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
        androidx.compose.foundation.Image(painterResource(R.drawable.brand_cat),"شعار Meow Budget",Modifier.size(120.dp))
        Text("أهلًا في Meow Budget",fontSize=28.sp,fontWeight=FontWeight.Bold)
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
        }catch(e:Exception){error=userError(e)}},enabled=!submitted,modifier=Modifier.fillMaxWidth()) {Text("ابدأ Meow Budget")}
    } }
}

@Composable fun HomeScreen(data:Household,period:Finance.Period,analytics:()->Unit,dues:()->Unit,budgetOpen:()->Unit,accounts:()->Unit,add:()->Unit,incomeAdd:()->Unit=add,transferAdd:()->Unit=add) {
    val entries=remember(data.transactions){data.entries()}
    val income=Finance.income(entries,period);val expense=Finance.expense(entries,period)
    val budget=data.budget(period)
    val top=Finance.categories(entries,period).maxByOrNull{it.value}
    val committed=data.dues.filter{LocalDate.parse(it.date)<period.end}.sumOf{data.remaining(it)}
    val available=budget?.let{it.amount-expense-committed}
    Page {
        ScreenTitle("كل شيء أوضح", "نظرة هادئة على أموال بيتك")
        if(data.transactions.isNotEmpty()) MeowMessage("Meow • جاهز ليوم جديد", "راجع المتاح ومواعيدك قبل إضافة المصروف التالي.")
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(if(available!=null)"المتاح من الميزانية بعد الالتزامات"else"أرصدة حساباتك الحالية",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                val headline=available?:data.accounts.sumOf{data.balance(it)}
                Text(money(headline),style=MaterialTheme.typography.displaySmall,color=if(headline>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Hint(if(available!=null)"ميزانية الدورة − الصرف الصافي − الالتزامات غير المدفوعة حتى نهاية الدورة"else"هذا رصيد الحسابات، وليس مبلغًا حرًا للصرف. حدّد ميزانية لمعرفة المتاح بعد الالتزامات")
                TextButton(onClick=if(available!=null)dues else budgetOpen){Text(if(available!=null)"التزامات محجوزة ${money(committed)}"else"تحديد ميزانية البيت")}
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
                Hint("الفائض المسجل ${money(income-expense)} يخص الدورة، ويختلف عن أرصدة الحسابات.")
            }
        }
        PrimaryAction("إضافة مصروف",add,modifier=Modifier.testTag("expense-fab"))
        ActionGrid(listOf(HubAction("home-income","إضافة دخل","wallet"),HubAction("home-transfer","تحويل","transfer"),
            HubAction("home-pay","سداد فاتورة","bill"),HubAction("home-accounts","الحسابات","wallet"))){key->when(key){"home-income"->incomeAdd();"home-transfer"->transferAdd();"home-pay"->dues();else->accounts()}}
        Panel("ميزانية البيت") {
            if(budget==null)Hint("حدد ميزانية لتعرف المتاح للصرف") else {
                AmountLine("المتبقي من الميزانية",budget.amount-expense,if(budget.amount>=expense)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                LinearProgressIndicator(progress={if(budget.amount>0)(expense.toFloat()/budget.amount).coerceIn(0f,1f)else 0f},modifier=Modifier.fillMaxWidth())
                Hint("المحدد ${money(budget.amount)} • المستخدم ${percent(Finance.percent(expense,budget.amount))}")
            }
            TextButton(onClick=budgetOpen,modifier=Modifier.testTag("open-budget")){Text("إدارة الميزانية")}
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
