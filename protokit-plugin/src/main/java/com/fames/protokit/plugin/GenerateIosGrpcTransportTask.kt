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
import NIOHPACK
import ComposeApp

@available(iOS 13.0, *)
final class IosGrpcTransport: NSObject, GrpcTransport {

    var baseUrl: String = ""

    private var group: EventLoopGroup?
    private var channel: GRPCChannel?

    func doInitIos() {
        guard
            !baseUrl.isEmpty,
            let url = URL(string: baseUrl),
            let host = url.host,
            let port = url.port
        else {
            print("ProtoKit-iOS Error: Invalid baseUrl -> \(baseUrl)")
            return
        }

        do {
            let group = PlatformSupport.makeEventLoopGroup(loopCount: 1)

            let channel = try GRPCChannelPool.with(
                target: .host(host, port: port),
                transportSecurity: .plaintext,
                eventLoopGroup: group
            )

            self.group = group
            self.channel = channel

            print("ProtoKit-iOS: gRPC v1 initialized for \(host):\(port)")
        } catch {
            print("ProtoKit-iOS Error: Failed to initialize client: \(error)")
        }
    }

    func unaryCall(
        method: String,
        requestBytes: KotlinByteArray,
        timeoutMillis: KotlinLong?,
        headers: [String: String],
        completionHandler: @escaping (TransportResponse?, Error?) -> Void
    ) {

        guard let channel = self.channel else {
            completionHandler(nil,
                IosGrpcError.clientNotInitialized("Client not initialized. Call initIos() first.")
            )
            return
        }

        let requestData = requestBytes.toSwiftData()

        var metadata = HPACKHeaders()
        headers.forEach { metadata.add(name: $0.key, value: $0.value) }

        let timeLimit: TimeLimit =
            timeoutMillis.map { .timeout(.milliseconds(Int64($0.int64Value))) }
            ?? .none

        let callOptions = CallOptions(
            customMetadata: metadata,
            timeLimit: timeLimit
        )

        let call = channel.makeUnaryCall(
            path: method,
            request: requestData,
            callOptions: callOptions
        )

        call.response.whenComplete { result in
            switch result {
            case .success(let responseData):

                call.trailingMetadata.whenComplete { trailersResult in
                    let trailers = (try? trailersResult.get()) ?? HPACKHeaders()

                    let transportResponse = TransportResponse(
                        body: responseData.toKotlinByteArray(),
                        trailers: trailers.toGrpcTrailers()
                    )

                    completionHandler(transportResponse, nil)
                }

            case .failure(let error):
                completionHandler(nil, error)
            }
        }
    }

    deinit {
        print("ProtoKit-iOS: Shutting down gRPC...")

        try? channel?.close().wait()
        try? group?.syncShutdownGracefully()
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
