package com.fames.protokit.plugin.task

import com.fames.protokit.plugin.codegen.ProtoKitCodegen
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Tarea personalizada de Gradle que genera código Kotlin a partir de archivos .proto.
 * Sigue las mejores prácticas al declarar explícitamente sus entradas y salidas,
 * lo que la hace compatible con la caché de configuración de Gradle y las compilaciones incrementales.
 */
abstract class ProtoKitGenerateTask : DefaultTask() {

    init {
        group = "protokit"
        description = "Generates Kotlin sources from .proto files using ProtoKit"
    }

    /**
     * El directorio que contiene los archivos de definición .proto.
     * Anotado como @InputDirectory para que Gradle pueda rastrear los cambios.
     */
    @get:InputDirectory
    abstract val protoDir: DirectoryProperty

    /**
     * El directorio donde se generará el código Kotlin.
     * Anotado como @OutputDirectory para que Gradle sepa qué carpeta produce esta tarea.
     */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /**
     * La acción principal de la tarea. Este método se ejecuta cuando se invoca la tarea.
     * Es seguro usar .get() aquí porque estamos en la fase de ejecución de Gradle.
     */
    @TaskAction
    fun execute() {
        // La lógica de logging y ejecución vive aquí, de forma segura.
        logger.lifecycle("[ProtoKit] Running codegen...")
        logger.lifecycle("[ProtoKit] Proto dir : ${protoDir.get().asFile.path}")
        logger.lifecycle("[ProtoKit] Output dir: ${outputDir.get().asFile.path}")

        // Limpia el directorio de salida antes de generar para evitar archivos antiguos.
        outputDir.get().asFile.deleteRecursively()
        outputDir.get().asFile.mkdirs()

        // Llama a tu lógica de generación de código.
        ProtoKitCodegen.main(
            arrayOf(
                protoDir.get().asFile.absolutePath,
                outputDir.get().asFile.absolutePath
            )
        )
        logger.lifecycle("[ProtoKit] Codegen finished successfully.")
    }
}
