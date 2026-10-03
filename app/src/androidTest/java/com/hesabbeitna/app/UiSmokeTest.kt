package com.hesabbeitna.app

import android.view.WindowManager
import android.view.inspector.WindowInspector
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

/** Synthetic financial data and screenshots belong only to the isolated debug test installation. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun waitText(text:String) {
        try {compose.waitUntil(30_000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}}
        catch(error:Throwable){
            val folder=context.getExternalFilesDir(null)!!
            UiDevice.getInstance(instrumentation).dumpWindowHierarchy(File(folder,"failure-hierarchy.xml"))
            UiDevice.getInstance(instrumentation).takeScreenshot(File(folder,"failure-screen.png"))
            throw error
        }
    }
    private fun screenshot(name:String,scenario:ActivityScenario<MainActivity>) {
        val frames=CountDownLatch(1)
        scenario.onActivity{
            it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if(android.os.Build.VERSION.SDK_INT>=29)WindowInspector.getGlobalWindowViews().forEach {view->
                val attrs=view.layoutParams as? WindowManager.LayoutParams
                if(attrs!=null&&attrs.flags and WindowManager.LayoutParams.FLAG_SECURE!=0) {
                    attrs.flags=attrs.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    (view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(view,attrs)
                }
                view.invalidate()
            }
            it.window.decorView.invalidate()
            it.window.decorView.postOnAnimation {it.window.decorView.postOnAnimation{frames.countDown()}}
        }
        check(frames.await(3,TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        compose.waitForIdle()
        val file=File(context.filesDir,"qa-$name.png")
        check(UiDevice.getInstance(instrumentation).takeScreenshot(file))
        if(android.os.Build.VERSION.SDK_INT<29) {file.copyTo(File(context.getExternalFilesDir(null),"qa-$name.png"),overwrite=true);return}
        val values=ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME,"qa-$name.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HesabBeitnaQA")
        }
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use{out->file.inputStream().use{it.copyTo(out)}}
    }
    private fun financialSnapshot()=runBlocking{Repository(context).load()}
    @Test fun setupExpenseEditSearchFilterAndAnalytics() {
        runBlocking{Repository(context).save(Household())}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitText("أهلًا في Meow Budget");screenshot("setup",scenario)
            compose.onNodeWithText("ابدأ Meow Budget").performScrollTo().performClick()
            waitText("كل شيء أوضح");compose.onNodeWithTag("app-name").assertIsDisplayed();screenshot("home-empty",scenario)
            compose.onNodeWithTag("expense-fab").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()}
            waitText("تسجيل عملية")
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextInput("0")
            compose.onNodeWithText("حفظ").performClick()
            compose.onNodeWithText("المبلغ أكبر من صفر").assertExists()
            assertTrue(financialSnapshot().transactions.isEmpty())
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextReplacement("45.50")
            compose.onNodeWithText("التاريخ وطريقة الدفع والملاحظة").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("ملاحظة اختيارية")).performTextInput("اختبار شراء احتياجات البيت")
            screenshot("expense-keyboard",scenario)
            compose.onNodeWithText("حفظ").performClick()
            compose.waitUntil(30_000){financialSnapshot().transactions.size==1}
            assertEquals(4550L,financialSnapshot().transactions.single().amount)
            screenshot("dashboard",scenario)
            compose.onNodeWithTag("nav-transactions").performClick()
            waitText("سجل العمليات")
            compose.onNode(hasSetTextAction() and hasText("ابحث في الملاحظة أو البند أو الحساب")).performTextInput("احتياجات")
            compose.onNodeWithText("تعديل").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()}
            waitText("تعديل العملية")
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextReplacement("70.50")
            compose.onNodeWithText("حفظ").performClick()
            compose.waitUntil(30_000){financialSnapshot().transactions.single().amount==7050L}
            assertEquals(1,financialSnapshot().transactions.size)
            screenshot("transactions",scenario)
            compose.onNode(hasSetTextAction() and hasText("ابحث في الملاحظة أو البند أو الحساب")).performScrollTo().performTextReplacement("لا يوجد هذا النص")
            compose.onNodeWithText("لا توجد عمليات مسجلة في هذه الفترة").performScrollTo().assertExists()
            screenshot("filtered-empty",scenario)
            compose.onNode(hasSetTextAction() and hasText("ابحث في الملاحظة أو البند أو الحساب")).performTextReplacement("")
            compose.onNodeWithText("التصفية بالتاريخ والتصنيف والحساب").performScrollTo().performClick()
            compose.onNode(hasSetTextAction() and hasText("من YYYY-MM-DD")).performTextReplacement(today())
            compose.onNode(hasSetTextAction() and hasText("حتى YYYY-MM-DD")).performTextReplacement(today())
            compose.onNodeWithText("1 عملية تطابق الاختيار").performScrollTo().assertExists()
            compose.onNodeWithTag("nav-analytics").performClick();waitText("تحليلات واضحة")
            compose.onAllNodesWithText("70.50",substring=true).onFirst().assertExists();screenshot("analytics",scenario)
            compose.onNodeWithTag("nav-more").performClick();compose.onNodeWithTag("more-settings").performScrollTo().performClick();waitText("الإعدادات والخصوصية");screenshot("settings",scenario)
            compose.onNodeWithText("الحسابات وهدف الادخار").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()};waitText("حساباتي ومحافظي");screenshot("accounts",scenario)
            UiDevice.getInstance(instrumentation).pressBack();waitText("كل شيء أوضح")
            compose.onNodeWithTag("open-budget").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()};waitText("ميزانية الدورة");screenshot("budget",scenario)
            UiDevice.getInstance(instrumentation).pressBack();waitText("كل شيء أوضح")
            compose.onNodeWithText("الفواتير والأقساط").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()};waitText("الفواتير والأقساط");screenshot("dues",scenario)
            assertEquals(-7050L,financialSnapshot().balance(financialSnapshot().accounts.single()))
        }
    }
    @Test fun appearancePersistsAndIconCategoryCanBeCreated() {
        val account=Account("cash","النقد")
        runBlocking{Repository(context).save(Household(accounts=listOf(account),prefs=Preferences(ready=true,trackingStart=today(),defaultAccount="cash")))}
        context.getSharedPreferences("appearance",android.content.Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            waitText("كل شيء أوضح")
            compose.onNodeWithTag("nav-more").performClick();compose.onNodeWithTag("more-settings").performScrollTo().performClick()
            compose.onNodeWithTag("theme-light").performScrollTo().performClick().assertIsSelected()
            screenshot("light-settings",scenario)
            scenario.recreate();waitText("مظهر التطبيق")
            compose.onNodeWithTag("theme-light").assertIsSelected()
            compose.onNodeWithTag("theme-dark").performScrollTo().performClick().assertIsSelected()
            screenshot("dark-settings",scenario)
            scenario.recreate();waitText("مظهر التطبيق")
            compose.onNodeWithTag("theme-dark").assertIsSelected()
            compose.onNodeWithTag("theme-system").performScrollTo().performClick().assertIsSelected()
            compose.onNodeWithTag("theme-light").performScrollTo().performClick()
            compose.onNodeWithTag("glass-toggle").performScrollTo().performClick().assertIsOff()
            scenario.recreate();waitText("مظهر التطبيق")
            compose.onNodeWithTag("glass-toggle").performScrollTo().assertIsOff().performClick().assertIsOn()
            compose.onNodeWithTag("nav-home").performClick()
            compose.onNodeWithTag("expense-fab").performScrollTo().performSemanticsAction(SemanticsActions.OnClick){it()}
            waitText("تسجيل عملية")
            compose.onNodeWithTag("category-picker").performScrollTo().performClick()
            compose.onNodeWithTag("category-grid").assertIsDisplayed();screenshot("icon-categories",scenario)
            compose.onNodeWithTag("category-grid").performScrollToNode(hasTestTag("new-category"))
            compose.onNodeWithTag("new-category").performClick()
            compose.onNode(hasSetTextAction() and hasText("اسم الصنف")).performTextInput("احتياجات خاصة")
            compose.onNodeWithTag("icon-cart").performScrollTo().performClick()
            compose.onNodeWithText("حفظ الصنف").performScrollTo().performClick()
            compose.waitUntil(30_000){financialSnapshot().categories.any{it.name=="احتياجات خاصة"}}
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performScrollTo().performTextInput("12.50")
            compose.onNodeWithText("حفظ",useUnmergedTree=false).performClick()
            compose.waitUntil(30_000){financialSnapshot().transactions.size==1}
            val snapshot=financialSnapshot()
            assertEquals("احتياجات خاصة",snapshot.category(snapshot.transactions.single().categoryId))
            assertEquals(1250L,snapshot.transactions.single().amount)
            assertEquals(-1250L,snapshot.balance(snapshot.accounts.single()))
        }
    }
    @Test fun largeAmountsLongArabicAndActivityRecreation() {
        val account=Account("cash","حساب مصروفات البيت والاحتياجات الشهرية الطويل",opening=100_000_000_000L)
        val tx=Transaction("big",TxType.EXPENSE,99_999_999_999L,accountId="cash",categoryId="expense-0",note="ملاحظة عربية طويلة لاختبار الالتفاف ووضوح الأرقام دون قص")
        runBlocking{Repository(context).save(Household(accounts=listOf(account),transactions=listOf(tx),prefs=Preferences(ready=true,trackingStart=today(),defaultAccount="cash")))}
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitText("كل شيء أوضح");screenshot("large-home",scenario)
            compose.onNodeWithTag("nav-transactions").performClick();waitText("سجل العمليات");screenshot("large-transactions",scenario)
            compose.onNodeWithTag("quick-add").performClick()
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextInput("123.45")
            scenario.recreate()
            // The draft and open form survive recreation; no draft becomes a financial record.
            waitText("تسجيل عملية")
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).assertTextContains("123.45")
            compose.onNodeWithContentDescription("إغلاق").performClick()
            waitText("سجل العمليات");assertEquals(99_999_999_999L,financialSnapshot().transactions.single().amount)
        }
    }
}
