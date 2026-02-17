package com.fames.protokit.plugin

import com.fames.protokit.plugin.codegen.ProtoKitCodegen
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ProtoKitPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val protoDir = project.layout.projectDirectory.dir("src/commonMain/protos")
        val outputDir = project.layout.buildDirectory.dir("generated/protokit")

        val generateProtoKit = project.tasks.register("protokitGenerate") {
            group = "protokit"
            description = "Generate Kotlin sources from .proto files using ProtoKit"

            inputs.dir(protoDir)
            outputs.dir(outputDir)

            doLast {
                project.logger.lifecycle("[ProtoKit] Running codegen")
                project.logger.lifecycle("[ProtoKit] Proto dir : ${protoDir.asFile}")
                project.logger.lifecycle("[ProtoKit] Output dir: ${outputDir.get().asFile}")

                ProtoKitCodegen.main(
                    arrayOf(
                        protoDir.asFile.absolutePath,
                        outputDir.get().asFile.absolutePath
                    )
                )
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {

            val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()

            kotlin.sourceSets
                .getByName("commonMain")
                .kotlin.srcDir(outputDir)

            project.tasks.matching {
                it.name.startsWith("compileKotlin")
            }.configureEach {
                dependsOn(generateProtoKit)
            }
        }
    }
}

