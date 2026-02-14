package com.fames.protokit.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import kotlin.collections.forEach

class ProtoKitPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val protoDir = project.layout.projectDirectory
            .dir("src/commonMain/protos")

        val generatedRoot = project.layout.buildDirectory
            .dir("generated/protokit")

        val generateProtoKit = project.tasks.register<Copy>("protokitGenerate") {
            group = "protokit"
            description = "Prepare ProtoKit gRPC sources from .proto files"

            from(protoDir)
            into(generatedRoot)

            include("**/*.proto")

            doFirst {
                project.logger.lifecycle(
                    """
                    |[ProtoKit]
                    |Protos directory : ${protoDir.asFile}
                    |Generated output : ${generatedRoot.get().asFile}
                    """.trimMargin()
                )
            }
        }

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {

            val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()

            kotlin.sourceSets.getByName("commonMain")
                .kotlin.srcDir(
                    generatedRoot.map { it.asFile.resolve("commonMain") }
                )

            listOf("androidMain", "iosMain", "desktopMain").forEach { name ->
                kotlin.sourceSets.findByName(name)?.kotlin?.srcDir(
                    generatedRoot.map { it.asFile.resolve(name) }
                )
            }

            project.tasks.matching {
                it.name.startsWith("compileKotlin")
            }.configureEach {
                dependsOn(generateProtoKit)
            }
        }
    }
}
