@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.hesabbeitna.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

private val categoryIcons=listOf("cat" to "Meow","cart" to "مشتريات","car" to "مواصلات","bill" to "فواتير","health" to "صحة","book" to "تعليم","star" to "ترفيه","shirt" to "ملابس","home" to "بيت","wallet" to "محفظة")
private val categoryTints=listOf("green" to "أخضر","orange" to "برتقالي","blue" to "أزرق","purple" to "بنفسجي")
@Composable private fun categoryColor(tint:String):Color=when(tint){"orange"->MaterialTheme.colorScheme.secondary;"blue"->Color(0xFF87B6D8);"purple"->Color(0xFFBA9ACC);else->MaterialTheme.colorScheme.primary}

@Composable fun CategoryTile(label:String,icon:String,tint:String,chosen:Boolean=false,tag:String="",click:()->Unit) {
    val colors=MaterialTheme.colorScheme
    LiquidClickableSurface(onClick=click,modifier=Modifier.fillMaxWidth().heightIn(min=104.dp).testTag(tag)
        .semantics{selected=chosen;role=Role.RadioButton},chosen=chosen) {
        Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(shape=Brand.Input,color=categoryColor(tint).copy(alpha=.15f)) {
                Box(Modifier.size(44.dp),contentAlignment=Alignment.Center){ToolIcon(icon,colors.onSurface)}
            }
            Text(label,style=MaterialTheme.typography.labelLarge)
            Text(if(chosen)"مختار"else" ",style=MaterialTheme.typography.labelSmall,color=colors.primary)
        }
    }
}

@Composable fun CategoryChoice(categories:List<Category>,current:String,transactions:List<Transaction>,model:AppModel,select:(String)->Unit) {
    var expanded by rememberSaveable{mutableStateOf(false)}
    var creating by rememberSaveable{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")}
    var icon by rememberSaveable{mutableStateOf("wallet")}
    var tint by rememberSaveable{mutableStateOf("green")}
    var error by remember{mutableStateOf<String?>(null)}
    var saving by remember{mutableStateOf(false)}
    val appearance=LocalAppearance.current
    val selectedCategory=categories.firstOrNull{it.id==current}
    Surface(onClick={expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp).testTag("category-picker"),shape=Brand.Input,
        color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ToolIcon(selectedCategory?.let{appearance.icon(it)}?:"wallet")
            Column(Modifier.weight(1f)){Hint("التصنيف");Text(selectedCategory?.name?:"اختر صنفًا")};ToolIcon("down")
        }
    }
    if(expanded)ModalBottomSheet(onDismissRequest={expanded=false;creating=false},containerColor=Color.Transparent) {
        FrostedWindow()
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Mascot(48.dp);Column(Modifier.weight(1f)){ScreenTitle(if(creating)"صنف جديد"else"اختر الصنف");Hint("خطوة صغيرة، وحساب أوضح")}
                    IconButton(onClick={expanded=false;creating=false},modifier=Modifier.semantics{contentDescription="إغلاق اختيار الصنف"}){ToolIcon("close")}
                }
                if(!creating) {
                    val ordered=remember(categories,transactions){val counts=transactions.groupingBy{it.categoryId}.eachCount();categories.sortedByDescending{counts[it.id]?:0}}
                    LazyVerticalGrid(columns=GridCells.Adaptive(if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f)144.dp else 96.dp),modifier=Modifier.fillMaxWidth().heightIn(max=360.dp).testTag("category-grid"),
                        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        items(ordered,key={it.id}) {cat->CategoryTile(cat.name,appearance.icon(cat),appearance.tint(cat.id),cat.id==current,"category-${cat.id}") {select(cat.id);expanded=false}}
                        item {CategoryTile("إضافة صنف","plus","orange",tag="new-category") {creating=true;error=null}}
                    }
                } else Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Field(name,{name=it.take(50);error=null},"اسم الصنف")
                    Hint("اختر الأيقونة")
                    categoryIcons.chunked(3).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        row.forEach{(key,label)->Box(Modifier.weight(1f)){CategoryTile(label,key,tint,icon==key,"icon-$key"){icon=key}}}
                    }}
                    Hint("اختر اللون")
                    categoryTints.chunked(2).forEach {row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        row.forEach{(key,label)->FilterChip(selected=tint==key,onClick={tint=key},label={Text(label)},modifier=Modifier.weight(1f))}
                    }}
                    ErrorText(error)
                    PrimaryAction("حفظ الصنف",enabled=!saving,onClick={
                        val cleaned=name.trim();val income=categories.firstOrNull()?.income?:false
                        if(cleaned.isBlank())error="أدخل اسم الصنف"
                        else {
                            val id=newId();saving=true
                            model.change("تمت إضافة الصنف",onResult={success->saving=false;if(success){appearance.setCategoryStyle(id,icon,tint);select(id);expanded=false;creating=false;name=""}else error="تعذر حفظ الصنف"}) {data->
                                require(data.categories.none{it.income==income&&it.name==cleaned}){"التصنيف موجود"}
                                require(data.categories.size<500){"وصلت إلى الحد الأقصى للتصنيفات"}
                                data.copy(categories=data.categories+Category(id,cleaned,income))
                            }
                        }
                    })
                    TextButton(onClick={creating=false}){Text("الرجوع للأصناف")}
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}
