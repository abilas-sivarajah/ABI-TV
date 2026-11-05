import SwiftUI
import UniformTypeIdentifiers

public struct LiveTvView: View {
    public let onPlayChannel: (LiveTvChannel) -> Void

    @ObservedObject private var castManager = IosCastManager.shared
    @State private var allChannels: [LiveTvChannel] = []
    @State private var customError: String?
    @State private var isLoading = true
    @State private var selectedCategory = "all"
    @State private var customM3uDraft = LocalStorage.shared.customM3uUrl
    @State private var savedM3uUrl = LocalStorage.shared.customM3uUrl
    @State private var m3uInputError: String?
    @State private var categories: [LiveTvCategory] = LiveTvRepository.categories
    @State private var folderName = LocalStorage.shared.m3uFolderName
    @State private var hasFolder = !LocalStorage.shared.m3uFolderBookmark.isEmpty
    @State private var showFolderPicker = false
    @State private var folderError: String?

    public init(onPlayChannel: @escaping (LiveTvChannel) -> Void) {
        self.onPlayChannel = onPlayChannel
    }

    private var filteredChannels: [LiveTvChannel] {
        if selectedCategory == "all" {
            return allChannels
        }
        return allChannels.filter { $0.category == selectedCategory }
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                // Kopfzeile
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(AbiTheme.liveRed)
                                .frame(width: 8, height: 8)
                            Text("LIVE TV & SPORT")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(AbiTheme.liveRed)
                                .kerning(1)
                        }
                        Text(
                            castManager.connectedDevice != nil
                                ? "Antippen spielt am iPhone, 📺 schickt an \(castManager.connectedDevice?.name ?? "TV")"
                                : "Sender antippen zum Abspielen"
                        )
                        .font(.system(size: 12))
                        .foregroundColor(AbiTheme.textSecondary)
                        .lineLimit(1)
                    }

                    Spacer()

                    Button {
                        reload(force: true)
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .foregroundColor(AbiTheme.accentCyan)
                            .frame(width: 34, height: 34)
                    }
                    .disabled(isLoading)
                }
                .padding(.horizontal, 16)

                // Kategorien
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(categories) { cat in
                            let isSelected = selectedCategory == cat.id
                            Button {
                                selectedCategory = cat.id
                            } label: {
                                Text("\(cat.icon) \(cat.title)")
                                    .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(isSelected ? AbiTheme.accentCyan.opacity(0.18) : AbiTheme.bgSurface)
                                    .clipShape(Capsule())
                                    .overlay(
                                        Capsule()
                                            .stroke(isSelected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                                    )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16)
                }

                // Eigene M3U-Liste
                if selectedCategory == "custom" {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("📂 Eigene M3U-Liste")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(AbiTheme.textPrimary)
                        Text("Link zu einer M3U- oder M3U8-Senderliste. Sie wird zusätzlich zu den festen Sendern geladen.")
                            .font(.system(size: 12))
                            .foregroundColor(AbiTheme.textSecondary)

                        TextField("http://server.de/liste.m3u", text: $customM3uDraft)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(10)
                            .background(AbiTheme.bgSurface)
                            .foregroundColor(AbiTheme.textPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .onChange(of: customM3uDraft) { _ in
                                m3uInputError = nil
                            }

                        if let err = m3uInputError {
                            Text(err)
                                .font(.system(size: 12))
                                .foregroundColor(AbiTheme.liveRed)
                        }

                        HStack {
                            Spacer()
                            if !savedM3uUrl.isEmpty {
                                Button("Entfernen") {
                                    saveM3u("")
                                }
                                .font(.system(size: 13))
                                .foregroundColor(AbiTheme.textSecondary)
                            }

                            Button("Speichern") {
                                saveM3u(customM3uDraft)
                            }
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(AbiTheme.onAccent)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(AbiTheme.accentCyan)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .disabled(customM3uDraft.trimmingCharacters(in: .whitespacesAndNewlines) == savedM3uUrl)
                        }
                    }
                    .padding(14)
                    .background(AbiTheme.bgElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 16)

                    folderCard
                }

                if let err = customError, selectedCategory == "custom" || selectedCategory == "all" {
                    Text(err)
                        .font(.system(size: 12))
                        .foregroundColor(AbiTheme.liveRed)
                        .padding(.horizontal, 16)
                }

                if isLoading {
                    HStack {
                        Spacer()
                        ProgressView()
                            .tint(AbiTheme.accentCyan)
                            .padding(.vertical, 48)
                        Spacer()
                    }
                } else if filteredChannels.isEmpty {
                    Text(
                        selectedCategory == "custom"
                            ? "Noch keine eigene Liste. Gib oben den Link zu deiner M3U-Datei ein."
                            : "Keine Sender in dieser Kategorie."
                    )
                    .font(.system(size: 14))
                    .foregroundColor(AbiTheme.textMuted)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 32)
                } else {
                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 155), spacing: 12)],
                        spacing: 12
                    ) {
                        ForEach(filteredChannels) { channel in
                            ChannelCard(
                                channel: channel,
                                showTvButton: castManager.connectedDevice != nil,
                                onClick: { onPlayChannel(channel) },
                                onPlayOnTv: {
                                    Task {
                                        _ = await castManager.playLiveOnTv(channel)
                                    }
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
            .padding(.top, 4)
        }
        .background(AbiTheme.bgPrimary)
        .task {
            reload(force: false)
        }
        .fileImporter(isPresented: $showFolderPicker, allowedContentTypes: [.folder]) { result in
            switch result {
            case .success(let url):
                if LiveTvRepository.saveM3uFolder(url) {
                    folderError = nil
                    hasFolder = true
                    folderName = LocalStorage.shared.m3uFolderName
                    reload(force: true)
                } else {
                    folderError = "Der Ordner konnte nicht übernommen werden."
                }
            case .failure:
                folderError = "Der Ordner konnte nicht geöffnet werden."
            }
        }
    }

    /// Eigener Ordner mit M3U-Dateien (Dateien-App, iCloud Drive …): Unterordner werden eigene Reiter.
    private var folderCard: some View {
        let folderChannels = allChannels.filter { $0.category.hasPrefix("folder:") }.count
        let folderTabs = categories.filter { $0.id.hasPrefix("folder:") }.count
        return VStack(alignment: .leading, spacing: 8) {
            Text(hasFolder ? "📁 Ordner: \(folderName)" : "📁 Eigener Ordner")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
            Text(hasFolder
                 ? "\(folderChannels) Sender in \(folderTabs) Reitern · neue Dateien mit ↻ laden"
                 : "Ordner mit M3U-Dateien aus der Dateien-App wählen. Unterordner werden eigene Reiter, die Dateien darin ihre Sender.")
                .font(.system(size: 12))
                .foregroundColor(AbiTheme.textSecondary)
            if let folderError {
                Text(folderError)
                    .font(.system(size: 12))
                    .foregroundColor(AbiTheme.liveRed)
            }
            HStack {
                Spacer()
                if hasFolder {
                    Button("Entfernen") {
                        LiveTvRepository.removeM3uFolder()
                        hasFolder = false
                        folderName = ""
                        reload(force: true)
                    }
                    .font(.system(size: 13))
                    .foregroundColor(AbiTheme.textSecondary)
                }
                Button(hasFolder ? "Anderer Ordner" : "Ordner hinzufügen") {
                    folderError = nil
                    showFolderPicker = true
                }
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(AbiTheme.onAccent)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(AbiTheme.accentCyan)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .padding(14)
        .background(AbiTheme.bgElevated)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
    }

    private func reload(force: Bool) {
        isLoading = true
        Task {
            let result = await LiveTvRepository.loadChannels(forceRefresh: force)
            allChannels = result.channels
            customError = result.customError
            categories = result.categories.isEmpty ? LiveTvRepository.categories : result.categories
            // Reiter eines entfernten Ordners gibt es nicht mehr
            if !categories.contains(where: { $0.id == selectedCategory }) { selectedCategory = "custom" }
            isLoading = false
        }
    }

    private func saveM3u(_ value: String) {
        if LiveTvRepository.saveCustomM3uUrl(value) {
            savedM3uUrl = LocalStorage.shared.customM3uUrl
            customM3uDraft = savedM3uUrl
            m3uInputError = nil
            reload(force: true)
        } else {
            m3uInputError = "Das ist keine gültige Adresse (Beispiel: http://server.de/liste.m3u)."
        }
    }
}

private struct ChannelCard: View {
    let channel: LiveTvChannel
    let showTvButton: Bool
    let onClick: () -> Void
    let onPlayOnTv: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button(action: onClick) {
                ZStack(alignment: .topTrailing) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Color.black.opacity(0.4))

                        if let url = URL(string: channel.logoUrl), !channel.logoUrl.isEmpty {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .scaledToFit()
                                        .padding(10)
                                default:
                                    Text(channel.name.prefix(3).uppercased())
                                        .font(.system(size: 20, weight: .heavy))
                                        .foregroundColor(AbiTheme.accentCyan)
                                }
                            }
                        } else {
                            Text(channel.name.prefix(3).uppercased())
                                .font(.system(size: 20, weight: .heavy))
                                .foregroundColor(AbiTheme.accentCyan)
                        }
                    }
                    .frame(height: 78)

                    Text(channel.qualityBadge)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.75))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                        .padding(5)
                }
            }
            .buttonStyle(.plain)

            HStack(spacing: 6) {
                Button(action: onClick) {
                    Text(channel.name)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(AbiTheme.textPrimary)
                        .lineLimit(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)

                if showTvButton {
                    Button(action: onPlayOnTv) {
                        Image(systemName: "tv")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(AbiTheme.accentCyan)
                            .frame(width: 32, height: 32)
                            .background(AbiTheme.accentCyan.opacity(0.15))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(10)
        .background(AbiTheme.bgElevated)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.white.opacity(0.08), lineWidth: 0.8)
        )
    }
}
