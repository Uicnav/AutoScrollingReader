import SwiftUI
import UIKit

@main
struct iOSApp: App {
    @State var fileUrl: String? = nil

    init() {
        UIApplication.shared.isIdleTimerDisabled = true
    }

    var body: some Scene {
        WindowGroup {
            ContentView(fileUrl: fileUrl)
                .onOpenURL { url in
                    print("Am primit fișier: \(url.absoluteString)")
                    fileUrl = url.absoluteString
                }
        }
    }
}
