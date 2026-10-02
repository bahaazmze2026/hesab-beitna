package com.hesabbeitna.app

import android.view.WindowManager
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Only the isolated test installation is seeded. Production installs remain empty. */
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun arabicSetupAndExpenseCanBeSaved() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runBlocking { Repository(context).save(Household()) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithText("أهلًا في حساب بيتنا").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("ابدأ حساب بيتنا").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("＋ تسجيل عملية").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("＋ تسجيل عملية").performClick()
            compose.onNode(hasSetTextAction() and hasText("المبلغ — جنيه")).performTextInput("10")
            compose.onNodeWithText("حفظ").performScrollTo().performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("10.00",substring=true).fetchSemanticsNodes().isNotEmpty() }
            // Disable screenshot protection only in this test process, for a synthetic-data QA image.
            scenario.onActivity { it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
            compose.waitForIdle()
            val image = File(context.filesDir,"qa-dashboard.png")
            check(UiDevice.getInstance(instrumentation).takeScreenshot(image))
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,"qa-dashboard.png")
                put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/HesabBeitnaQA")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
            context.contentResolver.openOutputStream(uri)!!.use { output -> image.inputStream().use { it.copyTo(output) } }
            compose.onNodeWithText("العمليات").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("الطعام").fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
