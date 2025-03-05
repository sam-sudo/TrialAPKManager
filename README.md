# TrialAPKManager

TrialAPKManager es una biblioteca para gestionar versiones de prueba y activación de licencias en aplicaciones Android. Permite configurar un período de prueba y activar la aplicación mediante códigos de activación predefinidos.

## Características

- Período de prueba configurable
- Gestión de licencias con códigos de activación
- Uso de almacenamiento Scoped Storage para mayor seguridad
- Cifrado simple con XOR y Base64
- Integración fácil con Jetpack Compose

## Instalación

### Paso 1: Agregar JitPack a tu archivo de configuración

Agrega el repositorio de JitPack en tu archivo `settings.gradle` (en la raíz de tu proyecto):

```gradle
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### Paso 2: Agregar la dependencia

```gradle
dependencies {
    implementation 'com.github.sam-sudo:TrialAPKManager:Tag'
}
```

## Uso

### Inicialización de la licencia

En tu pantalla principal, usa el método `start()` para gestionar el estado de la licencia:

```kotlin
TrialAPKManager.start(
    context = context,
    trialDurationMs = 7 * 24 * 60 * 60 * 1000L, // 7 días de prueba
    validCodes = listOf("ABC123", "DEF456"), // Lista de códigos de activación válidos
    showView = {
        MainScreen() // Pantalla principal de la aplicación
    },
    activationCodeView = { onCodeEntered ->
        ActivationScreen { code -> onCodeEntered(code) }
    }
)
```

### Validación de licencia

La biblioteca maneja automáticamente la validación de la licencia al leer el estado desde un archivo seguro en el almacenamiento. También permite la activación con un código válido.

## Métodos principales

### `getLicenseStatus(context: Context, trialDurationMs: Long): LicenseStatus`

Obtiene el estado actual de la licencia:

- `LICENSED`: Activado con código
- `TRIAL_VALID`: Período de prueba aún válido
- `TRIAL_EXPIRED`: Prueba expirada, requiere activación

### `activateLicense(context: Context, code: String, validCodes: List<String>): Boolean`

Activa la licencia si el código ingresado es válido.

### `validateActivationCode(code: String, validCodes: List<String>): Boolean`

Verifica si un código de activación es válido.

## Licencia

Este proyecto está disponible bajo la licencia MIT.

---

¡Gracias por usar `TrialAPKManager`! 🎉



