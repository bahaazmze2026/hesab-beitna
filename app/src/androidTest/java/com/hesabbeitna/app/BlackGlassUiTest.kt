package com.hesabbeitna.app

import android.content.ContentValues
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import android.provider.MediaStore
import android.view.WindowManager
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
class BlackGlassUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun snapshot()=runBlocking{Repository(context).load()}
    private fun waitText(text:String)=compose.waitUntil(30_000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}
    private fun click(tag:String)=compose.onNodeWithTag(tag).performClick()
    private fun captureBlack(scenario:ActivityScenario<MainActivity>) {
        scenario.onActivity{it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);it.window.decorView.invalidate()}
        val file=File(context.filesDir,"qa-black-glass-home.png")
        var valid=false
        for(attempt in 1..8){
            instrumentation.waitForIdleSync();SystemClock.sleep(150)
            check(UiDevice.getInstance(instrumentation).takeScreenshot(file))
            val bitmap=BitmapFactory.decodeFile(file.absolutePath)
            val colors=mutableSetOf<Int>()
            for(y in 0 until bitmap.height step 40)for(x in 0 until bitmap.width step 40)colors+=bitmap.getPixel(x,y)
            if(colors.size>8){assertEquals("Black background must be #000000",Color.BLACK,bitmap.getPixel(2,bitmap.height/2));valid=true;bitmap.recycle();break}
            bitmap.recycle()
        }
        check(valid){"Screenshot did not capture rendered UI"}
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,file.name);put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HesabBeitnaQA")}
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use{output->file.inputStream().use{it.copyTo(output)}}
    }
    @Test fun blackNavigationQuickTypesHistoricSearchAndMore() {
        val oldDate=LocalDate.now().minusMonths(2).toString()
        val old=Transaction("historic",TxType.EXPENSE,12550,oldDate,"cash",categoryId="expense-1",note="إيصال كهرباء قديم")
        val seed=Household(accounts=listOf(Account("cash","النقد",opening=100_000),Account("wallet","المحفظة")),transactions=listOf(old),
            prefs=Preferences(ready=true,salaryDay=1,trackingStart=oldDate,defaultAccount="cash")).validate()
        runBlocking{Repository(context).save(seed)}
        context.getSharedPreferences("appearance",0).edit().putString("theme","DARK").putBoolean("glass",true).commit()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitText("كل شيء أوضح");captureBlack(scenario)
            click("quick-options");for(tag in listOf("add-expense","add-income","add-transfer","add-payment"))compose.onNodeWithTag(tag).assertIsDisplayed()
            click("add-income");compose.onNodeWithTag("type-income").assertIsSelected()
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextInput("50.00")
            compose.onNodeWithText("حفظ").performClick()
            compose.waitUntil(30_000){snapshot().transactions.size==2}
            assertEquals(TxType.INCOME,snapshot().transactions.last().type)
            click("quick-options");click("add-transfer");compose.onNodeWithTag("type-transfer").assertIsSelected()
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextInput("10.00")
            compose.onNodeWithText("حفظ").performClick()
            compose.waitUntil(30_000){snapshot().transactions.size==3}
            assertEquals(1000L,snapshot().balance(snapshot().accounts.first{it.id=="wallet"}))
            click("nav-more");compose.onNodeWithTag("more-accounts").assertExists();compose.onNodeWithTag("more-settings").performScrollTo().performClick()
            waitText("مظهر التطبيق");compose.onNodeWithTag("theme-dark").assertIsSelected()
            click("nav-plan");waitText("Meow معاك في التخطيط")
            compose.onNodeWithTag("plan-calendar").assertIsDisplayed().performClick()
            compose.onNodeWithTag("plan-progress").assertIsDisplayed().performClick()
            click("global-search");compose.onNodeWithTag("search-input").performTextInput("كهرباء")
            waitText("إيصال كهرباء قديم");compose.onNodeWithText("إيصال كهرباء قديم").performScrollTo().performClick()
            waitText("تعديل هذه العملية");compose.onNodeWithText("تعديل هذه العملية").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("ملاحظة اختيارية")).performScrollTo().performTextReplacement("إيصال كهرباء تم مراجعته")
            compose.onNodeWithText("حفظ").performClick()
            compose.waitUntil(30_000){snapshot().transactions.first{it.id=="historic"}.note.contains("مراجعته")}
            assertEquals(3,snapshot().transactions.size)
            assertEquals(92_450L,snapshot().accounts.sumOf{snapshot().balance(it)})
        }
    }
    @Test fun deletionUndoRestoresTheSameRecordAndBalance() {
        val tx=Transaction("undo",TxType.EXPENSE,2000,today(),"cash",categoryId="expense-0")
        val seed=Household(accounts=listOf(Account("cash","النقد",opening=10_000)),transactions=listOf(tx),prefs=Preferences(ready=true,trackingStart=today(),defaultAccount="cash")).validate()
        runBlocking{Repository(context).save(seed)}
        ActivityScenario.launch(MainActivity::class.java).use{
            waitText("كل شيء أوضح");click("nav-transactions")
            compose.onNodeWithText("حذف").performScrollTo().performClick();waitText("حذف العملية؟")
            compose.onAllNodesWithText("حذف").onLast().performClick()
            compose.waitUntil(30_000){snapshot().transactions.isEmpty()}
            waitText("تراجع");compose.onNodeWithText("تراجع").performClick()
            compose.waitUntil(30_000){snapshot().transactions.size==1}
            assertEquals(tx,snapshot().transactions.single());assertEquals(8000L,snapshot().balance(snapshot().accounts.single()))
        }
    }
}
