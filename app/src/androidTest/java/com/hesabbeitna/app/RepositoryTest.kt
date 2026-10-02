package com.hesabbeitna.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun encryptedPersistenceSurvivesRepositoryReopen() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val data=Household(accounts=listOf(Account("cash","النقد",opening=12_550)),
            prefs=Preferences(ready=true,trackingStart=today(),defaultAccount="cash"))
        Repository(context).save(data)
        assertEquals(data,Repository(context).load())
        val raw=context.getDatabasePath("house-v1.db").readBytes().decodeToString()
        assertFalse(raw.contains("النقد"))
    }

    @Test fun oldSchemaBackupImportsWithoutPlansAndUpgradesSafely() {
        val old=Household(schema=1,accounts=listOf(Account("old-cash","بيانات قديمة",opening=12345)),prefs=Preferences(ready=true,trackingStart=today(),defaultAccount="old-cash"))
        val payload=codec.parseToJsonElement(codec.encodeToString(old)).jsonObject
        val legacy=JsonObject(payload.filterKeys{it !in setOf("plans","templates")}).toString().encodeToByteArray()
        val password="legacy-test-password".toCharArray()
        val imported=Backup.decrypt(BackupCrypto.encrypt(legacy,password),password)
        assertEquals(old.copy(schema=2),imported);assertTrue(imported.plans.isEmpty());assertTrue(imported.templates.isEmpty())
        assertEquals(12345L,imported.balance(imported.accounts.single()));password.fill('\u0000')
    }
    @Test fun failedValidationDoesNotReplacePreviousSnapshot() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val repo=Repository(context)
        val good=Household(accounts=listOf(Account("cash","النقد")),prefs=Preferences(ready=true,trackingStart=today(),defaultAccount="cash"))
        repo.save(good)
        var failed=false
        try {repo.save(good.copy(accounts=emptyList()))} catch(_:Exception) {failed=true}
        assertTrue(failed);assertEquals(good,repo.load())
    }
}
