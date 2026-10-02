package com.hesabbeitna.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.math.BigDecimal

@Composable fun DuePlanForm(data:Household,model:AppModel,due:Due,dismiss:()->Unit) {
    var amount by remember {mutableStateOf(BigDecimal.valueOf(due.amount,2).toPlainString())}
    var error by remember {mutableStateOf<String?>(null)}
    var submitted by remember {mutableStateOf(false)}
    DialogForm("المبلغ المخطط — ${due.title}",dismiss) {
        Hint("استحقاق ${due.date}. هذا التعديل لهذا الموعد فقط؛ لا يغير المصروف الفعلي أو القاعدة المستقبلية.")
        Field(amount,{amount=it},"مبلغ الاستحقاق — جنيه",true)
        Hint("المدفوع فعليًا ${money(data.paid(due))}؛ المبلغ المخطط لا يقل عن المدفوع.")
        ErrorText(error)
        Button(onClick={try {
            val value=Finance.money(amount)
            require(value>0&&value>=data.paid(due)) {"المبلغ أكبر من صفر ولا يقل عن المدفوع"}
            submitted=true
            model.change(onResult={if(it)dismiss()else submitted=false}) {old->
                old.copy(dues=old.dues.map {if(it.id==due.id)it.copy(amount=value)else it})
            }
        }catch(e:Exception){error=userError(e)}},enabled=!submitted) {Text("حفظ المخطط")}
    }
}
