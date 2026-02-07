import SwiftUI

@main
struct iOSApp: App {
    // Starea pentru URL-ul fișierului deschis
    @State var fileUrl: String? = nil

    var body: some Scene {
        WindowGroup {
            // Aici trimitem URL-ul către interfața Kotlin
            ContentView(fileUrl: fileUrl)
                .onOpenURL { url in
                    // Când iOS primește un fișier din "Open With", salvăm URL-ul
                    print("Am primit fișier: \(url.absoluteString)")
                    fileUrl = url.absoluteString
                }
        }
    }
}
