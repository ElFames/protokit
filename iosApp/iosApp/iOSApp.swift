import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        GrpcTransportProvider.shared.provide(implementation: IosGrpcTransport())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}