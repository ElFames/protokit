package com.fames.protokit.plugin

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtension
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

class ProtoKitPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply("com.google.protobuf")

        val outputDir = project.layout.buildDirectory.dir("generated/protos")
        val descriptorSetFile = project.layout.buildDirectory.file("protokit/descriptor.pb").get().asFile

        // 1. Configure the protoc artifact version.
        project.extensions.getByType<ProtobufExtension>().protoc {
            artifact = "com.google.protobuf:protoc:4.33.5"
        }

        project.dependencies.add("protobuf", "com.google.protobuf:protobuf-java:4.33.5")

        // 2. Register our custom code generation tasks.
        val generateProtoKitTask = project.tasks.register("generateProtoKitCode", ProtoKitCodegenTask::class.java) {
            descriptorSet.set(descriptorSetFile)
            outputDirectory.set(outputDir)
        }

        val generateIosGrpcTransportTask = project.tasks.register("generateIosGrpcTransport", GenerateIosGrpcTransportTask::class.java) {
            rootDirPath.set(project.projectDir.absolutePath)
        }

        // 3. Hook generated code and configure sources.
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            project.extensions.getByType<KotlinMultiplatformExtension>()
                .sourceSets.findByName("commonMain")?.kotlin?.srcDir(outputDir)

            project.tasks.withType<AbstractKotlinCompile<*>>().configureEach {
                dependsOn(generateProtoKitTask)
                dependsOn(generateIosGrpcTransportTask)
            }

            project.tasks.withType<KotlinNativeCompile>().configureEach {
                dependsOn(generateProtoKitTask)
                dependsOn(generateIosGrpcTransportTask)
            }

            // In a KMP project, we must wait for BOTH plugins before configuring Android source sets.
            project.plugins.withId("com.android.application") {
                val androidExtension = project.extensions.getByName("android")
                // Access the 'sourceSets' property via reflection, as it's a direct property, not an extension.
                val sourceSets = androidExtension::class.java.getMethod("getSourceSets").invoke(androidExtension) as NamedDomainObjectContainer<*>
                val mainSourceSet = sourceSets.getByName("main") as ExtensionAware
                val protoSourceSet = mainSourceSet.extensions.getByName("proto") as SourceDirectorySet
                protoSourceSet.srcDir("src/commonMain/protos")
            }
        }

        // 4. Defer configuration that depends on specific task names (e.g., from build variants).
        project.afterEvaluate {
            val generateDebugProtoProvider = tasks.named<GenerateProtoTask>("generateDebugProto") {
                generateDescriptorSet = true
                descriptorSetOptions.path = descriptorSetFile.absolutePath
                descriptorSetOptions.includeImports = true
                descriptorSetOptions.includeSourceInfo = true
            }

            generateProtoKitTask.configure {
                dependsOn(generateDebugProtoProvider)
            }
        }
    }
}
