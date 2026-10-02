package com.hesabbeitna.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Run explicitly on either side of adb install -r, outside the regular UI suite. */
@RunWith(AndroidJUnit4::class)
class UpgradePersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun expected(): Household {
        val start = LocalDate.now().withDayOfMonth(1).toString()
        return Household(accounts = listOf(Account("update-cash", "بيانات التحديث", opening = 90000)),
            transactions = listOf(Transaction("update-tx", TxType.EXPENSE, 1234, today(), "update-cash", categoryId = "expense-0", created = 1)),
            plans = listOf(MonthlyPlan(start, 100000, 40000, 10000, 20000, 5000, start)),
            templates = listOf(QuickTemplate("update-template", "قالب التحديث", amount = 4321, accountId = "update-cash", categoryId = "expense-0", created = 1)),
            prefs = Preferences(ready = true, salaryDay = 1, trackingStart = start, defaultAccount = "update-cash")).validate()
    }
    @Test fun seedBeforeUpdate() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradeStage") == "seed")
        Repository(context).save(expected())
        context.getSharedPreferences("appearance", 0).edit().putString("theme", "DARK").putBoolean("glass", false).commit()
        assertEquals(expected(), Repository(context).load())
        assertEquals(6L, context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
    }
    @Test fun verifyAfterUpdate() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradeStage") == "verify")
        val loaded = Repository(context).load()
        assertEquals(expected(), loaded); assertEquals(88766L, loaded.balance(loaded.accounts.single()))
        assertEquals("DARK", context.getSharedPreferences("appearance", 0).getString("theme", ""))
        assertFalse(context.getSharedPreferences("appearance", 0).getBoolean("glass", true))
        assertEquals(7L, context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode)
    }
}
