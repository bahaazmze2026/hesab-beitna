package com.hesabbeitna.app

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run after update acceptance with Pixel Launcher enabled; finance tests need no launcher. */
@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private fun screenshot(name: String) {
        assertNull("System ANR must not obscure icon verification", device.findObject(By.textContains("isn't responding")))
        val file = File(context.cacheDir, "qa-mew-$name.png")
        assertTrue(device.takeScreenshot(file))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HesabBeitnaQA")
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
    }
    @Test fun installedAdaptiveIconLauncherAndSettings() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("iconStage") == "verify")
        val mascot = (context.getDrawable(R.drawable.brand_cat) as BitmapDrawable).bitmap
        assertEquals("In-app character must use the supplied full-resolution image", 1254, mascot.width)
        assertEquals(1254, mascot.height)
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        assertTrue("Installed application must resolve to an adaptive icon", icon is AdaptiveIconDrawable)
        val foreground = (icon as AdaptiveIconDrawable).foreground
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        foreground.setBounds(0, 0, 432, 432); foreground.draw(Canvas(bitmap))
        var visible = 0
        for (y in 0 until 432) for (x in 0 until 432) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) {
                visible++
                assertTrue("Every foreground pixel must remain inside the 66 dp safe circle",
                    Math.hypot(x + .5 - 216, y + .5 - 216) <= 132)
            }
        }
        assertTrue("New character foreground must not be empty", visible > 20_000)
        bitmap.recycle()
        val shortcuts = context.getSystemService(android.content.pm.ShortcutManager::class.java)
        assertTrue("No independent static shortcut icons exist", shortcuts.manifestShortcuts.isEmpty())
        assertTrue("No independent dynamic shortcut icons exist", shortcuts.dynamicShortcuts.isEmpty())
        // Explicitly choose the test launcher after reenabling it; avoid a Home chooser.
        println(device.executeShellCommand("cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity"))
        println(device.executeShellCommand("am start -a android.intent.action.MAIN -c android.intent.category.HOME -n com.google.android.apps.nexuslauncher/.NexusLauncherActivity"))
        device.wait(Until.hasObject(By.pkg("com.google.android.apps.nexuslauncher")), 20_000)
        device.waitForIdle(5_000)
        screenshot("launcher-home")
        // Short shell-injected gesture avoids a hosted emulator interpreting slow
        // UiDevice pointer delivery as a wallpaper long-press/context menu.
        var opened = false
        for (attempt in 1..3) {
            device.executeShellCommand("input swipe ${device.displayWidth / 2} ${device.displayHeight * 85 / 100} ${device.displayWidth / 2} ${device.displayHeight * 25 / 100} 150")
            screenshot("drawer-gesture-$attempt")
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "drawer-$attempt.xml"))
            if (device.wait(Until.hasObject(By.desc("All apps")), 5_000)) { opened = true; break }
            device.pressBack(); device.waitForIdle(3_000)
        }
        screenshot("drawer-attempt")
        assertTrue("Launcher must show the actual app drawer", opened)
        assertTrue("Updated app must be listed by the launcher",
            device.wait(Until.hasObject(By.text("Meow Budget")), 20_000))
        screenshot("app-drawer")
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 20_000))
        assertTrue(device.wait(Until.hasObject(By.text("Meow Budget")), 20_000))
        device.waitForIdle(3_000); screenshot("app-info")
    }
}
