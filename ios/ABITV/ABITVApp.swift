import SwiftUI

@main
struct ABITVApp: App {
    // Liefert die erlaubten Ausrichtungen (Player: Querformat, sonst frei)
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        #if canImport(WebKit)
        StreamResolver.browserExtractor = WebKitStreamExtractor()
        #endif
    }

    var body: some Scene {
        WindowGroup {
            RootContainerView()
                .preferredColorScheme(.dark)
        }
    }
}

struct RootContainerView: View {
    @ObservedObject private var castManager = IosCastManager.shared
    @ObservedObject private var storage = LocalStorage.shared

    @State private var showIntro: Bool = true
    @State private var selectedCard: AnimeCard? = nil
    @State private var activePlayback: ActivePlaybackItem? = nil

    @State private var showCastSheet: Bool = false
    @State private var availableRelease: AppRelease? = nil

    var body: some View {
        ZStack {
            AbiTheme.bgPrimary.ignoresSafeArea()

            HomeView(
                onSelectItem: { card in
                    selectedCard = card
                },
                onPlayDirect: { entry in
                    let lang = LanguageOption.fromKey(entry.preferredLangKey) ?? storage.preferredLanguage
                    activePlayback = ActivePlaybackItem(
                        animeSlug: entry.animeSlug,
                        seasonLabel: entry.seasonLabel,
                        episodeNumber: entry.episodeNumber,
                        episodePath: entry.episodePath,
                        language: lang,
                        title: entry.animeTitle,
                        episodeTitle: entry.episodeTitle,
                        coverUrl: entry.coverUrl
                    )
                },
                onPlayLiveChannel: { channel in
                    activePlayback = ActivePlaybackItem(
                        animeSlug: "live:\(channel.id)",
                        seasonLabel: "LIVE",
                        episodeNumber: 1,
                        episodePath: channel.streamUrl,
                        language: .gerDub,
                        title: channel.name,
                        episodeTitle: "LIVE • \(channel.qualityBadge)",
                        coverUrl: channel.logoUrl,
                        liveChannel: channel
                    )
                },
                onOpenCastDialog: {
                    castManager.startDiscovery()
                    showCastSheet = true
                },
                onTriggerUpdate: { release in
                    availableRelease = release
                }
            )
            .zIndex(0)

            if let card = selectedCard {
                DetailView(
                    animeSlug: card.slug,
                    initialTitle: card.title,
                    initialCover: card.coverUrl,
                    onBack: {
                        selectedCard = nil
                    },
                    onOpenCastSheet: {
                        castManager.startDiscovery()
                        showCastSheet = true
                    },
                    onPlayEpisode: { slug, ep, lang, title, cover in
                        activePlayback = ActivePlaybackItem(
                            animeSlug: slug,
                            seasonLabel: ep.seasonLabel,
                            episodeNumber: ep.number,
                            episodePath: ep.path,
                            language: lang,
                            title: title,
                            episodeTitle: ep.displayTitle,
                            coverUrl: cover
                        )
                    }
                )
                .id(card.slug)
                .zIndex(1)
            }

            if let playback = activePlayback {
                PlayerView(
                    item: playback,
                    onBack: {
                        activePlayback = nil
                    },
                    onOpenCastSheet: {
                        castManager.startDiscovery()
                        showCastSheet = true
                    },
                    onSwitch: { next in
                        // Andere Folge oder Sprache: Player mit dem neuen Auftrag neu starten
                        activePlayback = next
                    }
                )
                .id(playback.id)
                .zIndex(2)
            }

            if let message = castManager.bannerMessage {
                VStack {
                    Text(message)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(AbiTheme.textPrimary)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(AbiTheme.bgElevated)
                        .clipShape(Capsule())
                        .overlay(
                            Capsule()
                                .stroke(AbiTheme.accentCyan.opacity(0.5), lineWidth: 1)
                        )
                        .padding(.top, 52)
                    Spacer()
                }
                .transition(.move(edge: .top).combined(with: .opacity))
                // Über Detailseite (1) und Player (2), sonst sind Cast-Meldungen dort unsichtbar
                .zIndex(5)
            }

            if showIntro {
                IntroAnimationView {
                    withAnimation(.easeOut(duration: 0.3)) {
                        showIntro = false
                    }
                }
                .zIndex(10)
            }
        }
        .sheet(isPresented: $showCastSheet) {
            CastBottomSheet(
                onDismiss: { showCastSheet = false }
            )
            .presentationDetents([.medium, .large])
        }
        .sheet(isPresented: Binding(
            get: { availableRelease != nil },
            set: { if !$0 { availableRelease = nil } }
        )) {
            if let release = availableRelease {
                MobileUpdateSheet(
                    release: release,
                    onDismiss: {
                        availableRelease = nil
                    }
                )
                .presentationDetents([.medium, .large])
            }
        }
        // Player immer im Querformat (auch mit Ausrichtungssperre), danach zurück ins Hochformat
        .onChange(of: activePlayback != nil) { playing in
            if playing { OrientationManager.enterPlayer() } else { OrientationManager.leavePlayer() }
        }
        .task {
            castManager.initialize()
            // Erst nach der Start-Animation prüfen, damit der Update-Dialog nicht über dem Intro aufgeht
            try? await Task.sleep(nanoseconds: 3_200_000_000)
            if case .available(let release) = await UpdateManager.checkForUpdate(forceCheck: false) {
                await MainActor.run {
                    self.availableRelease = release
                }
            }
        }
    }
}
