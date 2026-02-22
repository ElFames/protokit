package com.fames.protokit.plugin

import com.fames.protokit.plugin.codegen.ProtoKitCodegen
import com.google.protobuf.DescriptorProtos
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.FileInputStream

/**
 * A custom Gradle task that generates Kotlin code from a protobuf descriptor set.
 */
abstract class ProtoKitCodegenTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val descriptorSet: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val descriptorSetFile = descriptorSet.get().asFile
        if (!descriptorSetFile.exists() || descriptorSetFile.length() == 0L) {
            logger.warn("ProtoKit: Descriptor set file not found or empty. Skipping generation.")
            return
        }

        FileInputStream(descriptorSetFile).use { fis ->
            val descriptorSetProto = DescriptorProtos.FileDescriptorSet.parseFrom(fis)
            ProtoKitCodegen(descriptorSetProto, outputDirectory.get().asFile).generate()
        }
    }
}
