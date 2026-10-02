package com.hesabbeitna.app

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.provider.MediaStore
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LiquidGlassPilotTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun snapshot()=runBlocking{Repository(context).load()}
    private fun waitText(text:String)=compose.waitUntil(30_000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}
    private fun tag(key:String)=compose.onNodeWithTag(key)
    private fun capture(name:String,scenario:ActivityScenario<MainActivity>,black:Boolean=false) {
        scenario.onActivity { activity ->
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if(android.os.Build.VERSION.SDK_INT>=29)WindowInspector.getGlobalWindowViews().forEach{view->
                (view.layoutParams as? WindowManager.LayoutParams)?.let{attrs->attrs.flags=attrs.flags and WindowManager.LayoutParams.FLAG_SECURE.inv();(view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).updateViewLayout(view,attrs)}
                view.invalidate()
            }
            activity.window.decorView.invalidate()
        }
        val file=File(context.filesDir,"qa-liquid-$name.png")
        var valid=false
        repeat(8){if(!valid){
            instrumentation.waitForIdleSync();SystemClock.sleep(150)
            check(UiDevice.getInstance(instrumentation).takeScreenshot(file))
            val bitmap=BitmapFactory.decodeFile(file.absolutePath)
            val colors=mutableSetOf<Int>()
            for(y in 0 until bitmap.height step 40)for(x in 0 until bitmap.width step 40)colors+=bitmap.getPixel(x,y)
            if(colors.size>8){if(black)assertEquals(Color.BLACK,bitmap.getPixel(2,bitmap.height/2));valid=true};bitmap.recycle()
        }}
        check(valid){"Screenshot did not contain rendered UI"}
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,file.name);put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HesabBeitnaQA")}
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use{out->file.inputStream().use{it.copyTo(out)}}
    }
    @Test fun pilotLightBlackEntryAndReducedEffectsPersistWithoutFinancialChanges() {
        val period=Finance.period(LocalDate.now(),1)
        val seed=Household(accounts=listOf(Account("cash","النقد",opening=100_000)),budgets=listOf(Budget(period.start.toString(),60_000)),
            prefs=Preferences(ready=true,salaryDay=1,trackingStart=period.start.toString(),defaultAccount="cash")).validate()
        runBlocking{Repository(context).save(seed)}
        context.getSharedPreferences("appearance",0).edit().putString("theme","LIGHT").putBoolean("glass",true).putBoolean("reduce-effects",false).commit()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitText("كل شيء أوضح");tag("liquid-home-hero").assertExists();capture("home-light",scenario)
            tag("quick-add").performClick();waitText("تضيف إيه؟");capture("quick-light",scenario)
            tag("add-expense").performClick();waitText("تسجيل عملية")
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextInput("25.50")
            UiDevice.getInstance(instrumentation).pressBack();compose.waitForIdle();capture("entry-light",scenario)
            compose.onNodeWithText("حفظ").performScrollTo().performClick()
            compose.waitUntil(30_000){snapshot().transactions.size==1}
            assertEquals(2550L,snapshot().transactions.single().amount);assertEquals(97_450L,snapshot().balance(snapshot().accounts.single()))
            tag("nav-more").performClick();tag("more-settings").performScrollTo().performClick();waitText("مظهر التطبيق")
            tag("theme-dark").performClick();tag("reduce-effects-toggle").performScrollTo().performClick()
            assertTrue(context.getSharedPreferences("appearance",0).getBoolean("reduce-effects",false))
            val before=snapshot();scenario.recreate();waitText("مظهر التطبيق")
            assertTrue(context.getSharedPreferences("appearance",0).getBoolean("reduce-effects",false));assertEquals(before,snapshot())
            tag("nav-home").performClick();waitText("كل شيء أوضح");capture("home-black-reduced",scenario,true)
            val ledgerBeforeNavigation=snapshot()
            tag("nav-transactions").performClick();compose.waitForIdle();capture("transactions-black",scenario,true)
            tag("nav-analytics").performClick();compose.waitUntil(30_000){compose.onAllNodesWithTag("analysis-overview").fetchSemanticsNodes().isNotEmpty()};tag("analysis-overview").assertExists();compose.waitForIdle();capture("analytics-black",scenario,true)
            tag("nav-plan").performClick();compose.waitForIdle();capture("planning-black",scenario,true)
            tag("nav-more").performClick();tag("more-accounts").performClick();compose.waitForIdle();capture("accounts-black",scenario,true)
            tag("nav-more").performClick();tag("more-settings").performScrollTo().performClick();waitText("مظهر التطبيق");capture("settings-black",scenario,true)
            assertEquals(ledgerBeforeNavigation,snapshot())
            tag("nav-home").performClick();waitText("كل شيء أوضح")
            tag("quick-add").performClick();tag("add-income").performClick();tag("type-income").assertIsSelected()
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextInput("100")
            UiDevice.getInstance(instrumentation).pressBack();compose.waitForIdle();capture("entry-black",scenario)
            compose.onNodeWithText("حفظ").performScrollTo().performClick()
            compose.waitUntil(30_000){snapshot().transactions.size==2};assertEquals(107_450L,snapshot().balance(snapshot().accounts.single()))
            tag("nav-more").performClick();tag("more-settings").performScrollTo().performClick();tag("glass-toggle").performScrollTo().performClick()
            assertFalse(context.getSharedPreferences("appearance",0).getBoolean("glass",true))
            tag("nav-home").performClick();waitText("كل شيء أوضح");capture("home-solid-fallback",scenario,true)
            assertEquals(2,snapshot().transactions.size);assertEquals(107_450L,snapshot().balance(snapshot().accounts.single()))
        }
    }
}
