package com.github.sam_sudo.TrialAPKManager.security

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.sam_sudo.TrialAPKManager.view.ActivationScreen
import com.github.sam_sudo.TrialAPKManager.workers.TrialExpirationWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object TrialAPKManager {

    val TAG = "TrialAPKManager"
    private val fileName = "license.dat.txt"
    private val dirName = "MyAppLicense"  // subcarpeta en Documents para la licencia
    private val xorKey = "MiClaveSecreta" // clave para cifrado XOR (básico)
    private var trialStartTime: Long? = null

    private val _licenseStatus = MutableStateFlow<LicenseStatus>(LicenseStatus.TRIAL_VALID)

    @Composable
    fun start(
        context: Context,
        trialDurationMs: Long,
        validCodes: List<String>,
        showView: @Composable () -> Unit,
        activationCodeView: (@Composable (onCodeEntered: (String) -> Boolean) -> Unit)? = null,
    ) {
        Log.w(TAG, "trialapkmanager start")
        var isInitialized by rememberSaveable { mutableStateOf(false) }
        var isLoading by rememberSaveable { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            if (!isInitialized) {
                isInitialized = true
                _licenseStatus.value = getLicenseStatus(context, trialDurationMs) // 🔹 Asegurar que la UI no se componga antes
                initializeWork(context,trialDurationMs)
                isLoading = false
            }
        }

        val licenseStatus by _licenseStatus.collectAsState()

        if (isLoading) {
            // 🔹 Mientras se carga el estado real, mostramos un indicador de carga o pantalla en blanco
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            when (licenseStatus) {
                LicenseStatus.LICENSED, LicenseStatus.TRIAL_VALID -> showView()
                LicenseStatus.TRIAL_EXPIRED -> activationCodeView?.invoke { code ->
                    activateLicense(context, code, validCodes)
                } ?: ActivationScreen { code ->
                    activateLicense(context, code, validCodes)
                }
            }
        }
    }

    fun initializeWork(context: Context, trialDurationMs: Long) {
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(
                context.applicationContext,
                Configuration.Builder()
                    .setMinimumLoggingLevel(Log.DEBUG)
                    .build()
            )
            Log.d(TAG, "WorkManager manually initialized")
        }else{
            Log.d(TAG, "WorkManager was already initialized")
        }

        scheduleTrialExpiration(context, trialDurationMs)
    }

    fun scheduleTrialExpiration(context: Context, trialDurationMs: Long) {
        Log.d("TrialAPKManager", "scheduleTrialExpiration")

        try {

            if (_licenseStatus.value == LicenseStatus.LICENSED) {
                Log.i("TrialAPKManager", "scheduleTrialExpiration: LICENSED")
                return
            }

            val timeLeft = getTimeRemaining(trialDurationMs)
            if (timeLeft > 0) {
                Log.d("TrialAPKManager", "scheduleTrialExpiration: worker creating...")
                val workRequest = OneTimeWorkRequestBuilder<TrialExpirationWorker>()
                    .setInitialDelay(timeLeft, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("trial_duration" to trialDurationMs))
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "trial_expiration_work",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }

            Log.d(TAG, "scheduleTrialExpiration: Worker scheduled correctly")
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "scheduleTrialExpiration: WorkManager is not available", e)
        } catch (e: NoClassDefFoundError) {
            Log.e(TAG, "scheduleTrialExpiration: WorkManager class not found", e)
        } catch (e: Exception) {
            Log.e(TAG, "scheduleTrialExpiration: Unexpected error", e)
        }
    }


    private fun getTimeRemaining(trialDurationMs: Long): Long {
        val startTime = getTrialStartTime() ?: return 0L
        val now = System.currentTimeMillis()
        return (startTime + trialDurationMs) - now
    }

    fun updateLicenseStatus(newStatus: LicenseStatus) {
        if (_licenseStatus.value != newStatus) {
            Log.d("TrialAPKManager", "Updating license status to: $newStatus")
            _licenseStatus.value = newStatus
        }
    }


    // Obtiene el estado actual de la licencia (y crea el archivo inicial si no existe)
    fun getLicenseStatus(context: Context,trialDurationMs: Long): LicenseStatus {
        val existingFile = readLicenseFile(context)  // Intenta leer el archivo antes de crear uno nuevo
        if (existingFile != null) {
            val decoded = decrypt(existingFile)
            val parts = decoded.split("|")
            if (parts.size >= 2) {
                val isActivated = parts[0] == "1"
                trialStartTime = parts[1].toLongOrNull() ?: 0L // 🔹 Guardamos el tiempo en memoria
                if (isActivated) {
                    return LicenseStatus.LICENSED
                }
                val now = System.currentTimeMillis()
                return if (now - trialStartTime!! > trialDurationMs) {
                    LicenseStatus.TRIAL_EXPIRED
                } else {
                    LicenseStatus.TRIAL_VALID
                }
            }
        }
        // Si el archivo NO existe, entonces crearlo por primera vez
        if (existingFile == null) {
            createTrialFile(context)
        }
        return LicenseStatus.TRIAL_VALID
    }

    fun getTrialStartTime(): Long? = trialStartTime  // 🔹 Método para acceder al tiempo almacenado en memoria

    // Valida un código de activación; aquí usamos una lógica sencilla (ejemplo de código válido)
    fun validateActivationCode(code: String,validCodes: List<String>): Boolean {
        return code.uppercase() in validCodes
    }

    // Activa la licencia guardando el estado de activación en el archivo (si el código es válido)
    fun activateLicense(context: Context, code: String, validCodes: List<String>): Boolean {
        if (!validateActivationCode(code, validCodes)) return false

        val content = "1|${System.currentTimeMillis()}"
        writeLicenseFile(context, encrypt(content))

        _licenseStatus.value = LicenseStatus.LICENSED
        return true
    }

    // -- Métodos internos de lectura/escritura con Scoped Storage --

    // Lee el contenido (cifrado) del archivo de licencia, o null si no existe
    private fun readLicenseFile(context: Context): String? {
        try {
            // Intentar acceder via MediaStore (Scoped Storage) primero en Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Buscar el archivo por nombre en MediaStore (Documents)
                val uri = findLicenseUri(context)
                if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        return stream.bufferedReader().readText()
                    }
                }
            }
            // En Android 9 o anteriores (o si falló lo anterior), intentar ruta pública tradicional
            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(publicDir, "$dirName/$fileName")
            if (file.exists()) {
                return file.readText()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    // Crea el archivo de licencia inicial en modo prueba (no activado, fecha = ahora)
    private fun createTrialFile(context: Context) {
        trialStartTime  = System.currentTimeMillis()
        val content = "0|$trialStartTime "  // "0" indica no activado, separado de la marca de tiempo
        writeLicenseFile(context,encrypt(content))
    }

    // Escribe el contenido cifrado en el archivo de licencia (Scoped Storage)
    private fun writeLicenseFile(context: Context,encryptedContent: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Buscar si el archivo ya existe
                findLicenseUri(context)?.let { existingUri ->
                    context.contentResolver.delete(existingUri, null, null)  // 🔹 BORRAR ARCHIVO EXISTENTE
                }
                // Ahora crear el nuevo archivo
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/$dirName")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { output ->
                        output.write(encryptedContent.toByteArray())
                    }
                }
            } else {
                // Para versiones anteriores a Android 10
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val appDir = File(publicDir, dirName)
                if (!appDir.exists()) appDir.mkdirs()
                val file = File(appDir, fileName)
                if (file.exists()) file.delete()  // 🔹 BORRAR ARCHIVO EXISTENTE
                file.writeText(encryptedContent)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // Encuentra el URI del archivo de licencia en MediaStore (si existe)
    private fun findLicenseUri(context: Context): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fileName, "%$dirName%") // Buscar dentro de Documents/MyAppLicense
        val queryUri = MediaStore.Files.getContentUri("external")

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                return Uri.withAppendedPath(queryUri, "$id")
            }
        }
        return null
    }

    // -- Métodos de cifrado/desencriptado (XOR simple con Base64) --

    private fun encrypt(plainText: String): String {
        val xorBytes = xorWithKey(plainText.toByteArray(Charsets.UTF_8), xorKey.toByteArray(Charsets.UTF_8))
        // Codificar a Base64 para almacenar como texto
        return Base64.encodeToString(xorBytes, Base64.DEFAULT)
    }

    private fun decrypt(encodedText: String): String {
        return try {
            val encryptedBytes = Base64.decode(encodedText, Base64.DEFAULT)
            val decryptedBytes = xorWithKey(encryptedBytes, xorKey.toByteArray(Charsets.UTF_8))
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun xorWithKey(input: ByteArray, key: ByteArray): ByteArray {
        val output = ByteArray(input.size)
        for (i in input.indices) {
            output[i] = (input[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return output
    }
}