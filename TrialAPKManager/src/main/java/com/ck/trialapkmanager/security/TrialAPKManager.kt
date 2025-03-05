package com.ck.trialapkmanager.security

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ck.trialapkmanager.view.ActivationScreen
import java.io.File
import java.io.IOException

object TrialAPKManager {

    private val fileName = "license.dat.txt"
    private val dirName = "MyAppLicense"  // subcarpeta en Documents para la licencia
    private val xorKey = "MiClaveSecreta" // clave para cifrado XOR (básico)

    @Composable
    fun start(
        context: Context,
        trialDurationMs: Long,
        validCodes: List<String>,
        showView: @Composable () -> Unit,
        activationCodeView: (@Composable (onCodeEntered: (String) -> Boolean) -> Unit)? = null,
    ){
        // Composición de la UI según el estado de la licencia
        var licenseStatus by  remember { mutableStateOf(getLicenseStatus (context,trialDurationMs)) }

        when (licenseStatus) {
            LicenseStatus.LICENSED, LicenseStatus.TRIAL_VALID -> {
                // Contenido principal de la app (licencia activa o dentro de prueba)
                showView()
            }
            LicenseStatus.TRIAL_EXPIRED -> {
                activationCodeView?.invoke { code ->
                    if (activateLicense(context, code, validCodes)) {
                        licenseStatus = LicenseStatus.LICENSED // ✅ Update UI state
                        true
                    } else {
                        false
                    }
                } ?: ActivationScreen { code ->
                    if (activateLicense(context, code, validCodes)) {
                        licenseStatus = LicenseStatus.LICENSED // ✅ Update UI state
                        true
                    } else {
                        false
                    }
                }
            }
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
                val startTime = parts[1].toLongOrNull() ?: 0L
                if (isActivated) {
                    return LicenseStatus.LICENSED
                }
                val now = System.currentTimeMillis()
                return if (now - startTime > trialDurationMs) {
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

    // Valida un código de activación; aquí usamos una lógica sencilla (ejemplo de código válido)
    fun validateActivationCode(code: String,validCodes: List<String>): Boolean {
        return code.uppercase() in validCodes
    }

    // Activa la licencia guardando el estado de activación en el archivo (si el código es válido)
    fun activateLicense(context: Context,code: String,validCodes: List<String>): Boolean {
        if (!validateActivationCode(code,validCodes)) return false
        // Leer fecha de inicio original (para no alterar el inicio de prueba)
        val currentData = decrypt(readLicenseFile(context) ?: "")
        val startTime = currentData.split("|").getOrNull(1) ?: "${System.currentTimeMillis()}"
        // Construir nuevo contenido marcado como activado
        val newContent = "1|$startTime"
        writeLicenseFile(context,encrypt(newContent))
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
        val startTime = System.currentTimeMillis()
        val content = "0|$startTime"  // "0" indica no activado, separado de la marca de tiempo
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