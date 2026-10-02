package com.hesabbeitna.app

import android.content.Context
import androidx.compose.runtime.*

enum class ThemeMode(val title:String) { LIGHT("فاتح"), DARK("أسود"), SYSTEM("حسب الهاتف") }
fun resolveDark(mode:ThemeMode,systemDark:Boolean)=when(mode){ThemeMode.LIGHT->false;ThemeMode.DARK->true;ThemeMode.SYSTEM->systemDark}

/** Appearance stays device-local; changing it never rewrites the financial snapshot. */
class Appearance(context:Context) {
    private val prefs=context.getSharedPreferences("appearance",Context.MODE_PRIVATE)
    var mode by mutableStateOf(runCatching{ThemeMode.valueOf(prefs.getString("theme","SYSTEM")!!)}.getOrDefault(ThemeMode.SYSTEM))
        private set
    var glass by mutableStateOf(prefs.getBoolean("glass",true))
        private set
    private var revision by mutableIntStateOf(0)
    fun chooseMode(value:ThemeMode){mode=value;prefs.edit().putString("theme",value.name).apply()}
    fun chooseGlass(value:Boolean){glass=value;prefs.edit().putBoolean("glass",value).apply()}
    fun icon(category:Category):String {
        revision
        return prefs.getString("icon-${category.id}",null)?:when {
            category.income->"wallet"
            category.name.contains("طعام")||category.name.contains("مشتريات")->"cart"
            category.name.contains("صحة")->"health"
            category.name.contains("تعليم")->"book"
            category.name.contains("مواصلات")->"car"
            category.name.contains("ترفيه")->"star"
            category.name.contains("ملابس")->"shirt"
            category.name.contains("إيجار")->"home"
            category.name.contains("فواتير")||category.name.contains("أقساط")->"bill"
            else->"wallet"
        }
    }
    fun tint(id:String):String {revision;return prefs.getString("tint-$id","green")?:"green"}
    fun setCategoryStyle(id:String,icon:String,tint:String){prefs.edit().putString("icon-$id",icon).putString("tint-$id",tint).apply();revision++}
}
val LocalAppearance=staticCompositionLocalOf<Appearance>{error("Appearance is not provided")}
