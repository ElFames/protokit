## 📊 Matriz de Soporte

### Plataformas
| Plataforma | Estado |
| :--- | :--- | :--- |
| **Android** | ✅ Soportado |
| **JVM** | ✅ Soportado |
| **iOS** | ⚠️ No stream |
| **Web** | 🚧 Planeado |

### ✅ Soportado

- **Llamadas Unarias**: Soporte completo para el flujo de solicitud-respuesta de gRPC.
- **Tipos de Datos Proto3**: Incluyendo `string`, `int32`, `int64`, `bool`, `float`, `double`, `bytes`, mensajes anidados y campos `repeated`.
- **Generación de Código en Tiempo de Compilación**: Todo el código se genera durante la compilación, sin sobrecarga en tiempo de ejecución.

### ❌ No Soportado

- **gRPC Streaming**: No se admiten llamadas de streaming (cliente, servidor o bidireccional).
- **Interceptors**: No hay soporte para interceptores de cliente.
- **`oneof`, `map`, `any`**: Estos tipos de Protobuf no están implementados.
- **Reintentos Automáticos y Compresión**: Estas características deben ser manejadas manualmente.

---

## 💡 Decisiones de Diseño

### ¿Por qué no se usa la reflexión?

ProtoKit **evita deliberadamente la reflexión** para la serialización de Protobuf. Aunque la reflexión puede ofrecer más flexibilidad, introduce una sobrecarga de rendimiento significativa y aumenta el tamaño de la aplicación, lo cual es crítico en entornos móviles.

Al generar código de serialización explícito en tiempo de compilación, ProtoKit garantiza que la codificación y decodificación sea lo más rápida y eficiente posible. Este enfoque da como resultado un SDK de tiempo de ejecución más pequeño y un comportamiento más predecible.

---

## 🛠 Instalación

### 1. Aplicar el Plugin de Gradle
En tu archivo `build.gradle.kts`:

```kotlin
plugins {
    id("com.fames.protokit.plugin") version "0.1.0"
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

> **Nota:** La generación de código se activa automáticamente durante el proceso de `build`. También puedes invocarla manualmente con `./gradlew protokitGenerate`.

---

## 🚀 Quick Start

```kotlin
// 1. Configurar el cliente base
val client = ProtoClient(
    baseUrl = "https://api.tu-servicio.com"
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

### Mapeo de Respuestas y Metadatos

La clase `Response` de ProtoKit te permite transformar el resultado
Accede a los metadatos de gRPC (trailers) de forma sencilla.
Configura un timeout y headers por defecto o en cada llamada.


```kotlin
// Transforma el resultado a un modelo de dominio y accede a los trailers
suspend fun fetchAndMapUser() {
    val client = ProtoClient(
        baseUrl = "https://api.tu-servicio.com",
        defaultTimeoutMillis = 30_000,
        headers = mapOf("Authorization" to "Bearer $token")
    )
    val response = userService.getUser(GetUserRequest(id = 42))
    val domainUserResponse = response.map { userProto -> userProto.toDomain() }
    val trailers = domainUserResponse.getTrailers()
}

// Asumiendo que tienes una función de extensión para convertir el proto a tu modelo de dominio
data class DomainUser(val name: String)

fun User.toDomain(): DomainUser {
    return DomainUser(name = this.displayName)
}
```
