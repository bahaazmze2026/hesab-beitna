package com.hesabbeitna.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File

class AppModel(application: Application) : AndroidViewModel(application) {
    private val repository = Repository(application)
    private val mutation = Mutex()
    private val _data = MutableStateFlow<Household?>(null)
    val data = _data.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _loadFailure = MutableStateFlow<String?>(null)
    val loadFailure = _loadFailure.asStateFlow()
    private val _restorePreview = MutableStateFlow<Household?>(null)
    val restorePreview = _restorePreview.asStateFlow()
    init { reload() }
    fun message(text: String) { _message.value = text }
    fun clearMessage() { _message.value = null }
    fun reload() = viewModelScope.launch {
        try {
            _loadFailure.value = null
            val original = repository.load()
            val materialized = original.materialize().validate()
            if (materialized != original) repository.save(materialized)
            _data.value = materialized
        } catch (_: Exception) { _loadFailure.value = "تعذر فتح البيانات. لم تُحذف أو تُستبدل. أعد المحاولة، أو استخدم نسخة احتياطية سليمة." }
    }
    fun change(success: String = "تم الحفظ", onResult: (Boolean) -> Unit = {}, transform: (Household) -> Household) = viewModelScope.launch {
        mutation.withLock {
            val before = _data.value ?: return@withLock
            _busy.value = true
            try {
                val next = transform(before).materialize().validate()
                if (next != before) repository.save(next)
                _data.value = next
                message(success)
                onResult(true)
            } catch (e: IllegalArgumentException) { message(userError(e)); onResult(false) }
            catch (_: Exception) { message("تعذر الحفظ. لم تُغيّر البيانات؛ راجع مساحة الهاتف وأعد المحاولة."); onResult(false) }
            finally { _busy.value = false }
        }
    }
    fun saveTransaction(transaction: Transaction, fee: Long = 0, feeId: String = newId(), template: QuickTemplate? = null, onResult: (Boolean) -> Unit = {}) = change("Meow • تم حفظ العملية",onResult=onResult) {
        val txs = it.transactions.filterNot { old -> old.id == transaction.id } + transaction
        val withFees = if (fee > 0 && transaction.type == TxType.TRANSFER) {
            txs.filterNot { it.id == feeId } + Transaction(id = feeId, type = TxType.EXPENSE, amount = fee, date = transaction.date,
                accountId = transaction.accountId, categoryId = "expense-8", payment = transaction.payment,
                note = "رسوم تحويل ${transaction.id}")
        } else txs
        it.copy(schema=2,transactions = withFees,templates=if(template==null)it.templates else it.templates.filterNot{old->old.id==template.id}+template)
    }
    fun deleteTransaction(id: String,success:String="تم حذف العملية",onResult:(Boolean)->Unit={}) = change(success,onResult) { data ->
        require(data.transactions.none { it.originalId == id }) { "احذف الاستردادات المرتبطة أولًا، أو عدل المصروف" }
        data.copy(transactions = data.transactions.filterNot { it.id == id })
    }
    fun restoreTransaction(transaction:Transaction)=change("تم التراجع عن حذف العملية") {data->
        require(data.transactions.none{it.id==transaction.id}){"العملية موجودة بالفعل"}
        data.copy(transactions=data.transactions+transaction)
    }
    fun export(uri: Uri, kind: String, password: String, period: Finance.Period) = viewModelScope.launch {
        val snapshot = _data.value ?: return@launch
        _busy.value = true
        val chars = password.toCharArray()
        try {
            withContext(Dispatchers.IO) {
                val bytes = when (kind) {
                    "backup" -> Backup.encrypt(snapshot, chars)
                    "csv" -> Reports.csv(snapshot, period)
                    else -> Reports.pdf(snapshot, period)
                }
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
            }
            message("تم تصدير الملف")
        } catch (_: Exception) { message("تعذر تصدير الملف. تحقق من المكان المختار وكلمة المرور") }
        finally { chars.fill('\u0000'); _busy.value = false }
    }
    fun inspectBackup(uri: Uri, password: String) = viewModelScope.launch {
        _busy.value = true
        val chars = password.toCharArray()
        try {
            _restorePreview.value = withContext(Dispatchers.IO) {
                val bytes = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= Backup.maxSize)
                        output.write(buffer,0,n)
                    }
                    output.toByteArray()
                }
                Backup.decrypt(bytes,chars)
            }
        } catch (_: Exception) { message("لم تُستعد البيانات: كلمة المرور غير صحيحة، أو الملف تالف أو غير متوافق") }
        finally { chars.fill('\u0000'); _busy.value = false }
    }
    fun cancelRestore() { _restorePreview.value = null }
    fun confirmRestore() = viewModelScope.launch {
        mutation.withLock {
            val incoming = _restorePreview.value ?: return@withLock
            _busy.value = true
            try {
                val app = getApplication<Application>()
                // A device-key-encrypted household snapshot is retained before replacement.
                val current = _data.value
                if (current != null) {
                    withContext(Dispatchers.IO) {
                        val bytes = DeviceCipher.encrypt(codec.encodeToString(current).encodeToByteArray())
                        val file = File(app.filesDir,"pre-restore.vault")
                        val temp = File(app.filesDir,"pre-restore.tmp")
                        temp.writeBytes(bytes)
                        check(temp.renameTo(file))
                    }
                }
                val localLock = current?.prefs?.lock ?: false
                val next = incoming.copy(prefs = incoming.prefs.copy(lock = localLock)).materialize().validate()
                repository.save(next)
                _data.value = next; _restorePreview.value = null
                message("تمت الاستعادة؛ استُبدلت البيانات دون دمج. إعداد قفل هذا الهاتف بقي كما هو")
            } catch (_: Exception) { message("تعذرت الاستعادة. لم تُستبدل البيانات الحالية") }
            finally { _busy.value = false }
        }
    }
    fun undoRestore() = viewModelScope.launch {
        mutation.withLock {
            _busy.value = true
            try {
                val app = getApplication<Application>()
                val previous = withContext(Dispatchers.IO) {
                    codec.decodeFromString<Household>(DeviceCipher.decrypt(File(app.filesDir,"pre-restore.vault").readBytes()).decodeToString()).validate()
                }
                val next = previous.materialize().validate()
                repository.save(next); _data.value = next
                message("تم الرجوع إلى البيانات السابقة للاستعادة")
            } catch (_: Exception) { message("لا توجد نسخة أمان قابلة للاسترجاع على هذا الهاتف") }
            finally { _busy.value = false }
        }
    }
}
