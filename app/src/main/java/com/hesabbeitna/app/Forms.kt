@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hesabbeitna.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate

private fun amountText(value: Long) = BigDecimal.valueOf(value,2).toPlainString()

@Composable fun TransactionForm(data: Household, model: AppModel, editing: Transaction?, due: Due?, dismiss: ()->Unit,initialType:TxType=TxType.EXPENSE,template:QuickTemplate?=null) {
    val id=rememberSaveable {editing?.id?:newId()}
    val feeId=rememberSaveable {newId()}
    var type by rememberSaveable {mutableStateOf(editing?.type?:initialType)}
    var amount by rememberSaveable {mutableStateOf(editing?.amount?.let {amountText(it)}?:template?.amount?.let{amountText(it)}?:due?.let {amountText(data.remaining(it))}?:"")}
    var date by rememberSaveable {mutableStateOf(editing?.date?:today())}
    val recent=data.transactions.filter{it.type==initialType&&data.accounts.any{a->a.id==it.accountId&&!a.archived}}.maxByOrNull{it.created}
    var account by rememberSaveable {mutableStateOf(editing?.accountId?:template?.accountId?:recent?.accountId?:data.prefs.defaultAccount?:data.accounts.first {!it.archived}.id)}
    var destination by rememberSaveable {mutableStateOf(editing?.destinationId?:data.accounts.firstOrNull {it.id!=account&&!it.archived}?.id?:"")}
    var category by rememberSaveable {mutableStateOf(editing?.categoryId?:template?.categoryId?:due?.categoryId?:recent?.categoryId?.takeIf{id->data.categories.any{it.id==id&&!it.archived&&it.income==(initialType==TxType.INCOME)}}?:data.categories.firstOrNull { it.income==(initialType==TxType.INCOME)&&!it.archived }?.id?:"")}
    var payment by rememberSaveable {mutableStateOf(editing?.payment?:data.accounts.firstOrNull {it.id==account}?.kind?:"نقد")}
    var note by rememberSaveable {mutableStateOf(editing?.note?:template?.note?:due?.title?:"")}
    var fee by rememberSaveable {mutableStateOf("0")}
    val templateKey=rememberSaveable{newId()}
    var saveAsTemplate by rememberSaveable{mutableStateOf(false)}
    var templateTitle by rememberSaveable{mutableStateOf(template?.title?:"")}
    var details by rememberSaveable {mutableStateOf(editing!=null||due!=null)}
    var error by remember {mutableStateOf<String?>(null)}
    var amountError by remember {mutableStateOf<String?>(null)}
    var dateError by remember {mutableStateOf<String?>(null)}
    var duplicate by remember {mutableStateOf(false)}
    var pending by remember {mutableStateOf<Transaction?>(null)}
    var pendingFee by remember {mutableLongStateOf(0)}
    var submitted by remember {mutableStateOf(false)}
    fun submit(t:Transaction,feeAmount:Long) {
        submitted=true
        model.saveTransaction(t,feeAmount,feeId,template=if(saveAsTemplate&&t.type in listOf(TxType.EXPENSE,TxType.INCOME))QuickTemplate(templateKey,templateTitle.trim(),t.type,t.amount,t.accountId,requireNotNull(t.categoryId),t.note)else null) {success->if(success)dismiss()else submitted=false}
    }
    DialogForm(if(editing!=null) "تعديل العملية" else if(due!=null) "سداد ${due.title}" else "تسجيل عملية",dismiss,liquid=true) {
        LiquidAmountField(amount,{amount=it;amountError=null},amountError)
        if(due==null && editing==null) LiquidActionGrid(listOf(
            HubAction("type-expense","مصروف","cart"),HubAction("type-income","دخل","wallet"),HubAction("type-transfer","تحويل","transfer")),selected="type-${type.name.lowercase()}") {key->
            type=when(key){"type-income"->TxType.INCOME;"type-transfer"->TxType.TRANSFER;else->TxType.EXPENSE}
            category=data.categories.firstOrNull {it.income==(type==TxType.INCOME)&&!it.archived}?.id?:""
        } else Hint("${typeLabel(type)}${if(due!=null) " • المتبقي ${money(data.remaining(due))}" else ""}")
        if(type!=TxType.TRANSFER) {
            val cats=data.categories.filter {it.income==(type==TxType.INCOME)&&(!it.archived||it.id==category)}
            if(due==null && editing?.dueId==null) CategoryChoice(cats,category,data.transactions,model) {category=it}
            else Hint("التصنيف: ${data.category(category)}؛ مرتبط بالالتزام")
        }
        val accounts=data.accounts.filter {!it.archived||it.id==account||it.id==destination}
        Choice(if(type==TxType.TRANSFER) "من حساب" else "الحساب",account,accounts.map {it.id to it.name}) {key->
            account=key;payment=data.accounts.first {it.id==key}.kind
            if(destination==key) destination=accounts.firstOrNull {it.id!=key}?.id?:""
        }
        if(type==TxType.TRANSFER) {
            Choice("إلى حساب",destination,accounts.filter {it.id!=account}.map {it.id to it.name}) {destination=it}
            if(editing==null) {Field(fee,{fee=it},"رسوم التحويل — مصروف منفصل (اختياري)",true);Hint("الرسوم تُسجل في عملية مستقلة من حساب المصدر؛ تعديل التحويل لاحقًا لا يعدل عملية الرسوم تلقائيًا.")}
            Hint("التحويل لا يُحتسب دخلًا أو مصروفًا")
        }
        TextButton(onClick={details=!details}) {Text(if(details) "إخفاء التفاصيل" else "التاريخ وطريقة الدفع والملاحظة")}
        if(details) {
            Field(date,{date=it;dateError=null},"التاريخ YYYY-MM-DD",error=dateError)
            Choice("طريقة الدفع",payment,listOf("نقد","حساب بنكي","محفظة","بطاقة","أخرى").map {it to it}) {payment=it}
            Field(note,{note=it.take(500)},"ملاحظة اختيارية")
        } else Hint("التاريخ $date • $payment")
        if(editing==null&&due==null&&type in listOf(TxType.EXPENSE,TxType.INCOME)&&data.templates.size<100) {
            Row { Checkbox(checked=saveAsTemplate,onCheckedChange={saveAsTemplate=it});Text("حفظ نسخة كقالب سريع",modifier=Modifier.padding(top=12.dp)) }
            if(saveAsTemplate)Field(templateTitle,{templateTitle=it.take(60)},"اسم القالب")
        }
        ErrorText(error)
        Button(onClick={try {
            if(saveAsTemplate&&type in listOf(TxType.EXPENSE,TxType.INCOME))require(templateTitle.isNotBlank()){"أدخل اسم القالب"}
            error=null
            val value=runCatching{Finance.money(amount)}.getOrNull()
            amountError=if(value==null)"أدخل مبلغًا صالحًا، بحد أقصى خانتين عشريتين"else if(value<=0)"المبلغ أكبر من صفر"else null
            val parsedDate=runCatching{LocalDate.parse(date)}.getOrNull()
            dateError=when {parsedDate==null->"اكتب التاريخ بصيغة YYYY-MM-DD";parsedDate<LocalDate.parse(data.prefs.trackingStart)->"التاريخ يسبق بدء المتابعة";parsedDate>LocalDate.now()->"العملية الفعلية لا تكون في المستقبل";else->null}
            if(dateError!=null)details=true
            if(amountError!=null||dateError!=null)return@Button
            requireNotNull(value)
            val tx=Transaction(id=id,type=type,amount=value,date=date,accountId=account,
                destinationId=if(type==TxType.TRANSFER) destination else null,
                categoryId=if(type==TxType.TRANSFER)null else category,payment=payment,note=note.trim(),
                originalId=editing?.originalId,dueId=editing?.dueId?:due?.id,created=editing?.created?:System.currentTimeMillis())
            val feeAmount=if(type==TxType.TRANSFER&&editing==null)Finance.money(fee)else 0L
            data.copy(transactions=data.transactions.filterNot {it.id==id}+tx).validate()
            val similar=data.transactions.any {it.id!=id&&it.type==tx.type&&it.amount==tx.amount&&it.date==tx.date&&it.accountId==tx.accountId&&it.categoryId==tx.categoryId&&it.destinationId==tx.destinationId}
            if(similar&&editing==null) {pending=tx;pendingFee=feeAmount;duplicate=true}
            else submit(tx,feeAmount)
        }catch(e:Exception){error=userError(e)}},enabled=!submitted,modifier=Modifier.fillMaxWidth()) {Text(if(submitted)"جارٍ الحفظ…"else"حفظ")}
    }
    if(duplicate) AlertDialog(onDismissRequest={duplicate=false},title={Text("عملية مشابهة موجودة")},
        text={Text("هناك عملية بنفس النوع والمبلغ والتاريخ والحساب. هل هذه عملية أخرى مقصودة؟")},
        confirmButton={TextButton(onClick={duplicate=false;pending?.let {submit(it,pendingFee)}}) {Text("حفظ عملية أخرى")}},dismissButton={TextButton(onClick={duplicate=false}) {Text("مراجعة")}})
}

