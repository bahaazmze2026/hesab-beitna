package com.hesabbeitna.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    private val model: AppModel by viewModels()
    private val authenticated = mutableStateOf(false)
    private var pendingPassword = ""
    private var pendingKind = ""
    private var pendingPeriod: Finance.Period? = null
    private val backupExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingPassword; pendingPassword = ""
        if (uri != null) pendingPeriod?.let { model.export(uri,"backup",password,it) }
    }
    private val csvExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) pendingPeriod?.let { model.export(uri,"csv","",it) }
    }
    private val pdfExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) pendingPeriod?.let { model.export(uri,"pdf","",it) }
    }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val password = pendingPassword; pendingPassword = ""
        if (uri != null && password.isNotBlank()) model.inspectBackup(uri,password)
        else if (uri != null) model.message("أعد اختيار النسخة وأدخل كلمة المرور؛ تم إخلاء كلمة المرور من الذاكرة")
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { accepted ->
        model.message(if (accepted) "تم تفعيل إذن التنبيهات" else "التنبيهات غير مفعلة؛ الالتزامات متاحة داخل التطبيق")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val appearance=remember { Appearance(this@MainActivity) }
            val dark=resolveDark(appearance.mode,isSystemInDarkTheme())
            SideEffect {
                val bar=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle=bar,navigationBarStyle=bar)
            }
            CompositionLocalProvider(LocalAppearance provides appearance) {
            HouseTheme(dark=dark) {
                HouseRoot(model, authenticated.value,
                    unlock = { authenticate() },
                    canLock = { BiometricManager.from(this).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS },
                    export = { kind,password,period ->
                        pendingKind=kind; pendingPassword=password; pendingPeriod=period
                        when(kind) { "backup" -> backupExport.launch("meow-budget-${today()}.hbb")
                            "csv" -> csvExport.launch("meow-budget-${period.start}.csv")
                            else -> pdfExport.launch("meow-budget-${period.start}.pdf") }
                    },
                    restore = { password -> pendingPassword=password; restore.launch(arrayOf("*/*")) },
                    notifications = { if(Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else model.message("يمكن التحكم في التنبيهات من إعدادات Android") }
                )
            }
        }
        }
    }
    private fun authenticators() = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    private fun authenticate() {
        val prompt = BiometricPrompt(this,ContextCompat.getMainExecutor(this),object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { authenticated.value=true }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { model.message("لم يتم فتح القفل: $errString") }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("فتح Meow Budget")
            .setSubtitle("استخدم البصمة أو قفل الهاتف").setAllowedAuthenticators(authenticators()).build())
    }
    override fun onStop() { authenticated.value=false; super.onStop() }
}
