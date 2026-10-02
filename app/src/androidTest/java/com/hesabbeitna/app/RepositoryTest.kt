package com.hesabbeitna.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
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
