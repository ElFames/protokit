package com.fames.protokit.plugin.codegen

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import java.io.File
import java.util.Locale

class ProtoKitCodegen(
    private val descriptorSet: FileDescriptorSet,
    private val outputDir: File
) {
    private val typeRegistry = mutableMapOf<String, ClassName>()
    private val messageDescriptorMap = mutableMapOf<String, DescriptorProtos.DescriptorProto>()

    fun generate() {
        buildTypeRegistry()
        descriptorSet.fileList
            .filterNot { it.name.startsWith("google/protobuf") }
            .forEach { generateFile(it) }
    }

    private fun buildTypeRegistry() {
        descriptorSet.fileList.forEach { file ->
            val javaPackage = file.options.javaPackage.takeIf { it.isNotBlank() } ?: file.`package`
            val prefix = if (file.`package`.isNotEmpty()) ".${file.`package`}." else "."
            file.messageTypeList.forEach {
                val fqName = "${prefix}${it.name}"
                typeRegistry[fqName] = ClassName(javaPackage, it.name)
                messageDescriptorMap[fqName] = it
            }
            file.enumTypeList.forEach { typeRegistry["${prefix}${it.name}"] = ClassName(javaPackage, it.name) }
            file.serviceList.forEach { typeRegistry["${prefix}${it.name}"] = ClassName(javaPackage, it.name) }
        }
        typeRegistry[".google.protobuf.Any"] = PROTO_ANY_CLASS_NAME
    }

    private fun generateFile(file: DescriptorProtos.FileDescriptorProto) {
        val javaPackage = file.options.javaPackage.takeIf { it.isNotBlank() } ?: file.`package`
        if (file.options.javaMultipleFiles) {
            file.messageTypeList.forEach { FileSpec.builder(javaPackage, it.name).addType(generateMessage(it, file.`package`)).build().writeTo(outputDir) }
            file.enumTypeList.forEach { FileSpec.builder(javaPackage, it.name).addType(generateEnum(it, file.`package`)).build().writeTo(outputDir) }
            file.serviceList.forEach { 
                val (iface, client) = generateService(it, file.`package`)
                FileSpec.builder(javaPackage, it.name).addType(iface).build().writeTo(outputDir)
                FileSpec.builder(javaPackage, client.name!!).addType(client).build().writeTo(outputDir)
            }
        } else {
            val fileName = file.name.substringAfterLast('/').substringBeforeLast('.') + "Protokit"
            val fileSpec = FileSpec.builder(javaPackage, fileName)
            file.messageTypeList.forEach { fileSpec.addType(generateMessage(it, file.`package`)) }
            file.enumTypeList.forEach { fileSpec.addType(generateEnum(it, file.`package`)) }
            file.serviceList.forEach { 
                val (iface, client) = generateService(it, file.`package`)
                fileSpec.addType(iface).addType(client) 
            }
            fileSpec.build().writeTo(outputDir)
        }
    }

    private fun generateMessage(descriptor: DescriptorProtos.DescriptorProto, pkg: String): TypeSpec {
        val className = resolveType(descriptor.name, pkg)
        val oneofDeclarations = descriptor.oneofDeclList
        val oneofFieldMap = descriptor.fieldList.filter { it.hasOneofIndex() }.groupBy { it.oneofIndex }

        val builder = TypeSpec.classBuilder(className)
        if (descriptor.fieldList.isNotEmpty() || oneofDeclarations.isNotEmpty()) {
            builder.addModifiers(KModifier.DATA)
        }

        val constructor = FunSpec.constructorBuilder()

        // Add oneof sealed classes
        oneofDeclarations.forEachIndexed { index, oneofDecl ->
            val oneofName = oneofDecl.name.toPascalCase()
            val oneofClassName = className.nestedClass(oneofName)
            val sealedInterface = TypeSpec.interfaceBuilder(oneofClassName)
                .addModifiers(KModifier.SEALED)
                .build()
            builder.addType(sealedInterface)

            oneofFieldMap[index]?.forEach { field ->
                val oneofCaseClassName = oneofClassName.nestedClass(field.name.toPascalCase())
                val fieldType = mapProtoTypeToKotlin(field)
                val caseClass = if (isFieldEmpty(field)) {
                    TypeSpec.objectBuilder(oneofCaseClassName)
                        .addSuperinterface(oneofClassName)
                        .build()
                } else {
                    TypeSpec.classBuilder(oneofCaseClassName)
                        .addModifiers(KModifier.DATA)
                        .addSuperinterface(oneofClassName)
                        .primaryConstructor(FunSpec.constructorBuilder().addParameter("value", fieldType).build())
                        .addProperty(PropertySpec.builder("value", fieldType).initializer("value").build())
                        .build()
                }
                builder.addType(caseClass)
            }
            constructor.addParameter(oneofDecl.name, oneofClassName.copy(nullable = true))
            builder.addProperty(PropertySpec.builder(oneofDecl.name, oneofClassName.copy(nullable = true)).initializer(oneofDecl.name).build())
        }

        // Add regular fields
        descriptor.fieldList.filter { !it.hasOneofIndex() }.forEach {
            constructor.addParameter(it.name, mapProtoTypeToKotlin(it))
            builder.addProperty(PropertySpec.builder(it.name, mapProtoTypeToKotlin(it)).initializer(it.name).build())
        }

        return builder.primaryConstructor(constructor.build())
            .addFunction(generateEncodeMethod(descriptor, pkg, oneofDeclarations, oneofFieldMap))
            .addType(generateDecodeCompanion(descriptor, pkg, oneofDeclarations, oneofFieldMap))
            .build()
    }

    private fun generateEnum(descriptor: DescriptorProtos.EnumDescriptorProto, pkg: String): TypeSpec {
        val enumClass = TypeSpec.enumBuilder(resolveType(descriptor.name, pkg))
        descriptor.valueList.forEach { enumClass.addEnumConstant(it.name) }
        return enumClass.build()
    }

    private fun generateService(descriptor: DescriptorProtos.ServiceDescriptorProto, pkg: String): Pair<TypeSpec, TypeSpec> {
        val serviceName = descriptor.name
        val serviceInterface = resolveType(serviceName, pkg)
        val clientImpl = TypeSpec.classBuilder("${serviceName}Client").addSuperinterface(serviceInterface)
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("client", PROTO_CLIENT_CLASS_NAME).build())
            .addProperty(PropertySpec.builder("client", PROTO_CLIENT_CLASS_NAME, KModifier.PRIVATE).initializer("client").build())
        val interfaceBuilder = TypeSpec.interfaceBuilder(serviceInterface)

        descriptor.methodList.forEach { method ->
            val req = resolveType(method.inputType)
            val res = resolveType(method.outputType)
            val urlPath = if (pkg.isNotEmpty()) "/$pkg.$serviceName/${method.name}" else "/$serviceName/${method.name}"
            interfaceBuilder.addFunction(FunSpec.builder(method.name.lowercaseFirst()).addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND).addParameter("request", req).returns(RESPONSE_CLASS_NAME.parameterizedBy(res)).build())
            clientImpl.addFunction(FunSpec.builder(method.name.lowercaseFirst()).addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND).addParameter("request", req).returns(RESPONSE_CLASS_NAME.parameterizedBy(res))
                .addCode("return client.unary(%S, request, { it.encode() }, { %T.decode(it) })", urlPath, res).build())
        }
        return Pair(interfaceBuilder.build(), clientImpl.build())
    }
    
    private fun generateEncodeMethod(
        descriptor: DescriptorProtos.DescriptorProto,
        pkg: String,
        oneofDecls: List<DescriptorProtos.OneofDescriptorProto>,
        oneofFieldMap: Map<Int, List<DescriptorProtos.FieldDescriptorProto>>
    ): FunSpec {
        val body = CodeBlock.builder()
        body.addStatement("val writer = %T()", PROTO_WRITER_CLASS_NAME)

        // oneof fields
        oneofDecls.forEachIndexed { index, oneofDecl ->
            body.addStatement("when (val oneofValue = %N) {", oneofDecl.name)
            body.indent()
            oneofFieldMap[index]?.forEach { field ->
                val oneofClassName = resolveType(descriptor.name, pkg).nestedClass(oneofDecl.name.toPascalCase())
                val caseClassName = oneofClassName.nestedClass(field.name.toPascalCase())
                val valueIdentifier = if (isFieldEmpty(field)) "oneofValue" else "oneofValue.value"

                body.addStatement("is %T -> {", caseClassName)
                body.indent()
                writeField(valueIdentifier, field, body, false)
                body.unindent()
                body.addStatement("}")
            }
            body.addStatement("null -> {}") // Do nothing if oneof is not set
            body.unindent()
            body.addStatement("}")
        }

        // Regular fields
        descriptor.fieldList.filter { !it.hasOneofIndex() }.forEach { field ->
            writeField(field.name, field, body, field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED)
        }

        body.addStatement("return writer.toByteArray()")
        return FunSpec.builder("encode").returns(ByteArray::class).addCode(body.build()).build()
    }

    private fun writeField(valueIdentifier: String, field: DescriptorProtos.FieldDescriptorProto, body: CodeBlock.Builder, isRepeated: Boolean) {
        if (isMap(field)) {
            body.addStatement("%N.forEach { (key, value) ->", valueIdentifier)
            body.indent()
            body.addStatement("writer.writeObject(%L, key to value) { pair ->", field.number)
            body.indent()
            val mapEntry = messageDescriptorMap[field.typeName]!!
            writeField("pair.first", mapEntry.fieldList[0], body, false)
            writeField("pair.second", mapEntry.fieldList[1], body, false)
            body.unindent()
            body.addStatement("}")
            body.unindent()
            body.addStatement("}")
            return
        }

        if (isRepeated) {
            body.addStatement("%N.forEach { value ->", valueIdentifier)
            body.indent()
        }

        val finalValueIdentifier = if (isRepeated) "value" else valueIdentifier

        when (field.type) {
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM -> {
                body.addStatement("writer.writeEnum(%L, %N.ordinal)", field.number, finalValueIdentifier)
            }
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> {
                val typeName = mapProtoTypeToKotlin(field)
                if (typeName.isNullable) {
                    body.addStatement("%N?.let { writer.writeObject(%L) { it.encode() } }", finalValueIdentifier, field.number)
                } else {
                    body.addStatement("writer.writeObject(%L) { %N.encode() }", field.number, finalValueIdentifier)
                }
            }
            else -> {
                val methodSuffix = getProtoMethodSuffix(field.type)
                body.addStatement("writer.write%L(%L, %N)", methodSuffix, field.number, finalValueIdentifier)
            }
        }

        if (isRepeated) {
            body.unindent()
            body.addStatement("}")
        }
    }

    private fun generateDecodeCompanion(
        descriptor: DescriptorProtos.DescriptorProto,
        pkg: String,
        oneofDecls: List<DescriptorProtos.OneofDescriptorProto>,
        oneofFieldMap: Map<Int, List<DescriptorProtos.FieldDescriptorProto>>
    ): TypeSpec {
        val className = resolveType(descriptor.name, pkg)
        val body = CodeBlock.builder()

        // Init vars
        descriptor.fieldList.filter { !it.hasOneofIndex() }.forEach { body.addStatement("var %N: %T = %L", it.name, mapProtoTypeToKotlin(it, true), protoDefault(it)) }
        oneofDecls.forEach { body.addStatement("var %N: %T? = null", it.name, className.nestedClass(it.name.toPascalCase())) }

        body.addStatement("while (true) {")
        body.indent()
        body.addStatement("val (tag, wire) = reader.readTag() ?: break")
        body.addStatement("when (tag) {")
        body.indent()
        
        descriptor.fieldList.forEach { field ->
            if (field.hasOneofIndex()) {
                val oneofDecl = oneofDecls[field.oneofIndex]
                val oneofClassName = className.nestedClass(oneofDecl.name.toPascalCase())
                val caseClassName = oneofClassName.nestedClass(field.name.toPascalCase())
                val readCode = if (isFieldEmpty(field)) ""
                               else "(%L)".format(readFieldCode(field))

                body.addStatement("%L -> %N = %T$readCode", field.number, oneofDecl.name, caseClassName)
            } else {
                val isMap = isMap(field)
                val isRepeated = field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED && !isMap

                val assignment = when {
                    isMap -> "%N += %L"
                    isRepeated -> "%N.add(%L)"
                    else -> "%N = %L"
                }
                body.addStatement("%L -> $assignment", field.number, field.name, readFieldCode(field))
            }
        }
        
        body.addStatement("else -> reader.skip(wire)")
        body.unindent()
        body.addStatement("}") // end when
        body.unindent()
        body.addStatement("}") // end while
        
        val constructorRegularParams = descriptor.fieldList.filter { !it.hasOneofIndex() }.joinToString(", ") { it.name }
        val constructorOneofParams = oneofDecls.joinToString(", ") { it.name }
        val constructorParams = listOf(constructorOneofParams, constructorRegularParams).filter { it.isNotEmpty() }.joinToString(", ")

        body.addStatement("return %T(%L)", className, constructorParams)

        return TypeSpec.companionObjectBuilder()
            .addFunction(FunSpec.builder("decode").addParameter("bytes", ByteArray::class).returns(className).addStatement("return decode(%T(bytes))", PROTO_READER_CLASS_NAME).build())
            .addFunction(FunSpec.builder("decode").addParameter("reader", PROTO_READER_CLASS_NAME).returns(className).addCode(body.build()).build())
            .build()
    }

    private fun isMap(field: DescriptorProtos.FieldDescriptorProto): Boolean {
        return field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED &&
               field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE &&
               messageDescriptorMap[field.typeName]?.options?.mapEntry == true
    }

    private fun isFieldEmpty(field: DescriptorProtos.FieldDescriptorProto): Boolean {
        return field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE && messageDescriptorMap[field.typeName]?.fieldList?.isEmpty() == true
    }

    private fun mapProtoTypeToKotlin(field: DescriptorProtos.FieldDescriptorProto, isMutable: Boolean = false): TypeName {
        if (isMap(field)) {
            val mapEntryDescriptor = messageDescriptorMap[field.typeName]!!
            val keyType = mapProtoTypeToKotlin(mapEntryDescriptor.fieldList[0])
            val valueType = mapProtoTypeToKotlin(mapEntryDescriptor.fieldList[1])
            return (if (isMutable) MUTABLE_MAP_CLASS_NAME else MAP).parameterizedBy(keyType, valueType)
        }

        if (isFieldEmpty(field)) return Unit::class.asTypeName()

        val type = if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE || field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) resolveType(field.typeName) else getPrimitiveTypeName(field.type)

        if (field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            return (if (isMutable) MUTABLE_LIST_CLASS_NAME else LIST_CLASS_NAME).parameterizedBy(type)
        }

        return if (field.typeName == ".google.protobuf.Any") type.copy(nullable = true) else type
    }

    private fun readFieldCode(field: DescriptorProtos.FieldDescriptorProto): CodeBlock {
        if (isMap(field)) {
            val mapEntryDescriptor = messageDescriptorMap[field.typeName]!!
            val keyField = mapEntryDescriptor.fieldList[0]
            val valueField = mapEntryDescriptor.fieldList[1]
            return CodeBlock.builder()
                .addStatement("run {")
                .indent()
                .addStatement("var key: %T = %L", mapProtoTypeToKotlin(keyField), protoDefault(keyField))
                .addStatement("var value: %T = %L", mapProtoTypeToKotlin(valueField), protoDefault(valueField))
                .addStatement("reader.readMessage { _ -> ")
                .indent()
                .addStatement("while(true) {")
                .indent()
                .addStatement("val (tag, wire) = reader.readTag() ?: break")
                .addStatement("when (tag) {")
                .indent()
                .addStatement("1 -> key = %L", readFieldCode(keyField))
                .addStatement("2 -> value = %L", readFieldCode(valueField))
                .addStatement("else -> reader.skip(wire)")
                .unindent().addStatement("}") // end when
                .unindent().addStatement("}") // end while
                .unindent().addStatement("}") // end readMessage
                .addStatement("key to value")
                .unindent()
                .addStatement("}")
                .build()
        }

        return when (field.type) {
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> {
                val type = resolveType(field.typeName)
                CodeBlock.of("%T.decode(reader)", type)
            }
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM -> {
                val enumType = resolveType(field.typeName)
                CodeBlock.of("%T.values().getOrElse(reader.readEnum()) { %T.values()[0] }", enumType, enumType)
            }
            else -> CodeBlock.of("reader.read%L()", getProtoMethodSuffix(field.type))
        }
    }

    private fun getProtoMethodSuffix(type: DescriptorProtos.FieldDescriptorProto.Type): String = when (type) {
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE -> "Double"
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT -> "Float"
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64 -> "Int64"
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL -> "Bool"
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING -> "String"
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES -> "Bytes"
        else -> "Int32"
    }

    private fun protoDefault(field: DescriptorProtos.FieldDescriptorProto): String {
        if (isMap(field)) return "mutableMapOf()"
        if (field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) return "mutableListOf()"
        if (field.typeName == ".google.protobuf.Any") return "null"
        if (isFieldEmpty(field)) return "Unit"
        if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) return "${resolveType(field.typeName)}.decode(ByteArray(0))"
        if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) return "${resolveType(field.typeName)}.values()[0]"
        return when (getPrimitiveTypeName(field.type)) {
            Double::class.asTypeName() -> "0.0"
            Float::class.asTypeName() -> "0.0f"
            Long::class.asTypeName() -> "0L"
            Boolean::class.asTypeName() -> "false"
            String::class.asTypeName() -> "\"\""
            ByteArray::class.asTypeName() -> "ByteArray(0)"
            else -> "0"
        }
    }

    private fun resolveType(protoName: String, localPackage: String? = null): ClassName {
        val fqName = if (protoName.startsWith(".")) {
            protoName
        } else if (localPackage.isNullOrEmpty()) {
            ".$protoName"
        } else {
            ".$localPackage.$protoName"
        }
        return typeRegistry[fqName] ?: throw IllegalStateException("Type '$fqName' not found. Registered: ${typeRegistry.keys}")
    }

    private fun getPrimitiveTypeName(type: DescriptorProtos.FieldDescriptorProto.Type): TypeName = when (type) {
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE -> Double::class.asTypeName()
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT -> Float::class.asTypeName()
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64 -> Long::class.asTypeName()
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL -> Boolean::class.asTypeName()
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING -> String::class.asTypeName()
        DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES -> ByteArray::class.asTypeName()
        else -> Int::class.asTypeName()
    }

    private fun String.toPascalCase() = split("_").joinToString("") { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString() } }
    private fun String.lowercaseFirst() = if (isNotEmpty()) first().lowercase() + substring(1) else this

    companion object {
        private val PROTO_CLIENT_CLASS_NAME = ClassName("com.fames.protokit.sdk", "ProtoClient")
        private val PROTO_ANY_CLASS_NAME = ClassName("com.fames.protokit.sdk.models", "ProtoAny")
        private val RESPONSE_CLASS_NAME = ClassName("com.fames.protokit.sdk.models", "Response")
        private val PROTO_WRITER_CLASS_NAME = ClassName("com.fames.protokit.core.io", "ProtoWriter")
        private val PROTO_READER_CLASS_NAME = ClassName("com.fames.protokit.core.io", "ProtoReader")
        private val LIST_CLASS_NAME = ClassName("kotlin.collections", "List")
        private val MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
        private val MUTABLE_MAP_CLASS_NAME = ClassName("kotlin.collections", "MutableMap")
    }
}
