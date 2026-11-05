import UIKit

/// Dreht die App für den Player ins Querformat (wie Netflix) – auch wenn die Ausrichtungssperre
/// des iPhones an ist – und danach zurück ins Hochformat.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        OrientationManager.allowed
    }
}

@MainActor
enum OrientationManager {
    /// Erlaubte Ausrichtungen; im Player nur Querformat (beide Richtungen, das iPhone darf kippen).
    static var allowed: UIInterfaceOrientationMask = .allButUpsideDown

    static func enterPlayer() {
        set(.landscape, preferred: .landscapeRight)
    }

    static func leavePlayer() {
        set(.portrait, preferred: .portrait)
        // Danach wieder frei drehen lassen, ohne das Hochformat gleich wieder zu verlassen
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            allowed = .allButUpsideDown
            refreshSupportedOrientations()
        }
    }

    private static func set(_ mask: UIInterfaceOrientationMask, preferred: UIInterfaceOrientationMask) {
        allowed = mask
        guard let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first else { return }
        refreshSupportedOrientations()
        if #available(iOS 16.0, *) {
            scene.requestGeometryUpdate(.iOS(interfaceOrientations: preferred)) { _ in }
        }
    }

    private static func refreshSupportedOrientations() {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        for scene in scenes {
            for window in scene.windows {
                window.rootViewController?.setNeedsUpdateOfSupportedInterfaceOrientations()
            }
        }
    }
}
