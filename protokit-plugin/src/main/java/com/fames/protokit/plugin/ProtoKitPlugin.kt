package com.fames.protokit.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ProtoKitPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val protoDir = project.layout.projectDirectory.dir("src/commonMain/protos")
        val outputDir = project.layout.buildDirectory.dir("generated/protokit")
        val codegenJar = project.rootProject.file("protokit-codegen/build/libs/protokit-codegen-0.1.0.jar")

        val generateProtoKit = project.tasks.register<JavaExec>("protokitGenerate") {
            group = "protokit"
            description = "Generate Kotlin sources from .proto files using ProtoKit"

            mainClass.set("com.fames.protokit.codegen.ProtoKitCodegen")

            classpath = project.files(codegenJar)

            args(
                protoDir.asFile.absolutePath,
                outputDir.get().asFile.absolutePath
            )

            doFirst {
                println("[ProtoKit] Running codegen")
                println("[ProtoKit] Proto dir : ${protoDir.asFile}")
                println("[ProtoKit] Output dir: ${outputDir.get().asFile}")
            }
        }

        project.gradle.includedBuild("protokit-codegen")?.task(":codegenJar")?.let { codegenJarTask ->
            generateProtoKit.configure {
                dependsOn(codegenJarTask)
            }
        }

        // 🔗 Conectar al KMP
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {

            val kotlin = project.extensions
                .getByType<KotlinMultiplatformExtension>()

            kotlin.sourceSets
                .getByName("commonMain")
                .kotlin.srcDir(outputDir)

            project.tasks.matching {
                it.name.startsWith("compile")
            }.configureEach {
                dependsOn(generateProtoKit)
            }
        }
    }
}
