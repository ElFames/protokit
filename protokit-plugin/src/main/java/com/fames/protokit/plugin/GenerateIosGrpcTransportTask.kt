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
import NIO
import ComposeApp

class IosGrpcTransport: NSObject, GrpcTransport {
    let baseUrl: String

    private let group: MultiThreadedEventLoopGroup
    private let connection: ClientConnection

    func initIos() {
        guard let urlComponents = URL.init(string: baseUrl),
        let host = urlComponents.host,
        let port = urlComponents.port else {
            fatalError("Invalid base URL for gRPC client: \(baseUrl)")
        }

        self.group = MultiThreadedEventLoopGroup(numberOfThreads: 1)

        let tls = ClientConnection.Configuration.TLS.init(configuration: .makeClientConfiguration())
        let config = ClientConnection.Configuration(target: .hostAndPort(host, port), eventLoopGroup: group, tls: tls)
        self.connection = ClientConnection(configuration: config)

        print("gRPC connection configured for \(host):\(port)")
        super.init()
    }

    func unaryCall(
        method: String,
        requestBytes: KotlinByteArray,
        timeoutMillis: KotlinLong?,
        headers: [String : String],
        completionHandler: @escaping (TransportResponse?, Error?) -> Void
    ) {
        let requestData = Data(requestBytes)

        var callOptions = CallOptions()
        headers.forEach { key, value in
            callOptions.customMetadata.add(name: key, value: value)
        }
        if let timeout = timeoutMillis?.int64Value {
            callOptions.timeLimit = .timeout(.milliseconds(timeout))
        }

        let unaryCall = connection.makeUnaryCall(
            path: method,
            request: requestData,
            callOptions: callOptions
        )

        unaryCall.response.and(unaryCall.trailingMetadata).whenComplete { result in
            DispatchQueue.main.async {
                switch result {
                case .success(let (responseData, trailingMetadata)):
                    let trailers = self.extractTrailers(from: trailingMetadata)

                    let transportResponse = TransportResponse(
                        body: responseData.toKotlinByteArray(),
                        trailers: trailers
                    )
                    completionHandler(transportResponse, nil)

                case .failure(let error):
                    completionHandler(nil, error)
                }
            }
        }
    }

    func serverStream(
    method: String,
    requestBytes: KotlinByteArray,
    headers: [String : String]
    ) -> StreamCall {
        fatalError("serverStream(swift) no está implementado")
    }

    deinit {
        try? connection.close().wait()
        try? group.syncShutdownGracefully()
    }

    private func extractTrailers(from metadata: HPACKHeaders) -> GrpcTrailers {
        let rawTrailers = metadata.reduce(into: [String: String]()) { result, header in
            result[header.name] = header.value
        }

        let grpcStatusCodeStr = rawTrailers["grpc-status"]
        let grpcStatusCode = Int32(grpcStatusCodeStr ?? "") ?? GrpcStatus.unknown.code
        let grpcStatus = GrpcStatus(code: grpcStatusCode)
        let grpcMessage = rawTrailers["grpc-message"]

        return GrpcTrailers(
            status: grpcStatus,
            message: grpcMessage,
            raw: rawTrailers
        )
    }
}

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let byteArray = self.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) -> [UInt8] in
            Array(bytes)
        }
        let kotlinByteArray = KotlinByteArray(size: Int32(byteArray.count))
        for (index, byte) in byteArray.enumerated() {
            kotlinByteArray.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return kotlinByteArray
    }
}
""".trimIndent()

        iosProtoKitDirForWindows.mkdirs()
        iosProtoKitDirForMac.mkdirs()

        File(iosProtoKitDirForWindows, "IosGrpcTransport.swift").writeText(swiftContent)
        File(iosProtoKitDirForMac, "IosGrpcTransport.swift").writeText(swiftContent)

        val contentViewFileForWindows = File(iosAppDirForWindows, "ContentView.swift")
        val contentViewFileForMac = File(iosAppDirForMac, "ContentView.swift")

        if (contentViewFileForWindows.exists()) {
            writeBridge(contentViewFileForWindows)
        } else {
            writeBridge(contentViewFileForMac)
        }
    }

    private fun writeBridge(file: File) {
        val lines = file.readLines().toMutableList()
        val targetLine = "MainViewControllerKt.MainViewController()"
        val newLine = "        GrpcTransportProvider.provide(IosGrpcTransport())"
        val insertionIndex = lines.indexOfFirst { it.contains(targetLine) }

        if (insertionIndex != -1 && !lines.any { it.contains("GrpcTransportProvider.provide") }) {
            lines.add(insertionIndex, newLine)
            file.writeText(lines.joinToString(System.lineSeparator()))
        }
    }
}
