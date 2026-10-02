@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hesabbeitna.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun decimalAmount(value:Long)=BigDecimal.valueOf(value,2).toPlainString()
@Composable fun AnalyticsScreen(data:Household,period:Finance.Period,model:AppModel,initialTab:String="overview") {
    var loaded by remember(data,period.start,period.end){mutableStateOf<Analytics?>(null)}
    LaunchedEffect(data,period.start,period.end) {
        loaded=withContext(Dispatchers.Default){Analytics(data,period).also{it.signals()}}
    }
    val report=loaded
    if(report==null){Page{ScreenTitle("جارٍ تحليل السجل");CircularProgressIndicator()};return}
    var tab by rememberSaveable{mutableStateOf(initialTab)}
    val tabState=rememberSaveableStateHolder()
    var categoryId by rememberSaveable(period.start){mutableStateOf<String?>(null)}
    var evidenceTitle by remember{mutableStateOf("")}
    var evidenceIds by remember{mutableStateOf<List<String>>(emptyList())}
    var evidenceOpen by remember{mutableStateOf(false)}
    fun evidence(title:String,rows:List<Transaction>){evidenceTitle=title;evidenceIds=rows.map{it.id};evidenceOpen=true}
    val tabs=listOf(Triple("overview","نظرة عامة","home"),Triple("spending","تفاصيل الإنفاق","list"),Triple("comparison","المقارنات","chart"),Triple("planning","خطة التوفير","budget"))
    Column(Modifier.fillMaxSize()) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                tabs.chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach{(key,label,icon)->FilterChip(selected=tab==key,onClick={tab=key},label={Text(label)},leadingIcon={ToolIcon(icon)},
                        modifier=Modifier.weight(1f).heightIn(min=52.dp).testTag("analysis-$key"))}
                }}
            }
        }
    tabState.SaveableStateProvider(tab){Page {
        ScreenTitle("تحليلات واضحة","مساعد البيت المالي • ${periodLabel(period)}")
        if(report.coveredDays==0L)Hint("الفترة خارج الأيام المسجلة حتى اليوم؛ لا يمكن استنتاج نمط للصرف منها")
        else Hint("الأرقام من السجل المتاح خلال ${report.coveredDays} يومًا. اليوم بلا عملية لا يثبت عدم الصرف.")
        when(tab) {
            "overview"->{
                Panel("صورة الدورة") {
                    AmountLine("صافي المصروفات",report.expense)
                    AmountLine("الدخل المسجل",report.income)
                    AmountLine("الفائض المسجل",report.surplus,if(report.surplus>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Hint("الفائض = الدخل − صافي المصروفات. يختلف عن رصيد الحسابات؛ التحويلات والأرصدة الافتتاحية ليست دخلًا.")
                }
                Panel("القطة تلخص لك") {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Mascot(56.dp);Text("ما الذي يستحق انتباهك؟",style=MaterialTheme.typography.titleMedium)}
                    val top=report.categories.firstOrNull{it.net>0}
                    if(top==null)Hint("أضف مصروفات حقيقية لتظهر قراءة للإنفاق") else {
                        Text("أكبر صنف: ${data.category(top.id)} بقيمة ${money(top.net)}")
                        TextButton(onClick={categoryId=top.id}){Text("راجع الصنف والعمليات")}
                    }
                    val change=report.changes.firstOrNull{it.delta>0}
                    if(change!=null){Text("أكبر مساهمة في الزيادة: ${data.category(change.id)} بفرق ${money(change.delta)}");TextButton(onClick={tab="comparison"}){Text("لماذا تغيّر الصرف؟")}}
                    else Hint(if(report.comparable)"لا توجد زيادة في صافي أي صنف مقارنة بالمدة السابقة المتساوية"else"المقارنة تحتاج سجلًا يبدأ قبل الفترة السابقة")
                    report.availableDaily?.let{Text("المتاح يوميًا بعد حجز الالتزامات: ${money(it)} خلال ${report.daysLeft} يومًا بعد اليوم")}
                    if(report.availableDaily==null)Hint("حدد ميزانية للدورة ليظهر المتاح اليومي. متوسط الأيام التالية لا يشمل بقية اليوم الحالي.")
                }
                Panel("اسأل Meow Budget") {
                    TextButton(onClick={tab="spending"}){Text("أكثر صنف صرفت عليه؟")}
                    TextButton(onClick={tab="comparison"}){Text("ليه الصرف اتغيّر؟")}
                    TextButton(onClick={tab="planning"}){Text("أقدر أوفّر منين؟")}
                    Hint("إجابات محسوبة على الهاتف من سجلك، دون إرسال بيانات أو الحاجة للإنترنت")
                }
                SpendingDistribution(report){categoryId=it}
                Panel("نظرة لنهاية الدورة") {
                    if(report.forecast==null)Hint("التوقع متاح للدورة الحالية بعد 7 أيام مسجلة على الأقل وبوجود مصروفات")else {
                        AmountLine("توقع تقريبي للمصروفات",report.forecast)
                        Hint("الفعلي + وتيرة المصروف غير المرتبط بالتزامات × الأيام المتبقية + الالتزامات غير المدفوعة ${money(report.outstanding)}. يعتمد على اكتمال التسجيل وليس ضمانًا.")
                    }
                }
                val signals=remember(report){report.signals()}
                Panel("ملاحظات تستحق المراجعة") {
                    if(signals.isEmpty())Hint("لم يظهر نمط يستدعي المراجعة وفق القواعد الحالية؛ هذا لا يثبت أن السجل خالٍ من الأخطاء")
                    signals.forEach{signal->Text(signal.title,style=MaterialTheme.typography.titleMedium);Hint(signal.explanation);TextButton(onClick={evidence(signal.title,signal.transactions)}){Text("راجع العمليات")}}
                }
            }
            "spending"->{
                SpendingDistribution(report){categoryId=it}
                Panel("كل صنف بالتفصيل") {
                    if(report.categories.isEmpty())Hint("لا توجد مصروفات أو استردادات في الفترة")
                    report.categories.forEach{cat->CategorySummary(data,cat){categoryId=cat.id}}
                }
                SpendingTrend(report){title,rows->evidence(title,rows)}
                Panel("المدفوعات الصغيرة المتكررة") {
                    val small=report.transactions.filter{it.type==TxType.EXPENSE&&it.amount<=10_000}
                    Text("${small.size} عملية بقيمة 100 جنيه أو أقل للعملية")
                    AmountLine("إجمالي المدفوعات قبل الاسترداد",small.sumOf{it.amount})
                    Hint("100 جنيه حد توضيحي لهذا التحليل؛ صغر العملية أو تكرارها لا يعني أنها غير ضرورية")
                    if(small.isNotEmpty())TextButton(onClick={evidence("مدفوعات 100 جنيه أو أقل",small)}){Text("عرض العمليات الصغيرة")}
                }
            }
            "comparison"->ComparisonAnalysis(report){title,rows->evidence(title,rows)}
            "planning"->SavingPlanner(report,model)
        }
        Spacer(Modifier.height(80.dp))
    }}
    }
    categoryId?.let{id->val cat=report.categories.firstOrNull{it.id==id}?:Analytics.emptyCategory(id)
        DialogForm("تحليل ${data.category(id)}",{categoryId=null}) {
            AmountLine("صافي الصنف",cat.net);Hint("المدفوعات ${money(cat.gross)} • الاستردادات ${money(cat.refunds)}")
            Text("${cat.count} عملية مصروف • متوسط العملية ${money(cat.average)} قبل الاستردادات")
            cat.largest?.let{Text("أكبر مصروف ${money(it.amount)} في ${displayDate(it.date)}")}
            data.budget(period,id)?.let{AmountLine("المتبقي من ميزانية الصنف",it.amount-cat.net)}?:Hint("لا توجد ميزانية محددة لهذا الصنف في الدورة")
            val change=report.changes.firstOrNull{it.id==id}
            if(change!=null){Text("المدة المتساوية الحالية ${money(change.current.net)} • السابقة ${money(change.previous.net)}");AmountLine("فرق الصنف",change.delta)}
            AnalyticsRows(data,cat.transactions)
        }
    }
    val evidenceSet=remember(evidenceIds){evidenceIds.toHashSet()}
    if(evidenceOpen)DialogForm(evidenceTitle,{evidenceOpen=false}){AnalyticsRows(data,data.transactions.filter{it.id in evidenceSet}.sortedWith(compareByDescending<Transaction>{it.date}.thenByDescending{it.created}))}
}

