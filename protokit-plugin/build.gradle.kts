plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "com.fames.protokit.plugin"
version = "0.1.0"

gradlePlugin {
    website.set("https://github.com/ElFames/protokit")
    vcsUrl.set("https://github.com/ElFames/protokit")

    plugins {
        create("protokit") {
            id = "com.fames.protokit.plugin"
            implementationClass = "com.fames.protokit.plugin.ProtoKitPlugin"

            displayName = "ProtoKit Gradle Plugin"
            description = "Generate Kotlin gRPC clients from .proto files for Kotlin Multiplatform"
            tags.set(listOf("grpc", "protobuf", "kotlin", "kmp", "codegen"))
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")
}

kotlin {
    jvmToolchain(21)
}
