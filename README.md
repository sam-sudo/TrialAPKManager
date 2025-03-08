# TrialAPKManager

TrialAPKManager es una biblioteca para gestionar versiones de prueba y activación de licencias en aplicaciones Android. Permite configurar un período de prueba y activar la aplicación mediante códigos de activación predefinidos.

Esta librería permite implementar un sistema de límite de tiempo en una aplicación Android, ideal para generar versiones de prueba de cualquier aplicación. Su funcionamiento es simple y efectivo: establece un tiempo de uso limitado para la aplicación y, al alcanzar este límite, muestra automáticamente una pantalla solicitando la introducción de un código de activación. El acceso a la aplicación quedará bloqueado hasta que el código correcto sea ingresado, garantizando que solo los usuarios con el código puedan continuar usando la app después de este periodo.

## Características
- Establece un límite de tiempo para el uso de la aplicación.
- Muestra una pantalla de introducción de código cuando se alcanza el tiempo límite.
- Evita el acceso a la aplicación hasta que se ingrese el código correcto.
- Ideal para distribuir versiones de prueba de aplicaciones de forma controlada.
- Funciona de manera eficiente y sencilla, sin comprometer la funcionalidad de la app.

## Usos
Esta librería es perfecta para desarrolladores que desean ofrecer un periodo de prueba limitado de su aplicación. Puedes utilizarla para:
- Realizar pruebas con usuarios o clientes potenciales.
- Ofrecer versiones de prueba de una app sin comprometer la funcionalidad completa.
- Garantizar un control sobre el tiempo de uso, permitiendo evaluar la app de manera controlada y sin limitaciones adicionales.

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
    activationCodeView = { onCodeEntered -> //Este parámetro puede omitirse y la libreria usará su propio composable
        ActivationScreen { code -> onCodeEntered(code) }
    }
)
```

### Validación de licencia

La biblioteca maneja automáticamente la validación de la licencia al leer el estado desde un archivo seguro en el almacenamiento. También permite la activación con un código válido.

## Licencia

Este proyecto está disponible bajo la licencia MIT.

---

¡Gracias por usar `TrialAPKManager`! 🎉
