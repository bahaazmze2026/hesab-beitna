package com.hesabbeitna.app

import android.content.ContentValues
import android.provider.MediaStore
import android.view.WindowManager
import android.os.SystemClock
import android.graphics.BitmapFactory
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
class PlanningUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun snapshot() = runBlocking { Repository(context).load() }
    private fun waitText(text: String) = compose.waitUntil(30_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun field(label: String, value: String) = compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(value)
    private fun tag(key: String) = compose.onNodeWithTag(key)
    private fun capture(name: String, scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE); it.window.decorView.invalidate() }
        val file = File(context.filesDir, "qa-meow-$name.png")
        var valid = false
        repeat(8) {
            if (!valid) {
                instrumentation.waitForIdleSync(); SystemClock.sleep(150)
                check(UiDevice.getInstance(instrumentation).takeScreenshot(file))
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val colors = mutableSetOf<Int>()
                for (y in 0 until bitmap.height step 40) for (x in 0 until bitmap.width step 40) colors += bitmap.getPixel(x, y)
                valid = colors.size > 8; bitmap.recycle()
            }
        }
        check(valid)
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, file.name); put(MediaStore.Images.Media.MIME_TYPE, "image/png"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HesabBeitnaQA") }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }
    @Test fun planPersistsTemplateReviewsCalendarPaysAndBackupRestoresAll() {
        val start = LocalDate.now().withDayOfMonth(1)
        val rule = BillRule("test-rule", "كهرباء التقويم", 5000, "expense-1", today())
        val seed = Household(accounts = listOf(Account("cash", "النقد", opening = 100_000)), rules = listOf(rule), prefs = Preferences(ready = true, salaryDay = 1, trackingStart = start.toString(), defaultAccount = "cash")).materialize().validate()
        runBlocking { Repository(context).save(seed) }
        context.getSharedPreferences("appearance", 0).edit().putString("theme", "LIGHT").putBoolean("glass", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitText("كل شيء أوضح"); tag("app-name").assertTextEquals("Meow Budget")
            tag("nav-plan").performClick(); tag("create-month-plan").performScrollTo().performClick()
            field("الدخل المتوقع — جنيه", "1000"); field("المصروفات اليومية — جنيه", "400"); field("حجز الالتزامات — جنيه", "50")
            field("هدف الادخار — جنيه", "200"); field("احتياطي الطوارئ — جنيه", "100")
            tag("review-month-plan").performScrollTo().performClick(); tag("confirm-month-plan").performClick()
            compose.waitUntil(30_000) { snapshot().plans.size == 1 }; assertTrue(snapshot().transactions.isEmpty())
            assertEquals(100_000L, snapshot().balance(snapshot().accounts.single()))
            scenario.recreate(); waitText("خطتك المحفوظة"); capture("plan-light", scenario)
            tag("plan-calendar").performClick(); waitText("التقويم المالي")
            tag("calendar-${today()}").performScrollTo().performClick(); waitText("كهرباء التقويم"); capture("calendar-light", scenario)
            compose.onNodeWithTag("calendar-pay-test-rule:${today()}").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}
            waitText("سداد كهرباء التقويم")
            compose.onNodeWithText("حفظ").performScrollTo().performClick()
            compose.waitUntil(30_000) { snapshot().transactions.size == 1 }
            assertEquals(5000L, snapshot().transactions.single().amount); assertEquals(95_000L, snapshot().balance(snapshot().accounts.single()))
            tag("nav-more").performClick(); tag("more-templates").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick){it()}; waitText("القوالب السريعة"); tag("add-template").performScrollTo().performClick()
            field("اسم القالب", "مواصلات سريعة"); field("مبلغ القالب — جنيه", "12.50")
            tag("save-template").performScrollTo().performClick()
            compose.waitUntil(30_000) { snapshot().templates.size == 1 }
            val template = snapshot().templates.single(); tag("use-template-${template.id}").performScrollTo().performClick()
            assertEquals(1, snapshot().transactions.size)
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).assertTextContains("12.50")
            field("المبلغ — جنيه", "15.00"); compose.onNodeWithText("حفظ").performScrollTo().performClick()
            compose.waitUntil(30_000) { snapshot().transactions.size == 2 }
            assertEquals(1500L, snapshot().transactions.last().amount); assertEquals(1250L, snapshot().templates.single().amount)
            val before = snapshot(); val password = "planning-test-password".toCharArray()
            assertEquals(before, Backup.decrypt(Backup.encrypt(before, password), password)); password.fill('\u0000')
            assertEquals(93_500L, before.balance(before.accounts.single()))
            context.getSharedPreferences("appearance", 0).edit().putString("theme", "DARK").commit()
            scenario.recreate(); waitText("القوالب السريعة"); capture("templates-black", scenario)
            tag("nav-plan").performClick(); tag("plan-progress").performClick(); waitText("المخطط مقابل الفعلي"); capture("progress-black", scenario)
        }
    }
}