@Composable private fun AnalyticsRows(data:Household,rows:List<Transaction>) {
    var limit by rememberSaveable{mutableIntStateOf(30)}
    Hint("${rows.size} عملية • التفاصيل للقراءة؛ يمكن التعديل من سجل العمليات")
    if(rows.isEmpty())Hint("لا توجد عمليات لهذا الاختيار")
    rows.take(limit).forEach{tx->
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Text("${typeLabel(tx.type)} • ${data.category(tx.categoryId)}",style=MaterialTheme.typography.titleMedium)
        Text(money(tx.amount),color=if(tx.type==TxType.REFUND)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Hint("${displayDate(tx.date)} • ${data.account(tx.accountId)}")
        if(tx.note.isNotBlank())Text(tx.note,style=MaterialTheme.typography.bodyMedium)
    }
    if(rows.size>limit)TextButton(onClick={limit+=30}){Text("عرض المزيد")}
}
@Composable private fun CategorySummary(data:Household,cat:CategoryAnalysis,click:()->Unit) {
    val appearance=LocalAppearance.current
    LiquidClickableSurface(onClick=click,modifier=Modifier.fillMaxWidth().heightIn(min=72.dp).testTag("analysis-category-${cat.id}")) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                ToolIcon(data.categories.firstOrNull{it.id==cat.id}?.let{appearance.icon(it)}?:"wallet")
                Text(data.category(cat.id),style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                ToolIcon("back")
            }
            Text(money(cat.net),style=MaterialTheme.typography.headlineSmall)
            Hint("${cat.count} عملية • متوسط ${money(cat.average)} قبل الاستردادات")
        }
    }
}
@Composable private fun chartColors()=listOf(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.secondary,Color(0xFF719AB7),Color(0xFFAA8BBE),Color(0xFFBE9965),Color(0xFF6AA39B))
@Composable private fun SpendingDistribution(report:Analytics,select:(String)->Unit) {
    val positive=report.categories.filter{it.net>0};val total=positive.sumOf{it.net};val colors=chartColors()
    Panel("توزيع الإنفاق") {
        if(total==0L)Hint("لا توجد بنود بصافي موجب لرسم التوزيع")else {
            Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                Canvas(Modifier.size(208.dp).testTag("spending-donut").semantics{contentDescription="توزيع صافي البنود الموجب؛ التفاصيل في قائمة الأصناف"}.pointerInput(positive){
                    detectTapGestures{point->
                        val dx=point.x-size.width/2f;val dy=point.y-size.height/2f
                        val radius=kotlin.math.sqrt(dx*dx+dy*dy)
                        if(radius>size.width*.30f&&radius<size.width*.51f){
                            val angle=((Math.toDegrees(atan2(dy,dx).toDouble())+90+360)%360).toFloat();var edge=0f
                            positive.forEach{cat->edge+=(cat.net.toDouble()/total*360).toFloat();if(angle<edge){select(cat.id);return@detectTapGestures}}
                        }
                    }
                }) {
                    var angle=-90f
                    positive.forEachIndexed{i,cat->val sweep=(cat.net.toDouble()/total*360).toFloat();drawArc(colors[i%colors.size],angle,sweep,false,topLeft=Offset(14.dp.toPx(),14.dp.toPx()),size=Size(size.width-28.dp.toPx(),size.height-28.dp.toPx()),style=Stroke(22.dp.toPx()));angle+=sweep}
                }
                Column(Modifier.widthIn(max=132.dp),horizontalAlignment=Alignment.CenterHorizontally){Hint("صافي المصروفات");Text(money(report.expense),style=MaterialTheme.typography.titleMedium)}
            }
            positive.forEachIndexed{i,cat->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Canvas(Modifier.size(10.dp)){drawCircle(colors[i%colors.size])}
                TextButton(onClick={select(cat.id)},modifier=Modifier.weight(1f)){Text("${report.data.category(cat.id)} • ${percent(Finance.percent(cat.net,total))}")}
                Text(money(cat.net),style=MaterialTheme.typography.bodySmall)
            }}
            Hint("اضغط على جزء من الدائرة أو اسم الصنف لفتح التفاصيل")
        }
        if(report.categories.any{it.net<0})Hint("الدائرة توزع البنود ذات الصافي الموجب فقط؛ الاستردادات التي جعلت صنفًا سالبًا تظهر في التفاصيل ويشملها الإجمالي")
    }
}
@Composable private fun SpendingTrend(report:Analytics,evidence:(String,List<Transaction>)->Unit) {
    var unit by rememberSaveable{mutableStateOf("day")}
    var selected by rememberSaveable(report.period.start,unit){mutableIntStateOf(-1)}
    val buckets=remember(report,unit){report.buckets(unit)}
    Panel("اتجاه الإنفاق") {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("day" to "يومي","week" to "أسبوعي","month" to "شهري").forEach{(key,label)->FilterChip(selected=unit==key,onClick={unit=key},label={Text(label)})}
        }
        if(buckets.isEmpty())Hint("لا توجد أيام مغطاة بالرسم")else {
            InteractiveTrend(buckets.map{it.second},selected){selected=it}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Hint(displayDate(buckets.first().first));Hint(displayDate(buckets.last().first))}
            buckets.getOrNull(selected)?.let{(key,amount)->Text("${displayDate(key)} • ${money(amount)}");TextButton(onClick={evidence("تفاصيل ${displayDate(key)}",report.bucketTransactions(key,unit))}){Text("عمليات الفترة المختارة")}}
            buckets.forEach{(key,amount)->TextButton(onClick={evidence("تفاصيل ${displayDate(key)}",report.bucketTransactions(key,unit))},modifier=Modifier.fillMaxWidth()) {Text("${displayDate(key)}: ${money(amount)}")}}
        }
        Hint("الأخضر مصروف صافٍ، والأحمر صافي استردادات. الأسبوع يبدأ الأحد، وقد يكون أول وآخر أسبوع جزئيين.")
    }
}
@Composable private fun InteractiveTrend(values:List<Long>,selected:Int,select:(Int)->Unit) {
    val primary=MaterialTheme.colorScheme.primary;val error=MaterialTheme.colorScheme.error;val outline=MaterialTheme.colorScheme.outlineVariant
    val upper=(values.maxOrNull()?:0).coerceAtLeast(1).toDouble();val lower=(values.minOrNull()?:0).coerceAtMost(0).toDouble();val range=upper-lower
    Hint("أعلى قيمة ${money(values.maxOrNull()?:0)} • أقل قيمة ${money(values.minOrNull()?:0)}")
    Canvas(Modifier.fillMaxWidth().height(168.dp).testTag("spending-trend").semantics{contentDescription="اتجاه صافي الإنفاق؛ كل نقطة لها تفاصيل نصية أسفل الرسم"}.pointerInput(values){detectTapGestures{p->if(values.isNotEmpty())select((values.size-1-(p.x/(size.width.toFloat()/values.size)).toInt()).coerceIn(values.indices))}}) {
        val step=size.width/values.size.coerceAtLeast(1);val base=(upper/range*size.height).toFloat()
        drawLine(outline,Offset(0f,base),Offset(size.width,base),1.dp.toPx())
        values.forEachIndexed{i,value->
            val y=((upper-value)/range*size.height).toFloat();val x=size.width-(i+.5f)*step
            drawRoundRect(if(value>=0)primary.copy(alpha=if(i==selected)1f else .65f)else error,Offset(x-step*.3f,minOf(y,base)),Size(step*.6f,abs(base-y).coerceAtLeast(1.dp.toPx())))
            if(i>0){val old=((upper-values[i-1])/range*size.height).toFloat();drawLine(primary,Offset(x+step,old),Offset(x,y),2.dp.toPx())}
            if(i==selected)drawCircle(primary,5.dp.toPx(),Offset(x,y.coerceIn(5.dp.toPx(),size.height-5.dp.toPx())))
        }
    }
}
@Composable private fun ComparisonAnalysis(report:Analytics,evidence:(String,List<Transaction>)->Unit) {
    Panel("مقارنة عادلة") {
        Hint("الحالية: ${periodLabel(report.windows[0])}\nالسابقة: ${periodLabel(report.windows[1])}")
        if(!report.comparable)Hint("لا توجد مدة سابقة متساوية يغطيها تاريخ بدء المتابعة")else {
            val current=report.changes.sumOf{it.current.net};val previous=report.changes.sumOf{it.previous.net}
            AmountLine("صافي المدة الحالية",current);AmountLine("صافي المدة السابقة",previous);AmountLine("الفرق",current-previous)
            Hint("التغير ${percent(Finance.change(current,previous))}. لا تحسب النسبة إذا كان الصافي السابق صفرًا أو سالبًا.")
        }
        Hint("تساوي الأيام لا يثبت اكتمال إدخال العمليات في الفترتين")
    }
    if(report.comparable) {
        Panel("من أين جاء الفرق؟") {
            if(report.changes.isEmpty())Hint("لا توجد مصروفات للمقارنة")
            report.changes.forEach{change->
                Text(report.data.category(change.id),style=MaterialTheme.typography.titleMedium)
                AmountLine(if(change.delta>=0)"زيادة الصافي"else"انخفاض الصافي",abs(change.delta),if(change.delta>0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(change.driver,style=MaterialTheme.typography.bodyMedium)
                Hint("${change.previous.count} ← ${change.current.count} عملية مصروف • متوسط ${money(change.previous.average)} ← ${money(change.current.average)}")
                Hint("الاستردادات: ${money(change.previous.refunds)} سابقًا، ${money(change.current.refunds)} حاليًا. اختلاف العدد والمتوسط يفسر المدفوعات، والاستردادات تؤثر في الصافي.")
                TextButton(onClick={evidence("مقارنة ${report.data.category(change.id)}",change.current.transactions+change.previous.transactions)}){Text("راجع عمليات الفترتين")}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    Panel("الدخل والصرف عبر الدورات") {
        val history=report.cycleHistory()
        history.forEach{cycle->
            Text(periodLabel(cycle.period),style=MaterialTheme.typography.titleSmall)
            Text("دخل ${money(cycle.income)} • صرف ${money(cycle.expense)}")
            val scale=history.maxOf{maxOf(abs(it.income),abs(it.expense))}.coerceAtLeast(1).toDouble()
            Hint("الدخل")
            LinearProgressIndicator(progress={(cycle.income/scale).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            Hint(if(cycle.expense<0)"صافي استردادات"else"صافي الصرف")
            LinearProgressIndicator(progress={(abs(cycle.expense)/scale).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.secondary)
            AmountLine("الفائض",cycle.income-cycle.expense)
            TextButton(onClick={evidence("عمليات الدورة",report.data.transactions.filter{cycle.period.contains(LocalDate.parse(it.date))})}){Text("تفاصيل الدورة")}
        }
        Hint("الدورة الحالية قد تكون جزئية؛ الدورات بلا تسجيل لا تثبت عدم وجود دخل أو صرف")
    }
}
@Composable private fun SavingPlanner(report:Analytics,model:AppModel) {
    val data=report.data
    val options=report.categories.filter{it.net>0}
    var category by rememberSaveable(report.period.start){mutableStateOf(options.firstOrNull()?.id?:"")}
    var reduction by rememberSaveable{mutableIntStateOf(10)}
    Panel("جرّب خطة توفير") {
        Hint("اختَر بندًا يمكن تخفيضه حسب احتياجات بيتك؛ المحاكاة لا تعتبر الصحة أو الطعام مصروفات يمكن الاستغناء عنها")
        if(options.isEmpty())Hint("تحتاج بندًا بصافي موجب لتجربة المحاكاة")else {
            Choice("الصنف للتجربة",category,options.map{it.id to data.category(it.id)}){category=it}
            Text("خفض افتراضي $reduction%")
            Slider(value=reduction.toFloat(),onValueChange={reduction=it.toInt()},valueRange=0f..50f,steps=49,modifier=Modifier.testTag("saving-slider"))
            val simulation=report.simulate(category,reduction)
            AmountLine("توفير افتراضي",simulation.saving)
            AmountLine("الصرف بعد الخفض",simulation.simulatedExpense)
            AmountLine("الفائض بعد الخفض",simulation.simulatedSurplus)
            Hint("${money(simulation.baseline)} × $reduction%. هذه إعادة حساب افتراضية للدورة المختارة؛ لا تعدّل العمليات أو الميزانية ولا تتنبأ بتكرار التوفير.")
        }
    }
    val plan=remember(data){Analytics.nextPlan(data)}
    var income by rememberSaveable(plan.period.start){mutableStateOf(plan.income?.let{decimalAmount(it)}?:"")}
    var variable by rememberSaveable(plan.period.start){mutableStateOf(plan.variable?.let{decimalAmount(it)}?:"")}
    var saving by rememberSaveable(plan.period.start){mutableStateOf("0")}
    var reserve by rememberSaveable(plan.period.start){mutableStateOf("0")}
    var confirm by rememberSaveable{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val expected=runCatching{Finance.money(income)}.getOrNull()
    val spending=runCatching{Finance.money(variable)}.getOrNull()
    val target=runCatching{Finance.money(saving)}.getOrNull()
    val emergency=runCatching{Finance.money(reserve)}.getOrNull()
    val planned=spending?.let{it+plan.obligations+(emergency?:0)}
    Panel("خطة الدورة القادمة") {
        Hint("الدورة القادمة الفعلية: ${periodLabel(plan.period)}، مستقلة عن الفترة التي تستعرضها")
        if(plan.history.isEmpty())Hint("لا توجد دورة سابقة كاملة زمنيًا وبها مصروفات. أدخل تقديراتك بنفسك")else Hint("بداية المقترح من متوسط ${plan.history.size} دورات سابقة كاملة زمنيًا وبها تسجيل؛ اكتمال السجل مسؤولية المستخدم")
        Field(income,{income=it;error=null},"الدخل المتوقع — جنيه",true)
        Field(variable,{variable=it;error=null},"المصروف المتغير المقترح — جنيه",true)
        AmountLine("التزامات داخل الدورة القادمة",plan.commitments)
        AmountLine("التزامات غير مدفوعة قبل بدايتها",plan.carryover)
        Hint("الأموال المدفوعة المرتبطة بالتزام تُستبعد من المتوسط المتغير، كي لا تُحتسب مرة أخرى. ربط الفواتير بعمليات السداد يحسن دقة المقترح.")
        Field(saving,{saving=it;error=null},"هدف ادخار هذه الدورة — جنيه",true)
        Field(reserve,{reserve=it;error=null},"احتياطي طوارئ ضمن الميزانية — جنيه",true)
        if(planned!=null&&expected!=null&&target!=null&&emergency!=null) {
            AmountLine("ميزانية الصرف المقترحة",planned)
            val margin=expected-planned-target
            AmountLine(if(margin>=0)"هامش بعد الصرف والادخار"else"عجز يحتاج تعديل الخطة",abs(margin),if(margin>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Hint("الدخل − (المتغير + الالتزامات + الاحتياطي) − هدف الادخار. الهدف لا يحوّل أموالًا بين الحسابات.")
        }
        plan.dues.take(8).forEach{Hint("${it.title}: ${money(data.remaining(it))} • ${displayDate(it.date)}")}
        if(plan.dues.size>8)Hint("و${plan.dues.size-8} التزامًا آخر ضمن الإجمالي")
        ErrorText(error)
        PrimaryAction("مراجعة اعتماد الميزانية",onClick={
            if(expected==null||spending==null||target==null||emergency==null)error="أدخل مبالغ صحيحة في جميع حقول الخطة"
            else if(planned==null||planned>100_000_000_000L)error="الميزانية المقترحة تتجاوز الحد المسموح"
            else if(expected<planned+target)error="الخطة تتجاوز الدخل المتوقع. خفّض الصرف أو عدّل الهدف قبل اعتمادها"
            else confirm=true
        },modifier=Modifier.testTag("review-cycle-plan"))
        Hint("الحفظ يعتمد الميزانية العامة للدورة القادمة فقط؛ الدخل المتوقع وهدف الدورة والاحتياطي عناصر للمراجعة في هذه الخطة، ولا تُسجل كعمليات مالية")
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("اعتماد ميزانية الدورة القادمة؟")},
        text={Text("${periodLabel(plan.period)}\nميزانية عامة ${money(planned?:0)}${data.budget(plan.period)?.let{"\nستستبدل الميزانية الحالية ${money(it.amount)}"}?:""}\nلن تتغير ميزانيات الأصناف أو العمليات أو الأرصدة.")},
        confirmButton={TextButton(onClick={confirm=false;val amount=planned?:return@TextButton;model.change("تم اعتماد ميزانية الدورة القادمة"){it.copy(budgets=it.budgets.filterNot{b->b.period==plan.period.start.toString()&&b.categoryId==null}+Budget(plan.period.start.toString(),amount))}}){Text("اعتماد الميزانية")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("الرجوع للتعديل")}})
}
