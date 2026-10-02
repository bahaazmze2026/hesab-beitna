package com.hesabbeitna.app

import android.content.ContentValues
import android.provider.MediaStore
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AnalyticsUiTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun snapshot()=runBlocking{Repository(context).load()}
    private fun waitText(text:String){compose.waitUntil(30_000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
    private fun activate(tag:String){val node=compose.onNodeWithTag(tag);if(tag !in listOf("analysis-overview","analysis-spending","analysis-comparison","analysis-planning"))node.performScrollTo();node.performSemanticsAction(SemanticsActions.OnClick){it()}}
    private fun capture(name:String,scenario:ActivityScenario<MainActivity>) {
        compose.waitForIdle();val rendered=CountDownLatch(1)
        scenario.onActivity {activity->
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if(android.os.Build.VERSION.SDK_INT>=29)WindowInspector.getGlobalWindowViews().forEach{view->
                (view.layoutParams as? WindowManager.LayoutParams)?.let{attrs->attrs.flags=attrs.flags and WindowManager.LayoutParams.FLAG_SECURE.inv();(view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(view,attrs)}
                view.invalidate()
            }
            activity.window.decorView.postOnAnimation{activity.window.decorView.postOnAnimation{rendered.countDown()}}
        }
        check(rendered.await(3,TimeUnit.SECONDS));instrumentation.waitForIdleSync()
        val file=File(context.filesDir,"qa-analytics-$name.png");check(UiDevice.getInstance(instrumentation).takeScreenshot(file))
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,file.name);put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HesabBeitnaQA")}
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use{out->file.inputStream().use{it.copyTo(out)}}
    }
    @Test fun tabsEvidenceSimulationAndExplicitBudgetAdoption() {
        val current=Finance.period(LocalDate.now(),1);val next=Finance.period(current.end,1)
        val rows=mutableListOf<Transaction>()
        for(i in 1..3){val start=current.start.minusMonths(i.toLong()).toString()
            rows+=Transaction("salary-$i",TxType.INCOME,100_000,start,"cash",categoryId="salary")
            rows+=Transaction("food-$i",TxType.EXPENSE,20_000,start,"cash",categoryId="expense-0")
            rows+=Transaction("bills-$i",TxType.EXPENSE,10_000,start,"cash",categoryId="expense-1")
        }
        rows+=Transaction("now-food",TxType.EXPENSE,4000,today(),"cash",categoryId="expense-0",note="عملية الدليل في التحليل")
        rows+=Transaction("now-bills",TxType.EXPENSE,2000,today(),"cash",categoryId="expense-1")
        rows+=Transaction("now-refund",TxType.REFUND,1000,today(),"cash",categoryId="expense-0",originalId="now-food")
        rows+=Transaction("now-income",TxType.INCOME,100_000,today(),"cash",categoryId="salary")
        val rule=BillRule("next-rule","التزام الدورة القادمة",5000,"expense-1",next.start.plusDays(2).toString())
        val seed=Household(accounts=listOf(Account("cash","النقد")),transactions=rows,rules=listOf(rule),budgets=listOf(Budget(next.start.toString(),99_999)),prefs=Preferences(ready=true,salaryDay=1,trackingStart=current.start.minusMonths(3).toString(),defaultAccount="cash")).materialize().validate()
        runBlocking{Repository(context).save(seed)}
        context.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE).edit().putString("theme","LIGHT").commit()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitText("كل شيء أوضح");compose.onNodeWithTag("nav-analytics").performClick();waitText("تحليلات واضحة")
            compose.onAllNodesWithText("50.00",substring=true).onFirst().assertExists();capture("overview",scenario)
            activate("analysis-spending");activate("analysis-category-expense-0")
            waitText("تحليل الطعام");compose.onNodeWithText("عملية الدليل في التحليل").performScrollTo().assertExists();capture("category-detail",scenario)
            compose.onNodeWithText("إغلاق").performScrollTo().performClick()
            compose.onNodeWithTag("spending-trend").performScrollTo().performTouchInput{click(center)}
            compose.onNodeWithText("عمليات الفترة المختارة").performScrollTo().performClick();waitText("تفاصيل")
            compose.onNodeWithText("إغلاق").performScrollTo().performClick()
            activate("analysis-comparison");compose.onNodeWithText("مقارنة عادلة").assertExists();capture("comparison",scenario)
            activate("analysis-planning");compose.onNodeWithTag("saving-slider").performScrollTo().performTouchInput{click(center)}
            assertEquals(seed.transactions,snapshot().transactions);assertEquals(99_999L,snapshot().budget(next)!!.amount)
            capture("simulation",scenario)
            activate("review-cycle-plan");waitText("اعتماد ميزانية الدورة القادمة؟")
            compose.onNodeWithText("الرجوع للتعديل").performClick();assertEquals(99_999L,snapshot().budget(next)!!.amount)
            activate("review-cycle-plan");compose.onNodeWithText("اعتماد الميزانية").performClick()
            compose.waitUntil(30_000){snapshot().budget(next)?.amount==35_000L}
            assertEquals(seed.transactions,snapshot().transactions)
            assertEquals(seed.balance(seed.accounts.single()),snapshot().balance(snapshot().accounts.single()))
            scenario.recreate();waitText("تحليلات واضحة");activate("analysis-planning");capture("saved-plan",scenario)
        }
    }
    @Test fun refundOnlyAndEmptyHistoryStayReadableInDarkMode() {
        val current=Finance.period(LocalDate.now(),1);val prior=current.start.minusDays(1).toString()
        val rows=listOf(Transaction("old",TxType.EXPENSE,5000,prior,"cash",categoryId="expense-0"),Transaction("refund",TxType.REFUND,5000,today(),"cash",categoryId="expense-0",originalId="old"))
        val seed=Household(accounts=listOf(Account("cash","النقد")),transactions=rows,prefs=Preferences(ready=true,salaryDay=1,trackingStart=prior,defaultAccount="cash")).validate()
        runBlocking{Repository(context).save(seed)}
        context.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE).edit().putString("theme","DARK").commit()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitText("كل شيء أوضح");compose.onNodeWithTag("nav-analytics").performClick();waitText("تحليلات واضحة")
            compose.onAllNodesWithText("-50.00",substring=true).onFirst().assertExists();capture("dark-refund",scenario)
            activate("analysis-spending");compose.onNodeWithText("لا توجد بنود بصافي موجب لرسم التوزيع").assertExists()
            activate("analysis-planning");compose.onNodeWithText("تحتاج بندًا بصافي موجب لتجربة المحاكاة").assertExists()
            activate("review-cycle-plan");compose.onNodeWithText("تنبيه: أدخل مبالغ صحيحة في جميع حقول الخطة").assertExists()
            assertTrue(snapshot().budgets.isEmpty());assertEquals(rows,snapshot().transactions)
        }
    }
}
