package com.hesabbeitna.app

import android.content.ContentValues
import android.provider.MediaStore
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Same fixture/device and new instrumentation against both actual delivered APKs. */
@RunWith(AndroidJUnit4::class)
class UxReviewTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val stage get()=InstrumentationRegistry.getArguments().getString("uxStage")
    private fun waitText(text:String)=compose.waitUntil(30_000){compose.onAllNodesWithText(text,substring=true).fetchSemanticsNodes().isNotEmpty()}
    private fun click(tag:String)=compose.onNodeWithTag(tag).performClick()
    private fun fixture():Household {
        val start=LocalDate.now().withDayOfMonth(1).toString()
        return Household(accounts=listOf(Account("ux-cash","النقد",opening=200_000)),
            transactions=listOf(Transaction("ux-income",TxType.INCOME,150_000,today(),"ux-cash",categoryId="salary",created=1),
                Transaction("ux-food",TxType.EXPENSE,35_000,today(),"ux-cash",categoryId="expense-0",note="مشتريات البيت",created=2),
                Transaction("ux-bills",TxType.EXPENSE,15_000,today(),"ux-cash",categoryId="expense-1",note="الكهرباء",created=3)),
            budgets=listOf(Budget(start,200_000)),
            prefs=Preferences(ready=true,salaryDay=1,trackingStart=start,defaultAccount="ux-cash")).validate()
    }
    private fun seed() {
        runBlocking{Repository(context).save(fixture())}
        context.getSharedPreferences("appearance",0).edit().putString("theme","LIGHT").putBoolean("glass",true).putBoolean("reduce-effects",false).commit()
    }
    private fun capture(name:String,scenario:ActivityScenario<MainActivity>) {
        compose.waitForIdle()
        val frames=CountDownLatch(1)
        scenario.onActivity{activity->
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            WindowInspector.getGlobalWindowViews().forEach{view->
                (view.layoutParams as? WindowManager.LayoutParams)?.let{attrs->
                    attrs.flags=attrs.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    (view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager).updateViewLayout(view,attrs)
                };view.invalidate()
            }
            activity.window.decorView.postOnAnimation{activity.window.decorView.postOnAnimation{frames.countDown()}}
        }
        check(frames.await(5,TimeUnit.SECONDS));instrumentation.waitForIdleSync()
        val file=File(context.filesDir,"ux-$stage-$name.png")
        check(UiDevice.getInstance(instrumentation).takeScreenshot(file))
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,file.name);put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HesabBeitnaQA")}
        val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use{out->file.inputStream().use{it.copyTo(out)}}
    }
    @Test fun matchedScreens() {
        assumeTrue(stage in listOf("before","after"));seed()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            waitText("كل شيء أوضح");capture("home-light",scenario)
            click("nav-transactions");waitText("سجل العمليات");capture("transactions",scenario)
            click("nav-analytics");waitText("تحليلات واضحة");capture("analytics-light",scenario)
            click("nav-plan");waitText("خطة الشهر");capture("plan",scenario)
            click("nav-more");capture("more",scenario)
            click("more-accounts");waitText("حساباتي ومحافظي");capture("accounts",scenario)
            click("nav-more");click("more-budget");waitText("ميزانية الدورة");capture("budget",scenario)
            click("nav-more");click("more-dues");waitText("الفواتير والأقساط");capture("dues",scenario)
            click("nav-more");compose.onNodeWithTag("more-settings").performScrollTo().performClick();waitText("مظهر التطبيق");capture("settings",scenario)
            compose.onNodeWithTag("theme-dark").performClick()
            click("nav-home");waitText("كل شيء أوضح");capture("home-dark",scenario)
            click("nav-analytics");waitText("تحليلات واضحة");capture("analytics-dark",scenario)
            click("nav-home");if(stage=="before")compose.onNodeWithTag("expense-fab").performScrollTo().performClick() else click("quick-add");waitText("تسجيل عملية");capture("entry-dark",scenario)
            assertEquals(fixture(),runBlocking{Repository(context).load()})
        }
    }
    @Test fun smallViewport() {
        assumeTrue(stage=="small");seed()
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            try {
            waitText("كل شيء أوضح")
            scenario.onActivity{activity->assertTrue(activity.resources.configuration.screenWidthDp<=320);assertTrue(activity.resources.configuration.fontScale>=1.45f)}
            for(tag in listOf("nav-home","nav-transactions","nav-analytics","nav-plan","nav-more","quick-add","quick-options")) {
                compose.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            }
            val content=compose.onNodeWithTag("screen-content").fetchSemanticsNode().boundsInRoot
            val dock=compose.onNodeWithTag("quick-add").fetchSemanticsNode().boundsInRoot
            assertTrue("No dock overlaps scrollable screen",content.bottom<=dock.top+1)
            capture("home-large-font",scenario)
            click("quick-add");waitText("تسجيل عملية")
            compose.onNodeWithTag("type-expense").assertIsSelected()
            val amount=compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه"))
            amount.performClick()
            compose.waitUntil(10_000){WindowInspector.getGlobalWindowViews().any{ViewCompat.getRootWindowInsets(it)?.isVisible(WindowInsetsCompat.Type.ime())==true}}
            amount.performTextInput("43.21")
            compose.onNodeWithTag("save-transaction").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            capture("keyboard",scenario)
            amount.performImeAction();compose.waitForIdle();click("save-transaction")
            compose.waitUntil(30_000){runBlocking{Repository(context).load()}.transactions.size==4}
            assertEquals(4321L,runBlocking{Repository(context).load()}.transactions.last().amount)
            waitText("Meow • تم حفظ العملية")
            compose.waitUntil(10_000){compose.onAllNodesWithText("Meow • تم حفظ العملية").fetchSemanticsNodes().isEmpty()}
            click("nav-transactions");waitText("سجل العمليات");capture("transactions-large-font",scenario)
            click("nav-analytics");waitText("تحليلات واضحة")
            compose.onNodeWithTag("analysis-comparison").performScrollTo().performClick();capture("analytics-large-font",scenario)
            click("nav-more");capture("more-large-font",scenario)
            compose.onNodeWithTag("more-settings").performScrollTo().performClick();waitText("مظهر التطبيق");capture("settings-large-font",scenario)
            compose.onNodeWithTag("theme-dark").performScrollTo().performClick();click("nav-home");waitText("كل شيء أوضح")
            val layouts=mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("home-main-amount").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
            assertFalse("Main amount must not truncate at 150% font",layouts.single().didOverflowWidth)
            capture("home-dark-large-font",scenario)
            } catch(error:Throwable) {
                capture("failure",scenario)
                UiDevice.getInstance(instrumentation).dumpWindowHierarchy(File(context.getExternalFilesDir(null),"ux-small-failure.xml"))
                throw error
            }
        }
    }
}
