package com.fames.protokit.plugin

import com.fames.protokit.plugin.task.ProtoKitGenerateTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class ProtoKitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val generateProtoKitTask = project.tasks.register<ProtoKitGenerateTask>("protokitGenerate") {
            protoDir.set(project.layout.projectDirectory.dir("src/commonMain/protos"))
            outputDir.set(project.layout.buildDirectory.dir("generated/protokit"))
        }

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()
            kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(generateProtoKitTask)
            project.tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
                dependsOn(generateProtoKitTask)
            }
        }
    }

}
