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
    compileOnly("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    implementation("com.squareup:kotlinpoet:1.17.0")
    // Align protobuf-java-util version with the one used by protoc in the gradle plugin
    implementation("com.google.protobuf:protobuf-java-util:3.22.3")
}

kotlin {
    jvmToolchain(21)
}
