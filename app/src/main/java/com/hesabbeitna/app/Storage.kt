package com.hesabbeitna.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

val codec = Json { ignoreUnknownKeys = false; encodeDefaults = true }

@Entity(tableName = "encrypted_household_chunks")
data class Vault(@PrimaryKey val id: Int, val payload: ByteArray)
@Dao interface VaultDao {
    @Query("SELECT * FROM encrypted_household_chunks ORDER BY id") suspend fun read(): List<Vault>
    @Query("DELETE FROM encrypted_household_chunks") suspend fun clear()
    @Insert suspend fun insert(chunks: List<Vault>)
    @RoomTransaction suspend fun write(chunks: List<Vault>) { clear(); insert(chunks) }
}
@Database(entities = [Vault::class], version = 1, exportSchema = false)
abstract class HouseDatabase : RoomDatabase() { abstract fun vault(): VaultDao }

/** Entire household is an authenticated encrypted snapshot, committed as small SQLite chunks.
 * Referential validation occurs before every commit; no monetary metadata appears in plaintext. */
object DeviceCipher {
    private val keyAlias = "hesab-beitna-vault-v1"
    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
    fun encrypt(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(bytes)
    }
    fun decrypt(bytes: ByteArray): ByteArray {
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
    }
}
class Repository(context: Context) {
    private val database = Room.databaseBuilder(context, HouseDatabase::class.java, "house-v1.db").build()
    private val mutex = Mutex()
    suspend fun load(): Household = mutex.withLock { withContext(Dispatchers.IO) {
        val rows = database.vault().read()
        if(rows.isEmpty()) Household() else {
            require(rows.map { it.id } == rows.indices.toList())
            val bytes = ByteArrayOutputStream().also { output -> rows.forEach { output.write(it.payload) } }.toByteArray()
            codec.decodeFromString<Household>(DeviceCipher.decrypt(bytes).decodeToString()).validate()
        }
    } }
    suspend fun save(data: Household) = mutex.withLock { withContext(Dispatchers.IO) {
        data.validate()
        val encrypted = DeviceCipher.encrypt(codec.encodeToString(data).encodeToByteArray())
        require(encrypted.size <= Backup.maxSize) { "حجم البيانات يتجاوز 32 ميجابايت" }
        val chunks = (encrypted.indices step (256 * 1024)).mapIndexed { index, offset ->
            Vault(index, encrypted.copyOfRange(offset, minOf(offset + 256 * 1024, encrypted.size)))
        }
        database.vault().write(chunks)
    } }
}

object Backup {
    const val maxSize = BackupCrypto.MAX_SIZE
    fun encrypt(data: Household, password: CharArray): ByteArray {
        val plaintext = codec.encodeToString(data.validate()).encodeToByteArray()
        return try { BackupCrypto.encrypt(plaintext,password) } finally { plaintext.fill(0) }
    }
    fun decrypt(bytes: ByteArray, password: CharArray): Household {
        val plaintext = BackupCrypto.decrypt(bytes,password)
        return try { codec.decodeFromString<Household>(plaintext.decodeToString()).validate() } finally { plaintext.fill(0) }
    }
}
