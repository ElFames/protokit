plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "com.fames.protokit.codegen"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

application {
    mainClass.set("com.fames.protokit.codegen.ProtoKitCodegen")
}

kotlin {
    jvmToolchain(21)
}

tasks.register<Jar>("codegenJar") {
    group = "build"
    description = "Build an executable jar for ProtoKit codegen"

    archiveBaseName.set("protokit-codegen")
    archiveVersion.set(project.version.toString())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }

    from(sourceSets.main.get().output)

    // Incluir todas las dependencias dentro del JAR (fatJar)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}