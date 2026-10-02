package com.hesabbeitna.app

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HouseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("bills","تذكير الالتزامات",NotificationManager.IMPORTANCE_DEFAULT))
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("bill-reminder", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BillWorker>(1,TimeUnit.DAYS).build())
    }
}
class BillWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result {
        return try {
            val data = Repository(applicationContext).load().materialize()
            if (!data.prefs.ready) return Result.success()
            val pending = data.dues.count { data.remaining(it)>0 && !LocalDate.parse(it.date).isAfter(LocalDate.now().plusDays(3)) }
            if (pending == 0) return Result.success()
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
            val intent = PendingIntent.getActivity(applicationContext,0,Intent(applicationContext,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(applicationContext,"bills").setSmallIcon(R.drawable.notification_logo)
                .setContentTitle("Meow Budget").setContentText("لديك التزامات قريبة أو متأخرة؛ افتح التطبيق للمراجعة")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(intent).setAutoCancel(true).build()
            applicationContext.getSystemService(NotificationManager::class.java).notify(25,notification)
            Result.success()
        } catch (_: Exception) { Result.retry() }
    }
}
