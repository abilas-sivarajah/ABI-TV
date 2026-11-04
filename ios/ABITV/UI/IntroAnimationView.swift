import SwiftUI
import AVFoundation

private let introDurationMs: Double = 2950.0
private let bassHitMs: Double = 1420.0
private let wobbleStartMs: Double = 1920.0

private func fastOutSlowIn(_ x: Double) -> Double {
    let t = min(max(x, 0.0), 1.0)
    return t * t * (3.0 - 2.0 * t)
}

private func fastOutLinearIn(_ x: Double) -> Double {
    let t = min(max(x, 0.0), 1.0)
    return t * t
}

private func popEasing(_ x: Double) -> Double {
    let t = min(max(x, 0.0), 1.0)
    let c1 = 1.70158
    let c3 = c1 + 1.0
    return 1.0 + c3 * pow(t - 1.0, 3.0) + c1 * pow(t - 1.0, 2.0)
}

private func phase(_ timeMs: Double, _ startMs: Double, _ endMs: Double, easing: (Double) -> Double = fastOutSlowIn) -> Double {
    let raw = min(max((timeMs - startMs) / (endMs - startMs), 0.0), 1.0)
    return easing(raw)
}

private func spinAngle(_ timeMs: Double) -> Double {
    1080.0 * phase(timeMs, 0.0, 1400.0)
}

private func bassHit(_ timeMs: Double) -> Double {
    phase(timeMs, bassHitMs, bassHitMs + 60.0) * (1.0 - phase(timeMs, bassHitMs + 60.0, bassHitMs + 400.0))
}

private func glowPulse(_ timeMs: Double) -> Double {
    let wub = pow((1.0 - cos(spinAngle(timeMs) * .pi / 180.0)) / 2.0, 6.0)
    let wobble: Double
    if timeMs >= wobbleStartMs {
        wobble = 0.25 * (1.0 - cos(2.0 * .pi * 5.0 * (timeMs - wobbleStartMs) / 1000.0))
    } else {
        wobble = 0.0
    }
    return max(wub, bassHit(timeMs), wobble)
}

/// Start-Animation beim Öffnen der App inkl. Bass-Ton (`intro_sound.wav`).
/// Entspricht 1:1 `IntroAnimation.kt` auf Android und PC.
public struct IntroAnimationView: View {
    public let onFinished: () -> Void

    @State private var startTime: Date?
    @State private var audioPlayer: AVAudioPlayer?
    @State private var didFinish = false

    public init(onFinished: @escaping () -> Void) {
        self.onFinished = onFinished
    }