@Composable fun RefundForm(data: Household,model: AppModel,original: Transaction,dismiss: ()->Unit) {
    val editing=original.type==TxType.REFUND
    val source=if(editing)data.transactions.first {it.id==original.originalId}else original
    val available=source.amount-data.transactions.filter {it.originalId==source.id&&it.id!=original.id}.sumOf {it.amount}
    val id=rememberSaveable {if(editing)original.id else newId()}
    var amount by rememberSaveable {mutableStateOf(amountText(if(editing)original.amount else available))}
    var account by rememberSaveable {mutableStateOf(original.accountId)}
    var date by rememberSaveable {mutableStateOf(if(editing)original.date else today())}
    var note by rememberSaveable {mutableStateOf(if(editing)original.note else "استرداد ${data.category(source.categoryId)}")}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by remember {mutableStateOf(false)}
    DialogForm("استرداد مصروف",dismiss) {
        Hint("مرتبط بمصروف ${source.date} بقيمة ${money(source.amount)}؛ الحد المتاح ${money(available)}")
        Field(amount,{amount=it},"المبلغ المسترد",true)
        Choice("حساب استلام المال",account,data.accounts.filter {!it.archived||it.id==account}.map {it.id to it.name}){account=it}
        Field(date,{date=it},"التاريخ YYYY-MM-DD")
        Field(note,{note=it.take(500)},"الملاحظة")
        ErrorText(error)
        Button(onClick={try {
            val value=Finance.money(amount);require(value in 1..available) {"الاسترداد أكبر من صفر ولا يتجاوز المتاح"}
            val tx=Transaction(id=id,type=TxType.REFUND,amount=value,date=date,accountId=account,categoryId=source.categoryId,
                payment=data.accounts.first {it.id==account}.kind,note=note,originalId=source.id,dueId=source.dueId)
            data.copy(transactions=data.transactions.filterNot {it.id==id}+tx).validate()
            submitted=true;model.saveTransaction(tx,onResult={if(it)dismiss()else submitted=false})
        }catch(e:Exception){error=userError(e)}},enabled=!submitted,modifier=Modifier.fillMaxWidth()) {Text("تسجيل الاسترداد")}
    }
}

