plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "com.fames.protokit.plugin"
version = "0.1.1"

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
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.6")
    implementation("com.squareup:kotlinpoet:2.2.0")
    implementation("com.google.protobuf:protobuf-java-util:4.33.5")
}

kotlin {
    jvmToolchain(21)
}
