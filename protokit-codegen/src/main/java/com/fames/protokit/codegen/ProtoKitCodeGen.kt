package com.fames.protokit.codegen

import java.io.File
object ProtoKitCodegen {

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) {
            "Usage: <inputProtoDir> <outputDir>"
        }

        val inputDir = File(args[0])
        val outputDir = File(args[1])

        val parser = ProtoParser()
        val generator = KotlinGenerator()

        inputDir.walkTopDown()
            .filter { it.extension == "proto" }
            .forEach { protoFile ->

                val proto = parser.parse(protoFile.readText())
                val files = generator.generate(proto)

                files.forEach { (name, content) ->
                    val pkgPath = proto.pkg.replace(".", "/")
                    val outDir = File(outputDir, pkgPath)
                    outDir.mkdirs()

                    File(outDir, name).writeText(content)
                }
            }
    }
}
