@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hesabbeitna.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class HubAction(val key:String,val title:String,val icon:String)
@Composable fun ActionGrid(actions:List<HubAction>,selected:String?=null,click:(String)->Unit) {
    LiquidActionGrid(actions,selected,click)
}
@Composable fun QuickAddSheet(dismiss:()->Unit,choose:(String)->Unit) {
    ModalBottomSheet(onDismissRequest=dismiss,containerColor=androidx.compose.ui.graphics.Color.Transparent,dragHandle=null) {
        LiquidSurface(Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(),prominent=true) {
            Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                MeowMessage("تضيف إيه؟","اختَر العملية، ثم أدخل المبلغ")
                LiquidActionGrid(listOf(HubAction("add-expense","مصروف","cart"),HubAction("add-income","دخل","wallet"),
                    HubAction("add-transfer","تحويل","transfer"),HubAction("add-payment","سداد فاتورة","bill"),HubAction("add-template","قوالبي السريعة","star")),click=choose)
                TextButton(onClick=dismiss,modifier=Modifier.fillMaxWidth()){Text("إغلاق")}
            }
        }
    }
}
@Composable fun MoreScreen(open:(String)->Unit) {
    Page {
        ScreenTitle("كل أدوات بيتك","اختصارات واضحة لإدارة البيت والبيانات")
        ActionGrid(listOf(HubAction("more-accounts","الحسابات والمحافظ","wallet"),HubAction("more-budget","الميزانيات","budget"),
            HubAction("more-dues","الفواتير والأقساط","calendar"),HubAction("more-categories","الأصناف","cart"),
            HubAction("more-backup","نسخة احتياطية","shield"),HubAction("more-restore","استعادة البيانات","shield"),
            HubAction("more-export","التقارير والتصدير","chart"),HubAction("more-settings","المظهر والإعدادات","settings"),HubAction("more-templates","القوالب السريعة","star")),click=open)
        Hint("اختيار الفاتح أو الأسود والمظهر الزجاجي من المظهر والإعدادات")
        Spacer(Modifier.height(8.dp))
    }
}
@Composable fun SearchScreen(data:Household,open:(SearchHit)->Unit,navigate:(String)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    var results by remember{mutableStateOf<List<SearchHit>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    LaunchedEffect(data,query){
        loading=query.isNotBlank();results=emptyList()
        if(query.isNotBlank()){delay(200);results=withContext(Dispatchers.Default){searchHousehold(data,query)}}
        loading=false
    }
    Page {
        ScreenTitle("بحث شامل","الحسابات والأصناف والفواتير والعمليات في جميع الدورات")
        OutlinedTextField(value=query,onValueChange={query=it.take(120)},modifier=Modifier.fillMaxWidth().testTag("search-input"),
            label={Text("اسم، ملاحظة، مبلغ أو تاريخ")},singleLine=true,shape=Brand.Input,
            trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""},modifier=Modifier.semantics{contentDescription="مسح البحث"}){ToolIcon("close")}})
        if(query.isBlank()){
            ActionGrid(listOf(HubAction("search-accounts","الحسابات","wallet"),HubAction("search-budget","الميزانية","budget"),
                HubAction("search-dues","الفواتير","bill"),HubAction("search-settings","الإعدادات","settings")),click={navigate(it.removePrefix("search-"))})
            Hint("مثال: كهرباء، 125.50، أو 2026-10")
        }else if(loading)CircularProgressIndicator()else if(results.isEmpty()){
            Panel("لا توجد نتائج"){Hint("جرّب كلمة أقصر أو جزءًا من المبلغ أو التاريخ");TextButton(onClick={query=""}){Text("بدء بحث جديد")}}
        }else{
            Hint("تظهر حتى 20 حسابًا و20 صنفًا و30 فاتورة وأحدث 50 عملية مطابقة")
            for((kind,label)in listOf("action" to "الأدوات","account" to "الحسابات","category" to "الأصناف","due" to "الفواتير","transaction" to "العمليات")){
                val rows=results.filter{it.kind==kind}
                if(rows.isNotEmpty())Panel(label){rows.forEach{hit->QuickLink(hit.title,hit.detail,when(kind){"account"->"wallet";"category"->"cart";"due"->"bill";else->"list"}){open(hit)}}}
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
@Composable fun SearchDetail(data:Household,hit:SearchHit,dismiss:()->Unit,edit:(Transaction)->Unit,pay:(Due)->Unit) {
    DialogForm(hit.title,dismiss){
        when(hit.kind){
            "transaction"->data.transactions.firstOrNull{it.id==hit.id}?.let{tx->
                AmountLine(typeLabel(tx.type),tx.amount);Hint("${displayDate(tx.date)} • ${data.account(tx.accountId)}")
                if(tx.destinationId!=null)Hint("إلى ${data.account(tx.destinationId)}")
                if(tx.categoryId!=null)Hint(data.category(tx.categoryId));if(tx.note.isNotBlank())Text(tx.note)
                PrimaryAction("تعديل هذه العملية",{edit(tx)})
            }
            "due"->data.dues.firstOrNull{it.id==hit.id}?.let{due->
                Hint("${displayDate(due.date)} • ${data.category(due.categoryId)}")
                AmountLine("المتبقي",data.remaining(due));Hint("المخطط ${money(due.amount)} • المدفوع ${money(data.paid(due))}")
                if(data.remaining(due)>0)PrimaryAction("تسجيل سداد",{pay(due)})
            }
            else->{
                if(hit.kind=="account")data.accounts.firstOrNull{it.id==hit.id}?.let{AmountLine("الرصيد الحالي",data.balance(it))}
                val rows=remember(data,hit){data.transactions.filter{if(hit.kind=="category")it.categoryId==hit.id else it.accountId==hit.id||it.destinationId==hit.id}
                    .sortedWith(compareByDescending<Transaction>{it.date}.thenByDescending{it.created})}
                var limit by remember{mutableIntStateOf(20)}
                Hint("${rows.size} عملية مرتبطة في جميع الدورات")
                if(rows.isEmpty())Hint("لا توجد عمليات مرتبطة")
                rows.take(limit).forEach{tx->QuickLink(tx.note.ifBlank{typeLabel(tx.type)},"${displayDate(tx.date)} • ${money(tx.amount)}","list"){edit(tx)}}
                if(rows.size>limit)TextButton(onClick={limit+=20}){Text("عرض المزيد")}
            }
        }
    }
}
