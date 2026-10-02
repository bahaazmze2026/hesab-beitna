package com.hesabbeitna.app

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

data class SearchHit(val kind:String,val id:String,val title:String,val detail:String)
private fun searchText(value:String)=Normalizer.normalize(value,Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}+"),"").replace("ـ","").replace('أ','ا').replace('إ','ا').replace('آ','ا')
    .map{when(it){in '٠'..'٩'->'0'+(it-'٠');in '۰'..'۹'->'0'+(it-'۰');'٫'->'.';else->it}}
    .joinToString("").lowercase(Locale.ROOT).trim()

/** Search is derived from the entire local snapshot; amounts remain integer piastres. */
fun searchHousehold(data:Household,query:String):List<SearchHit> {
    val terms=searchText(query).split(Regex("\\s+")).filter{it.isNotBlank()}
    if(terms.isEmpty())return emptyList()
    fun matches(value:String):Boolean {val text=searchText(value);return terms.all{text.contains(it)}}
    fun amount(value:Long)=BigDecimal.valueOf(value,2).toPlainString()
    val categories=data.categories.associateBy{it.id};val accounts=data.accounts.associateBy{it.id}
    val result=mutableListOf<SearchHit>()
    val actions=listOf(
        SearchHit("action","accounts","الحسابات والمحافظ","الأرصدة وهدف الادخار"),
        SearchHit("action","budget","الميزانيات","ميزانية البيت والأصناف"),
        SearchHit("action","dues","الفواتير والأقساط","الالتزامات والسداد"),
        SearchHit("action","settings","المظهر والإعدادات","فاتح داكن أسود جلاس dark light glass خصوصية قفل"),
        SearchHit("action","categories","إدارة الأصناف","التصنيفات والأيقونات"),
        SearchHit("action","backup","نسخة احتياطية","حفظ نسخة مشفرة"),
        SearchHit("action","restore","استعادة البيانات","استيراد نسخة احتياطية"),
        SearchHit("action","export","التقارير والتصدير","PDF CSV"),
        SearchHit("action","analytics","التحليلات","الصرف والمقارنة"),
        SearchHit("action","plan","خطة التوفير","محاكاة وميزانية الدورة القادمة"))
    result+=actions.filter{matches(it.title+" "+it.detail)}
    data.accounts.filter{matches(it.name+" "+it.kind)}.take(20).forEach{result+=SearchHit("account",it.id,it.name,if(it.archived)"حساب مؤرشف"else"حساب أو محفظة")}
    data.categories.filter{matches(it.name)}.take(20).forEach{result+=SearchHit("category",it.id,it.name,if(it.income)"صنف دخل"else"صنف مصروف")}
    data.dues.asSequence().filter{matches("${it.title} ${categories[it.categoryId]?.name} ${it.date} ${amount(it.amount)}")}
        .sortedBy{it.date}.take(30).forEach{result+=SearchHit("due",it.id,it.title,"${it.date} • ${amount(it.amount)} جنيه")}
    data.transactions.asSequence().filter{matches("${it.note} ${categories[it.categoryId]?.name.orEmpty()} ${accounts[it.accountId]?.name.orEmpty()} ${accounts[it.destinationId]?.name.orEmpty()} ${it.date} ${amount(it.amount)} ${typeLabel(it.type)}")}
        .sortedWith(compareByDescending<Transaction>{it.date}.thenByDescending{it.created}).take(50).forEach{
            result+=SearchHit("transaction",it.id,it.note.ifBlank{if(it.type==TxType.TRANSFER)"تحويل بين الحسابات"else categories[it.categoryId]?.name?:typeLabel(it.type)},"${it.date} • ${amount(it.amount)} جنيه • ${typeLabel(it.type)}")
        }
    return result
}
