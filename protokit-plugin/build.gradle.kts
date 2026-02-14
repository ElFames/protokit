plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

group = "com.fames.protokit.plugin"
version = "0.1.0"

gradlePlugin {
    plugins {
        create("protokit") {
            id = "com.fames.protokit.plugin"
            implementationClass = "com.fames.protokit.plugin.ProtoKitPlugin"
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
