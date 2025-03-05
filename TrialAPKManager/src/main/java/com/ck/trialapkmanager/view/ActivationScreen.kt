package com.ck.trialapkmanager.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ActivationScreen(onCodeEntered: (String) -> Boolean) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Periodo de prueba expirado. Ingrese código de activación:", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it
                if (error) error = false  // limpiar error al cambiar texto
            },
            label = { Text("Código de activación") }
        )
        if (error) {
            Text("Código inválido, inténtelo de nuevo", color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            // Llamar al callback con el código ingresado
            val success = onCodeEntered(code)
            if (!success) {
                error = true // mostrar mensaje de error si falla
            }
        }) {
            Text("Activar")
        }
    }
}