@Composable fun AccountForm(data: Household,model: AppModel,dismiss: ()->Unit,editing: Account?=null) {
    val id=rememberSaveable {editing?.id?:newId()}
    var name by rememberSaveable {mutableStateOf(editing?.name?:"")}
    var kind by rememberSaveable {mutableStateOf(editing?.kind?:"نقد")}
    var opening by rememberSaveable {mutableStateOf(amountText(editing?.opening?:0))}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by remember {mutableStateOf(false)}
    DialogForm(if(editing==null)"حساب جديد"else"تعديل الحساب",dismiss) {
        Field(name,{name=it.take(50)},"اسم الحساب")
        Choice("نوع الحساب",kind,listOf("نقد","حساب بنكي","محفظة").map {it to it}) {kind=it}
        Field(opening,{opening=it},"الرصيد الافتتاحي عند ${data.prefs.trackingStart}",true)
        Hint("الرصيد الافتتاحي لا يدخل الدخل. استخدم تحويلًا لنقل مال موجود بين حساباتك؛ لا تضفه مرة ثانية كرصيد افتتاحي.")
        ErrorText(error)
        Button(onClick={try {
            require(name.isNotBlank()) {"أدخل اسم الحساب"}
            require(data.accounts.none {it.id!=id&&it.name==name.trim()}) {"اسم الحساب موجود"}
            val account=Account(id,name.trim(),kind,Finance.money(opening),editing?.archived?:false)
            submitted=true;model.change(onResult={if(it)dismiss()else submitted=false}) { it.copy(accounts=it.accounts.filterNot {a->a.id==id}+account) }
        }catch(e:Exception){error=userError(e)}},enabled=!submitted,modifier=Modifier.fillMaxWidth()) {Text("حفظ الحساب")}
    }
}

