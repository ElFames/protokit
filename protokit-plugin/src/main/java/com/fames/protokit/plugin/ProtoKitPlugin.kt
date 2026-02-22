package com.fames.protokit.plugin

import com.fames.protokit.plugin.codegen.ProtoKitCodegen
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.gradle.ProtobufExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.PathSensitivity
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.FileInputStream

class ProtoKitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.withId("com.google.protobuf") {
            val protobufExtension = project.extensions.getByType<ProtobufExtension>()

            // 1. Configure protoc to generate a single descriptor set file.
            val descriptorSetFile = project.layout.buildDirectory.file("protokit/descriptor.pb").get().asFile
            protobufExtension.generateProtoTasks.all().forEach { task ->
                task.generateDescriptorSet = true
                task.descriptorSetOptions.path = descriptorSetFile.absolutePath
                task.descriptorSetOptions.includeImports = true
                task.descriptorSetOptions.includeSourceInfo = true
            }

            // 2. Register our code generation task.
            val outputDir = project.layout.buildDirectory.dir("generated/source/protokit/commonMain")
            val generateProtoKitTask = project.tasks.register("generateProtoKitCode") {
                group = "protokit"
                description = "Generates KMP gRPC clients from proto files."

                inputs.file(descriptorSetFile).withPathSensitivity(PathSensitivity.NONE)
                outputs.dir(outputDir)

                doLast {
                    if (!descriptorSetFile.exists() || descriptorSetFile.length() == 0L) {
                        logger.warn("ProtoKit: Descriptor set file not found or empty. Skipping generation.")
                        return@doLast
                    }

                    FileInputStream(descriptorSetFile).use { fis ->
                        val fileDescriptorSet = DescriptorProtos.FileDescriptorSet.parseFrom(fis)
                        fileDescriptorSet.fileList.forEach { fileDescriptor ->
                            ProtoKitCodegen(fileDescriptor, outputDir.get().asFile).generate()
                        }
                    }
                }
            }

            // 3. Wire our task to run after the descriptor is created.
            protobufExtension.generateProtoTasks.all().forEach { protoTask ->
                generateProtoKitTask.get().dependsOn(protoTask)
            }

            // 4. Add the generated directory to the Kotlin 'commonMain' source set.
            project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
                val kotlin = project.extensions.getByType<KotlinMultiplatformExtension>()
                kotlin.sourceSets.findByName("commonMain")?.kotlin?.srcDir(outputDir)
                project.tasks.matching { task ->
                    if (task.name.startsWith("compileKotlin")) {
                        task.dependsOn(generateProtoKitTask)
                        true
                    } else false
                }
            }
        }
    }
}
