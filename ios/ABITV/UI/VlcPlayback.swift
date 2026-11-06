import SwiftUI
import UIKit
import MobileVLCKit

/// VLC-Player für alles, was AVPlayer nicht kann: IPTV-Sender als durchgehender MPEG-TS-Strom
/// (viele Anbieter sperren HLS) sowie MKV/AVI-Filme aus eigenen Listen.
/// Der Zustand wird vom Player-Bildschirm regelmäßig abgefragt (kein Delegate nötig).
final class VlcPlayback {
    let videoView: UIView = {
        let view = UIView()
        view.backgroundColor = .black
        view.isUserInteractionEnabled = false
        return view
    }()

    private var player: VLCMediaPlayer?
    private var sawVideo = false

    var isActive: Bool { player != nil }

    /// Startet die Adresse; Kopfzeilen der Liste (User-Agent, Referer) gibt VLC beim Abruf mit.
    func play(url: URL, headers: [String: String], isLive: Bool) {
        stop()
        let media = VLCMedia(url: url)
        // Etwas Puffer gegen Aussetzer; Live braucht weniger als Filme
        media.addOption(isLive ? ":network-caching=1500" : ":network-caching=3000")
        media.addOption(":http-reconnect")
        if let agent = headers.first(where: { $0.key.caseInsensitiveCompare("User-Agent") == .orderedSame })?.value {
            media.addOption(":http-user-agent=\(agent)")
        }
        if let referer = headers.first(where: { $0.key.caseInsensitiveCompare("Referer") == .orderedSame })?.value {
            media.addOption(":http-referrer=\(referer)")
        }
        let newPlayer = VLCMediaPlayer()
        newPlayer.drawable = videoView
        newPlayer.media = media
        player = newPlayer
        sawVideo = false
        newPlayer.play()
    }

    func stop() {
        guard let player else { return }
        player.stop()
        player.drawable = nil
        self.player = nil
    }

    func resume() { player?.play() }
    func pause() { player?.pause() }

    var isPlaying: Bool { player?.isPlaying ?? false }

    /// Bild läuft: Zeit zählt hoch oder der Player meldet "spielt" mit Bildgröße.
    var hasStarted: Bool {
        guard let player else { return false }
        if player.time.intValue > 0 || player.hasVideoOut { sawVideo = true }
        return sawVideo
    }

    var failed: Bool { player?.state == .error }
    var ended: Bool { player?.state == .ended }

    var currentSec: Double { Double(player?.time.intValue ?? 0) / 1000 }

    /// Länge in Sekunden; 0 bei Live-Sendern.
    var durationSec: Double { Double(player?.media?.length.intValue ?? 0) / 1000 }

    var canSeek: Bool { (player?.isSeekable ?? false) && durationSec > 0 }

    func seek(to seconds: Double) {
        guard let player, canSeek else { return }
        player.time = VLCTime(int: Int32(max(0, seconds) * 1000))
    }
}

/// Bildfläche des VLC-Players.
struct VlcVideoView: UIViewRepresentable {
    let playback: VlcPlayback

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = .black
        attach(to: container)
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        if playback.videoView.superview !== uiView { attach(to: uiView) }
    }

    private func attach(to container: UIView) {
        let video = playback.videoView
        video.removeFromSuperview()
        video.frame = container.bounds
        video.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        container.addSubview(video)
    }
}