@Composable fun BillForm(data: Household,model: AppModel,dismiss: ()->Unit,editing: BillRule?=null) {
    val id=rememberSaveable {editing?.id?:newId()}
    var title by rememberSaveable {mutableStateOf(editing?.title?:"")}
    var amount by rememberSaveable {mutableStateOf(editing?.amount?.let {amountText(it)}?:"")}
    var date by rememberSaveable {mutableStateOf(editing?.start?:today())}
    var end by rememberSaveable {mutableStateOf(editing?.end?:"")}
    var every by rememberSaveable {mutableStateOf(editing?.intervalMonths?.toString()?:"1")}
    var category by rememberSaveable {mutableStateOf(editing?.categoryId?:data.categories.first {!it.income&&!it.archived}.id)}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by remember {mutableStateOf(false)}
    DialogForm(if(editing==null)"فاتورة أو قسط متكرر"else"تعديل التكرار المستقبلي",dismiss) {
        Field(title,{title=it.take(80)},"اسم الالتزام")
        Field(amount,{amount=it},"المبلغ المخطط لكل استحقاق",true)
        CategoryChoice(data.categories.filter {!it.income&&(!it.archived||it.id==category)},category,data.transactions,model) {category=it}
        Field(date,{date=it},"أول استحقاق YYYY-MM-DD")
        Choice("التكرار",every,listOf("1" to "شهري","2" to "كل شهرين","3" to "كل 3 أشهر","6" to "كل 6 أشهر","12" to "سنوي")) {every=it}
        Field(end,{end=it},"آخر تاريخ — اختياري YYYY-MM-DD")
        Hint("المبلغ مخطط فقط؛ يصبح مصروفًا عند تسجيل السداد. يوم 31 ينتقل لآخر يوم في الشهر القصير، ثم يعود إلى 31 في الشهر التالي.")
        if(editing!=null)Hint("التعديل يطبق على الاستحقاقات المستقبلية التي لا ترتبط بأي سداد. الاستحقاقات السابقة والحالية والمرتبطة بعمليات تبقى كما هي.")
        ErrorText(error)
        Button(onClick={try {
            require(title.isNotBlank()) {"أدخل اسم الالتزام"};LocalDate.parse(date)
            val rule=BillRule(id,title.trim(),Finance.money(amount),category,date,every.toInt(),end.ifBlank {null},editing?.active?:true)
            val candidate=data.copy(rules=data.rules.filterNot {it.id==id}+rule)
            candidate.validate()
            submitted=true;model.change(onResult={if(it)dismiss()else submitted=false}) {old->
                old.copy(rules=old.rules.filterNot {it.id==id}+rule,
                    dues=if(editing!=null)old.dues.filterNot {d->d.ruleId==id&&LocalDate.parse(d.date)>LocalDate.now()&&old.transactions.none {it.dueId==d.id}}else old.dues)
            }
        }catch(e:Exception){error=userError(e)}},enabled=!submitted,modifier=Modifier.fillMaxWidth()) {Text("إضافة الالتزام")}
    }
}

@Composable fun BackupForm(creating: Boolean,dismiss: ()->Unit,done: (String)->Unit) {
    var password by remember {mutableStateOf("")}
    var again by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    DialogForm(if(creating)"نسخة احتياطية مشفرة"else"استعادة نسخة مشفرة",dismiss) {
        Hint(if(creating)"اختر كلمة مرور واحفظها خارج الهاتف؛ لا يمكن استعادتها إذا نسيتها. اختر مكان الملف في الخطوة التالية."else"أدخل كلمة مرور النسخة ثم اختر الملف. نفحص الملف أولًا، ونطلب تأكيد الاستبدال بعد الفحص.")
        Field(password,{password=it},"كلمة المرور",secret=true)
        if(creating)Field(again,{again=it},"تأكيد كلمة المرور",secret=true)
        ErrorText(error)
        Button(onClick={when {
            password.length<8->error="8 أحرف على الأقل"
            creating&&password!=again->error="كلمتا المرور غير متطابقتين"
            else->done(password)
        }},modifier=Modifier.fillMaxWidth()) {Text("اختيار الملف")}
    }
}
