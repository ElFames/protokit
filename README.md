## 📊 Matriz de Soporte

### Plataformas
| Plataforma | Estado |
| :--- | :--- | :--- |
| **Android** | ✅ Soportado |
| **JVM** | ✅ Soportado |
| **iOS** | ⚠️ No stream |
| **Web** | 🚧 Planeado |

### Protobuf
| Protobuf   | Estado        |
|:-----------|:--------------|
| **proto3** | ✅ Soportado   |
| **proto2** | 🚧 Planeado   |
| **proto1** | ⚠️ No Soporte |

---

## 🛠 Instalación

### 1. Aplicar el Plugin de Gradle
En tu archivo `build.gradle.kts` a nivel de módulo:

```kotlin
plugins {
    id("com.fames.protokit") version "0.1.0"
}
```

### 2. Configurar la Dependencia del SDK
Añade la librería al set de fuentes comunes de tu proyecto Multiplatform:

```kotlin
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.fames.protokit:protokit-sdk:0.1.0")
            }
        }
    }
}
```

---

## ⚙️ Configuración del Plugin

El plugin de ProtoKit busca por defecto los archivos `.proto` en `src/commonMain/protos`.

> **Nota:** La generación de código se activa automáticamente durante el proceso de `build`. También puedes invocarla manualmente con `./gradlew generateProtoKit`.

---

## 💡 Quick Start

```kotlin
// 1. Configurar el cliente base, opcional con headers por defecto y default 15s timeout configurable
val client = ProtoClient(
    baseUrl = "https://api.tu-servicio.com",
    headers = mapOf("Authorization" to "Bearer ${token}")
)

// 2. Instanciar el servicio (generado automáticamente)
val userService = UserServiceClient(client)

// 3. Realizar la llamada de forma asíncrona
suspend fun fetchUser() {
    val response = userService.getUser(GetUserRequest(id = 42))

    response
        .onSuccess { user ->
            println("Usuario recibido: ${user.displayName}")
        }
        .onFailure { error ->
            println("Error gRPC: ${error.status} - ${error.message}")
        }
}
```

## ⚖️ Licencia

Este proyecto está bajo la Licencia [Tu Licencia - ej: MIT]. Consulta el archivo `LICENSE` para más detalles.