    public var body: some View {
        TimelineView(.animation) { timeline in
            let elapsedMs: Double = {
                guard let start = startTime else { return 0.0 }
                return min(timeline.date.timeIntervalSince(start) * 1000.0, introDurationMs)
            }()

            let bgAlpha = 1.0 - phase(elapsedMs, 2550.0, 2950.0)
            let zoom = 1.0 + 0.15 * phase(elapsedMs, 2450.0, 2950.0, easing: fastOutLinearIn)
            let punch = 1.0 + 0.08 * bassHit(elapsedMs)
            let totalScale = zoom * punch
            let revealProgress = phase(elapsedMs, 1350.0, 2000.0)
            let triScale = 0.3 + 0.7 * phase(elapsedMs, 0.0, 600.0, easing: popEasing)
            let triAlpha = phase(elapsedMs, 0.0, 350.0) * (1.0 - phase(elapsedMs, 2450.0, 2850.0))
            let wordAlpha = phase(elapsedMs, 1350.0, 1650.0) * (1.0 - phase(elapsedMs, 2450.0, 2800.0))
            let pulse = glowPulse(elapsedMs)
            let glowAlpha = min(phase(elapsedMs, 300.0, 1100.0) + 0.7 * pulse, 1.0) * (1.0 - phase(elapsedMs, 2450.0, 2850.0))

            ZStack {
                RadialGradient(
                    colors: [
                        Color(red: 0x0F / 255.0, green: 0x17 / 255.0, blue: 0x2A / 255.0),
                        Color(red: 0x02 / 255.0, green: 0x03 / 255.0, blue: 0x06 / 255.0)
                    ],
                    center: .center,
                    startRadius: 10,
                    endRadius: 500
                )
                .opacity(bgAlpha)
                .ignoresSafeArea()

                HStack(spacing: 18) {
                    ZStack {
                        Circle()
                            .fill(
                                RadialGradient(
                                    colors: [
                                        AbiTheme.accentCyan.opacity(0.45),
                                        AbiTheme.accentBlue.opacity(0.15),
                                        Color.clear
                                    ],
                                    center: .center,
                                    startRadius: 4,
                                    endRadius: 110 * (1.0 + 0.25 * pulse)
                                )
                            )
                            .frame(width: 220, height: 220)
                            .opacity(glowAlpha)

                        PlayTriangleShape()
                            .fill(
                                LinearGradient(
                                    colors: [AbiTheme.accentBlue, AbiTheme.accentCyan],
                                    startPoint: .bottomLeading,
                                    endPoint: .topTrailing
                                )
                            )
                            .frame(width: 70, height: 84)
                            .scaleEffect(triScale)
                            .rotationEffect(.degrees(spinAngle(elapsedMs)))
                            .opacity(triAlpha)
                    }
                    .frame(width: 70, height: 84)
                    .zIndex(1)

                    if revealProgress > 0.01 {
                        HStack(spacing: 12) {
                            Text("ABI")
                                .font(.system(size: 36, weight: .heavy))
                                .foregroundColor(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 7)
                                .background(
                                    LinearGradient(
                                        colors: [AbiTheme.accentBlue, AbiTheme.accentCyan],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .clipShape(RoundedRectangle(cornerRadius: 14))

                            Text("TV")
                                .font(.system(size: 36, weight: .bold))
                                .foregroundColor(AbiTheme.accentCyan)
                        }
                        .offset(x: -(1.0 - revealProgress) * 140.0)
                        .opacity(wordAlpha)
                    }
                }
                .offset(x: (1.0 - revealProgress) * 70.0)
                .scaleEffect(totalScale)
            }
            .onChange(of: elapsedMs >= introDurationMs) { finished in
                if finished && !didFinish {
                    didFinish = true
                    onFinished()
                }
            }
        }
        .onAppear {
            playIntroSound()
            startTime = Date()
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: UInt64(introDurationMs * 1_000_000))
                if !didFinish {
                    didFinish = true
                    onFinished()
                }
            }
        }
    }

    private func playIntroSound() {
        guard let url = Bundle.main.url(forResource: "intro_sound", withExtension: "wav") else { return }
        do {
            #if os(iOS)
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            #endif
            let player = try AVAudioPlayer(contentsOf: url)
            player.prepareToPlay()
            player.play()
            audioPlayer = player
        } catch {
            // Ton optional, falls Gerät stumm oder Audio-Session belegt
        }
    }
}

private struct PlayTriangleShape: Shape {
    func path(in rect: CGRect) -> Path {
        let corners = [
            CGPoint(x: 0, y: 0),
            CGPoint(x: rect.width, y: rect.height / 2.0),
            CGPoint(x: 0, y: rect.height)
        ]
        let radius = rect.width * 0.1
        var path = Path()
        for i in corners.indices {
            let corner = corners[i]
            let prev = corners[(i + corners.count - 1) % corners.count]
            let next = corners[(i + 1) % corners.count]
            let distPrev = hypot(prev.x - corner.x, prev.y - corner.y)
            let distNext = hypot(next.x - corner.x, next.y - corner.y)
            let from = CGPoint(
                x: corner.x + (prev.x - corner.x) * (radius / distPrev),
                y: corner.y + (prev.y - corner.y) * (radius / distPrev)
            )
            let to = CGPoint(
                x: corner.x + (next.x - corner.x) * (radius / distNext),
                y: corner.y + (next.y - corner.y) * (radius / distNext)
            )
            if i == 0 {
                path.move(to: from)
            } else {
                path.addLine(to: from)
            }
            path.addQuadCurve(to: to, control: corner)
        }
        path.closeSubpath()
        return path
    }
}
