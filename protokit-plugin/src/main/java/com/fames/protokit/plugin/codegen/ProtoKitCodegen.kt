package com.fames.protokit.plugin.codegen

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import java.io.File

class ProtoKitCodegen(
    private val descriptorSet: FileDescriptorSet,
    private val outputDir: File
) {
    private val typeRegistry = mutableMapOf<String, ClassName>()

    fun generate() {
        buildTypeRegistry()
        descriptorSet.fileList
            .filterNot { it.name.startsWith("google/protobuf") }
            .forEach { generateFile(it) }
    }

    private fun buildTypeRegistry() {
        descriptorSet.fileList.forEach { file ->
            val javaPackage = file.options.javaPackage.takeIf { it.isNotBlank() } ?: file.getPackage()
            file.messageTypeList.forEach { typeRegistry[".${file.getPackage()}.${it.name}"] = ClassName(javaPackage, it.name) }
            file.enumTypeList.forEach { typeRegistry[".${file.getPackage()}.${it.name}"] = ClassName(javaPackage, it.name) }
            file.serviceList.forEach { typeRegistry[".${file.getPackage()}.${it.name}"] = ClassName(javaPackage, it.name) }
        }
    }

    private fun generateFile(file: DescriptorProtos.FileDescriptorProto) {
        val javaPackage = file.options.javaPackage.takeIf { it.isNotBlank() } ?: file.getPackage()
        if (file.options.javaMultipleFiles) {
            file.messageTypeList.forEach { FileSpec.builder(javaPackage, it.name).addType(generateMessage(it, file.getPackage())).build().writeTo(outputDir) }
            file.enumTypeList.forEach { FileSpec.builder(javaPackage, it.name).addType(generateEnum(it, file.getPackage())).build().writeTo(outputDir) }
            file.serviceList.forEach { 
                val (iface, client) = generateService(it, file.getPackage())
                FileSpec.builder(javaPackage, it.name).addType(iface).build().writeTo(outputDir)
                FileSpec.builder(javaPackage, client.name!!).addType(client).build().writeTo(outputDir)
            }
        } else {
            val fileName = file.name.substringAfterLast('/').substringBeforeLast('.') + "Protokit"
            val fileSpec = FileSpec.builder(javaPackage, fileName)
            file.messageTypeList.forEach { fileSpec.addType(generateMessage(it, file.getPackage())) }
            file.enumTypeList.forEach { fileSpec.addType(generateEnum(it, file.getPackage())) }
            file.serviceList.forEach { 
                val (iface, client) = generateService(it, file.getPackage())
                fileSpec.addType(iface).addType(client) 
            }
            fileSpec.build().writeTo(outputDir)
        }
    }

    private fun generateMessage(descriptor: DescriptorProtos.DescriptorProto, pkg: String): TypeSpec {
        val className = resolveType(descriptor.name, pkg)
        val builder = TypeSpec.classBuilder(className).addModifiers(KModifier.DATA)
        val constructor = FunSpec.constructorBuilder()
        descriptor.fieldList.forEach {
            constructor.addParameter(it.name, mapProtoTypeToKotlin(it))
            builder.addProperty(PropertySpec.builder(it.name, mapProtoTypeToKotlin(it)).initializer(it.name).build())
        }
        return builder.primaryConstructor(constructor.build())
            .addFunction(generateEncodeMethod(descriptor))
            .addType(generateDecodeCompanion(descriptor, pkg))
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
            val urlPath = "/$pkg.$serviceName/${method.name}"
            interfaceBuilder.addFunction(FunSpec.builder(method.name.lowercaseFirst()).addModifiers(KModifier.ABSTRACT, KModifier.SUSPEND).addParameter("request", req).returns(RESPONSE_CLASS_NAME.parameterizedBy(res)).build())
            clientImpl.addFunction(FunSpec.builder(method.name.lowercaseFirst()).addModifiers(KModifier.OVERRIDE, KModifier.SUSPEND).addParameter("request", req).returns(RESPONSE_CLASS_NAME.parameterizedBy(res))
                .addCode("return client.unary(%S, request, { it.encode() }, { %T.decode(it) })", urlPath, res).build())
        }
        return Pair(interfaceBuilder.build(), clientImpl.build())
    }
    
    private fun generateEncodeMethod(descriptor: DescriptorProtos.DescriptorProto): FunSpec {
        val body = CodeBlock.builder()
        body.addStatement("val writer = %T()", PROTO_WRITER_CLASS_NAME)
        descriptor.fieldList.forEach { field ->
            val fieldName = field.name
            when (field.type) {
                DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM -> {
                    body.addStatement("writer.writeEnum(%L, %N.ordinal)", field.number, fieldName)
                }
                DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> {
                    body.addStatement("writer.writeObject(%L, %N, { it.encode() })", field.number, fieldName)
                }
                else -> {
                    val methodSuffix = getProtoMethodSuffix(field.type)
                    body.addStatement("writer.write%L(%L, %N)", methodSuffix, field.number, fieldName)
                }
            }
        }
        body.addStatement("return writer.toByteArray()")
        return FunSpec.builder("encode").returns(ByteArray::class).addCode(body.build()).build()
    }

    private fun generateDecodeCompanion(descriptor: DescriptorProtos.DescriptorProto, pkg: String): TypeSpec {
        val className = resolveType(descriptor.name, pkg)
        val body = CodeBlock.builder()
        descriptor.fieldList.forEach { body.addStatement("var %N: %T = %L", it.name, mapProtoTypeToKotlin(it, true), protoDefault(it)) }
        
        body.addStatement("while (true) {")
        body.indent()
        body.addStatement("val (tag, wire) = reader.readTag() ?: break")
        body.addStatement("when (tag) {")
        body.indent()
        
        descriptor.fieldList.forEach { field ->
            body.addStatement("%L -> %N = %L", field.number, field.name, readFieldCode(field))
        }
        
        body.addStatement("else -> reader.skip(wire)")
        body.unindent()
        body.addStatement("}") // end when
        body.unindent()
        body.addStatement("}") // end while
        
        val constructorParams = descriptor.fieldList.joinToString(", ") { it.name }
        body.addStatement("return %T(%L)", className, constructorParams)

        return TypeSpec.companionObjectBuilder()
            .addFunction(FunSpec.builder("decode").addParameter("bytes", ByteArray::class).returns(className).addStatement("return decode(%T(bytes))", PROTO_READER_CLASS_NAME).build())
            .addFunction(FunSpec.builder("decode").addParameter("reader", PROTO_READER_CLASS_NAME).returns(className).addCode(body.build()).addModifiers(KModifier.PRIVATE).build())
            .build()
    }

    private fun mapProtoTypeToKotlin(field: DescriptorProtos.FieldDescriptorProto, isMutable: Boolean = false): TypeName {
        val type = if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE || field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) resolveType(field.typeName) else getPrimitiveTypeName(field.type)
        return if (field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) {
            (if (isMutable) MUTABLE_LIST_CLASS_NAME else LIST_CLASS_NAME).parameterizedBy(type)
        } else type
    }

    private fun readFieldCode(field: DescriptorProtos.FieldDescriptorProto): CodeBlock {
        return when (field.type) {
            DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE -> CodeBlock.of("%T.decode(reader)", resolveType(field.typeName))
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
        if (field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED) return "mutableListOf()"
        if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE) return "${resolveType(field.typeName)}.decode(ByteArray(0))"
        if (field.type == DescriptorProtos.FieldDescriptorProto.Type.TYPE_ENUM) return "${resolveType(field.typeName)}.values()[0]"
        return when (getPrimitiveTypeName(field.type)) {
            Double::class.asTypeName(), Float::class.asTypeName() -> "0.0f"
            Long::class.asTypeName() -> "0L"
            Boolean::class.asTypeName() -> "false"
            String::class.asTypeName() -> "\"\""
            ByteArray::class.asTypeName() -> "ByteArray(0)"
            else -> "0"
        }
    }

    private fun resolveType(protoName: String, localPackage: String? = null): ClassName {
        val fqName = if (protoName.startsWith(".")) protoName else ".${localPackage}.$protoName"
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

    private fun String.lowercaseFirst() = if (isNotEmpty()) first().lowercase() + substring(1) else this

    companion object {
        private val PROTO_CLIENT_CLASS_NAME = ClassName("com.fames.protokit.sdk", "ProtoClient")
        private val RESPONSE_CLASS_NAME = ClassName("com.fames.protokit.sdk.models", "Response")
        private val PROTO_WRITER_CLASS_NAME = ClassName("com.fames.protokit.core.io", "ProtoWriter")
        private val PROTO_READER_CLASS_NAME = ClassName("com.fames.protokit.core.io", "ProtoReader")
        private val LIST_CLASS_NAME = ClassName("kotlin.collections", "List")
        private val MUTABLE_LIST_CLASS_NAME = ClassName("kotlin.collections", "MutableList")
    }
}