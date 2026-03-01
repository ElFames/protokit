package com.fames.protokit.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateIosGrpcTransportTask : DefaultTask() {

    @get:Input
    abstract val rootDirPath: Property<String>

    @TaskAction
    fun generate() {
        val iosAppDirForMac = File(rootDirPath.get().replace("/composeApp", "/iosApp") + "/iosApp")
        val iosProtoKitDirForMac = File(iosAppDirForMac, "/ProtoKit")

        val iosAppDirForWindows = File(rootDirPath.get().replace("\\composeApp", "\\iosApp") + "\\iosApp")
        val iosProtoKitDirForWindows = File(iosAppDirForWindows, "/ProtoKit")

        val swiftContent = """
import Foundation
import GRPC
import NIOCore
import NIOPosix
import ComposeApp
import NIOHPACK

@available(iOS 13.0, *)
final class IosGrpcTransport: NSObject, GrpcTransport {

    var baseUrl: String = ""

    private var group: EventLoopGroup?
    private var connection: ClientConnection?

    // MARK: - Init

    func doInitIos() {

        guard let url = URL(string: baseUrl),
              let host = url.host,
              let port = url.port else {
            fatalError("Invalid baseUrl -> \(baseUrl)")
        }

        let group = MultiThreadedEventLoopGroup(numberOfThreads: 1)

        let connection: ClientConnection

        if url.scheme == "https" {
            connection = ClientConnection
                .usingTLSBackedByNIOSSL(on: group)
                .connect(host: host, port: port)
        } else {
            connection = ClientConnection
                .insecure(group: group)
                .connect(host: host, port: port)
        }

        self.group = group
        self.connection = connection
    }

    // MARK: - Unary

    func unaryCall(
        method: String,
        requestBytes: KotlinByteArray,
        timeoutMillis: KotlinLong?,
        headers: [String: String],
        completionHandler: @escaping (TransportResponse?, Error?) -> Void
    ) {

        guard let connection = self.connection else {
            completionHandler(nil, IosGrpcError.clientNotInitialized)
            return
        }

        let requestData = requestBytes.toSwiftData()

        var callOptions = CallOptions()
        headers.forEach { callOptions.customMetadata.add(name: $0.key, value: $0.value) }
        if let timeout = timeoutMillis?.int64Value {
            callOptions.timeLimit = .timeout(.milliseconds(timeout))
        }

        let call: UnaryCall<RawGRPCPayload, RawGRPCPayload> =
            connection.makeUnaryCall(
                path: method,
                request: RawGRPCPayload(data: requestData),
                callOptions: callOptions
            )

        call.response.whenComplete { result in
            switch result {

            case .success(let payload):
                call.trailingMetadata.whenComplete { trailersResult in
                    let trailers: HPACKHeaders
                    switch trailersResult {
                    case .success(let headers):
                        trailers = headers
                    case .failure:
                        trailers = HPACKHeaders()
                    }

                    let response = TransportResponse(
                        body: payload.data.toKotlinByteArray(),
                        trailers: Self.mapTrailers(trailers)
                    )
                    completionHandler(response, nil)
                }

            case .failure(let error):
                completionHandler(nil, error)
            }
        }
    }

    // MARK: - Stream (no implementado)

    func serverStream(
        method: String,
        requestBytes: KotlinByteArray,
        headers: [String : String]
    ) -> StreamCall {
        fatalError("serverStream not implemented")
    }

    deinit {
        try? connection?.close().wait()
        try? group?.syncShutdownGracefully()
    }
}

struct RawGRPCPayload: GRPCPayload {

    var data: Data

    init(data: Data) {
        self.data = data
    }

    init(serializedByteBuffer: inout ByteBuffer) throws {
        self.data = serializedByteBuffer.readData(length: serializedByteBuffer.readableBytes) ?? Data()
    }

    func serialize(into buffer: inout ByteBuffer) throws {
        buffer.writeBytes(data)
    }
}

private extension IosGrpcTransport {

    static func mapTrailers(_ metadata: HPACKHeaders) -> GrpcTrailers {

        var raw: [String: String] = [:]
        metadata.forEach { data in
            raw[data.name] = data.value
        }

        let statusCode = Int32(raw["grpc-status"] ?? "") ?? GrpcStatus.unknown.code
        let status = GrpcStatus.companion.fromCode(code: statusCode)
        let message = raw["grpc-message"]

        return GrpcTrailers(
            status: status,
            message: message,
            raw: raw
        )
    }
}

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let array = KotlinByteArray(size: Int32(count))
        for (i, byte) in self.enumerated() {
            array.set(index: Int32(i), value: Int8(bitPattern: byte))
        }
        return array
    }
}

extension KotlinByteArray {
    func toSwiftData() -> Data {
        var bytes = [UInt8](repeating: 0, count: Int(size))
        for i in 0..<Int(size) {
            bytes[i] = UInt8(bitPattern: get(index: Int32(i)))
        }
        return Data(bytes)
    }
}

enum IosGrpcError: Error {
    case clientNotInitialized
    case invalidMethodFormat
}

""".trimIndent()

        iosProtoKitDirForWindows.mkdirs()
        iosProtoKitDirForMac.mkdirs()

        File(iosProtoKitDirForWindows, "IosGrpcTransport.swift").writeText(swiftContent)
        File(iosProtoKitDirForMac, "IosGrpcTransport.swift").writeText(swiftContent)

        val iosAppFileForWindows = File(iosAppDirForWindows, "iosApp.swift")
        val iosAppFileForMac = File(iosAppDirForMac, "iosApp.swift")

        if (iosAppFileForWindows.exists()) {
            writeBridge(iosAppFileForWindows)
        } else {
            writeBridge(iosAppFileForMac)
        }
    }

    private fun writeBridge(file: File) {

        val iosAppFile = File(file.parentFile, "iosApp.swift")
        if (!iosAppFile.exists()) return

        val lines = iosAppFile.readLines().toMutableList()

        val composeImport = "import ComposeApp"
        val provideLine =
            "        GrpcTransportProvider.shared.provide(implementation: IosGrpcTransport())"

        // ----------------------------
        // 1️⃣ Añadir import ComposeApp
        // ----------------------------
        if (!lines.any { it.trim() == composeImport }) {

            val firstImportIndex = lines.indexOfFirst { it.trim().startsWith("import ") }

            if (firstImportIndex != -1) {
                lines.add(firstImportIndex + 1, composeImport)
            }
        }

        // Evitar duplicar provide
        if (lines.any { it.contains("GrpcTransportProvider.shared.provide") }) {
            iosAppFile.writeText(lines.joinToString(System.lineSeparator()))
            return
        }

        // ----------------------------
        // 2️⃣ Buscar struct que implemente App
        // ----------------------------
        val structIndex = lines.indexOfFirst { it.contains(": App") }
        if (structIndex == -1) {
            iosAppFile.writeText(lines.joinToString(System.lineSeparator()))
            return
        }

        // ----------------------------
        // 3️⃣ Buscar init existente
        // ----------------------------
        val initIndex = lines.indexOfFirst { it.trim().startsWith("init(") }

        if (initIndex != -1) {

            val braceIndex = (initIndex until lines.size)
                .firstOrNull { lines[it].contains("{") }

            if (braceIndex != null) {
                lines.add(braceIndex + 1, provideLine)
            }

        } else {

            val structBraceIndex = (structIndex until lines.size)
                .firstOrNull { lines[it].contains("{") }

            if (structBraceIndex != null) {

                val initBlock = listOf(
                    "    init() {",
                    provideLine,
                    "    }",
                    ""
                )

                lines.addAll(structBraceIndex + 1, initBlock)
            }
        }

        iosAppFile.writeText(lines.joinToString(System.lineSeparator()))
    }
}
