import SwiftUI
import ComposeApp // Numele setat în build.gradle la baseName

struct ContentView: UIViewControllerRepresentable {
    var fileUrl: String?

    func makeUIViewController(context: Context) -> UIViewController {
        // AICI ESTE LINIA DESPRE CARE ÎNTREBAI
        // Cheamă funcția MainViewController din fișierul MainViewController.kt
        return MainViewControllerKt.MainViewController(fileUrl: fileUrl)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // Lăsăm gol
    }
}
