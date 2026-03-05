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
