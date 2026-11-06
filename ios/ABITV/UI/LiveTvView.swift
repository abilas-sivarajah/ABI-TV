import SwiftUI
import UniformTypeIdentifiers

public struct LiveTvView: View {
    public let onPlayChannel: (LiveTvChannel) -> Void

    @ObservedObject private var castManager = IosCastManager.shared
    @State private var allChannels: [LiveTvChannel] = []
    @State private var customError: String?
    @State private var isLoading = true
    @State private var selectedCategory = "all"
    @State private var userPickedTab = false
    @State private var customM3uDraft = ""
    @State private var hasSavedUrl = !LocalStorage.shared.customM3uUrl.isEmpty
    @State private var m3uInputError: String?
    @State private var trees: [LiveTvNode] = []
    @State private var customGrouped = false
    /// Gewählte Unterreiter je Ebene unterhalb des Land-/Ordner-Reiters.
    @State private var treePath: [String] = []
    @State private var query = ""
    @State private var folderName = LocalStorage.shared.m3uFolderName
    @State private var hasFolder = !LocalStorage.shared.m3uFolderBookmark.isEmpty
    @State private var showFolderPicker = false
    @State private var folderError: String?

    private static let treePrefix = "tree:"
    /// Bis zu so vielen Unterreitern als Knöpfe, darüber als Liste (z. B. alle Serien einer Gruppe).
    private static let chipLimit = 60

    public init(onPlayChannel: @escaping (LiveTvChannel) -> Void) {
        self.onPlayChannel = onPlayChannel
    }

    private var selectedRoot: LiveTvNode? {
        trees.first { Self.treePrefix + $0.id == selectedCategory }
    }

    /// Flache Reiter: feste Sender und eine Liste ohne Gruppen; sortierte Listen und Ordner haben eigene Reiter.
    private var filteredChannels: [LiveTvChannel] {
        let flat = allChannels.filter { ch in
            !ch.category.hasPrefix("folder:") && !(customGrouped && ch.category == "custom")
        }
        if selectedCategory == "all" { return flat }
        return flat.filter { $0.category == selectedCategory }
    }

    public var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                header

                // Hauptreiter: zuerst die eigenen Listen (Länder, Ordner), danach die festen Kategorien
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(trees) { root in
                            chip("\(root.icon) \(root.title)", selected: selectedCategory == Self.treePrefix + root.id) {
                                userPickedTab = true
                                if selectedCategory != Self.treePrefix + root.id {
                                    selectedCategory = Self.treePrefix + root.id
                                    treePath = []
                                    query = ""
                                }
                            }
                        }
                        ForEach(LiveTvRepository.categories) { cat in
                            chip("\(cat.icon) \(cat.title)", selected: selectedCategory == cat.id) {
                                userPickedTab = true
                                selectedCategory = cat.id
                                query = ""
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                }

                if selectedCategory == "custom" {
                    customListCard
                    folderCard
                    if customGrouped {
                        Text("Deine Liste ist nach Ländern sortiert: " +
                             trees.filter { $0.id.hasPrefix("iptv:") }.map { "\($0.icon) \($0.title) (\($0.total))" }.joined(separator: " · "))
                            .font(.system(size: 12))
                            .foregroundColor(AbiTheme.textSecondary)
                            .padding(.horizontal, 16)
                    }
                }

                if let err = customError, selectedCategory == "custom" || selectedCategory == "all" || selectedRoot != nil {
                    Text(err)
                        .font(.system(size: 12))
                        .foregroundColor(AbiTheme.liveRed)
                        .padding(.horizontal, 16)
                }

                if isLoading && allChannels.isEmpty {
                    HStack {
                        Spacer()
                        ProgressView()
                            .tint(AbiTheme.accentCyan)
                            .padding(.vertical, 48)
                        Spacer()
                    }
                } else if let root = selectedRoot {
                    treeContent(root)
                } else if filteredChannels.isEmpty {
                    Text(
                        selectedCategory == "custom"
                            ? (customGrouped ? "Die Sender stehen in den Länder-Reitern ganz vorne." : "Noch keine eigene Liste. Gib oben den Link zu deiner M3U-Datei ein.")
                            : "Keine Sender in dieser Kategorie."
                    )
                    .font(.system(size: 14))
                    .foregroundColor(AbiTheme.textMuted)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 32)
                } else {
                    channelGrid(filteredChannels, trail: [])
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

    private var header: some View {
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
    }

    private func chip(_ title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: selected ? .bold : .medium))
                .foregroundColor(selected ? AbiTheme.accentCyan : AbiTheme.textSecondary)
                .lineLimit(1)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? AbiTheme.accentCyan.opacity(0.18) : AbiTheme.bgSurface)
                .clipShape(Capsule())
                .overlay(
                    Capsule()
                        .stroke(selected ? AbiTheme.accentCyan : Color.white.opacity(0.1), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Baum (Land/Ordner › Unterreiter)

    private enum TreeLevel {
        case chips(depth: Int, children: [LiveTvNode], selectedId: String)
        case back(depth: Int, parent: LiveTvNode, selected: LiveTvNode)
    }

    private struct TreeView {
        var levels: [TreeLevel] = []
        var list: [LiveTvNode]?
        var listDepth = 0
        var leaf: LiveTvNode?
        var trail: [LiveTvNode] = []
    }

    private func resolve(_ root: LiveTvNode) -> TreeView {
        var view = TreeView()
        view.trail = [root]
        var node = root
        var depth = 0
        while true {
            let children = node.children
            if children.isEmpty {
                view.leaf = node
                view.listDepth = depth
                return view
            }
            let chosen = depth < treePath.count ? children.first { $0.id == treePath[depth] } : nil
            if children.count <= Self.chipLimit {
                let selected = chosen ?? children[0]
                view.levels.append(.chips(depth: depth, children: children, selectedId: selected.id))
                node = selected
            } else {
                guard let chosen else {
                    view.list = children
                    view.listDepth = depth
                    return view
                }
                view.levels.append(.back(depth: depth, parent: node, selected: chosen))
                node = chosen
            }
            view.trail.append(node)
            depth += 1
        }
    }

    @ViewBuilder
    private func treeContent(_ root: LiveTvNode) -> some View {
        // Suche im ganzen Land/Ordner: Reiter (z. B. Serien) und Sender/Filme
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundColor(AbiTheme.textMuted)
            TextField("In \(root.title) suchen …", text: $query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundColor(AbiTheme.textPrimary)
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundColor(AbiTheme.textMuted)
                }
            }
        }
        .padding(10)
        .background(AbiTheme.bgSurface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(.horizontal, 16)

        let trimmed = query.trimmingCharacters(in: .whitespaces)
        if trimmed.count >= 2 {
            searchResults(root, trimmed)
        } else {
            let view = resolve(root)
            ForEach(Array(view.levels.enumerated()), id: \.offset) { _, level in
                switch level {
                case let .chips(depth, children, selectedId):
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(children) { child in
                                chip([child.icon, child.title].filter { !$0.isEmpty }.joined(separator: " "),
                                     selected: child.id == selectedId) {
                                    treePath = Array(treePath.prefix(depth)) + [child.id]
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                case let .back(depth, parent, selected):
                    HStack(spacing: 10) {
                        Button {
                            treePath = Array(treePath.prefix(depth))
                        } label: {
                            Label(parent.title, systemImage: "chevron.left")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(AbiTheme.accentCyan)
                                .lineLimit(1)
                        }
                        .buttonStyle(.plain)
                        Text(selected.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(AbiTheme.textPrimary)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 16)
                }
            }
            if let list = view.list {
                nodeList(list) { child in treePath = Array(treePath.prefix(view.listDepth)) + [child.id] }
            } else if let leaf = view.leaf {
                if leaf.channels.isEmpty {
                    Text("Keine Sender in diesem Reiter.")
                        .font(.system(size: 14))
                        .foregroundColor(AbiTheme.textMuted)
                        .padding(.horizontal, 16)
                } else {
                    channelGrid(leaf.channels, trail: view.trail)
                }
            }
        }
    }

    /// Viele Unterreiter (z. B. alle Serien einer Gruppe) als Liste.
    private func nodeList(_ nodes: [LiveTvNode], onOpen: @escaping (LiveTvNode) -> Void) -> some View {
        LazyVStack(spacing: 6) {
            ForEach(nodes) { node in
                Button { onOpen(node) } label: {
                    HStack {
                        Text(node.title)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(AbiTheme.textPrimary)
                            .lineLimit(1)
                        Spacer()
                        Text(node.children.isEmpty ? "\(node.total)" : "\(node.children.count) St. · \(node.total)")
                            .font(.system(size: 12))
                            .foregroundColor(AbiTheme.textMuted)
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(AbiTheme.textMuted)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(AbiTheme.bgElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 24)
    }

    /// Pfade (Reiter-Kennungen ab der ersten Ebene) zu allen Reitern, deren Titel passt.
    private func searchNodes(_ root: LiveTvNode, _ text: String, limit: Int) -> [(path: [String], node: LiveTvNode)] {
        var result: [(path: [String], node: LiveTvNode)] = []
        func walk(_ node: LiveTvNode, _ path: [String]) {
            for child in node.children {
                if result.count >= limit { return }
                let childPath = path + [child.id]
                if child.title.localizedCaseInsensitiveContains(text) { result.append((childPath, child)) }
                walk(child, childPath)
            }
        }
        walk(root, [])
        return result
    }

    @ViewBuilder
    private func searchResults(_ root: LiveTvNode, _ text: String) -> some View {
        let nodes = searchNodes(root, text, limit: 40)
        let channels = Array(root.allChannels().lazy.filter { $0.name.localizedCaseInsensitiveContains(text) }.prefix(300))
        if nodes.isEmpty && channels.isEmpty {
            Text("Nichts gefunden für „\(text)“.")
                .font(.system(size: 14))
                .foregroundColor(AbiTheme.textMuted)
                .padding(.horizontal, 16)
        }
        if !nodes.isEmpty {
            nodeList(nodes.map { $0.node }) { picked in
                if let hit = nodes.first(where: { $0.node.id == picked.id }) {
                    treePath = hit.path
                    query = ""
                }
            }
        }
        if !channels.isEmpty {
            channelGrid(channels, trail: [])
        }
    }

    private func channelGrid(_ channels: [LiveTvChannel], trail: [LiveTvNode]) -> some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 155), spacing: 12)],
            spacing: 12
        ) {
            ForEach(channels) { channel in
                ChannelCard(
                    channel: channel,
                    showTvButton: castManager.connectedDevice != nil,
                    onClick: { onPlayChannel(named(channel, trail)) },
                    onPlayOnTv: {
                        Task {
                            _ = await castManager.playLiveOnTv(named(channel, trail))
                        }
                    }
                )
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 24)
    }

    /// Serienfolgen heißen im Raster nur "S01 E05": für den Player den Serientitel davorsetzen.
    private func named(_ channel: LiveTvChannel, _ trail: [LiveTvNode]) -> LiveTvChannel {
        guard channel.name.range(of: #"^S\d+ E\d+"#, options: .regularExpression) != nil,
              let show = trail.last(where: { !$0.title.hasPrefix("Staffel ") && $0.title != "Weitere" }) else { return channel }
        return LiveTvChannel(id: channel.id, name: "\(show.title) · \(channel.name)", logoUrl: channel.logoUrl,
                             streamUrl: channel.streamUrl, category: channel.category, qualityBadge: channel.qualityBadge,
                             headers: channel.headers, group: channel.group)
    }

    // MARK: - Eigene Liste und Ordner

    /// Link zur eigenen Liste: nach dem Speichern nie wieder sichtbar (enthält Zugangsdaten).
    private var customListCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(hasSavedUrl ? "🔒 Eigene Liste gespeichert" : "📂 Eigene M3U-Liste")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
            Text(hasSavedUrl
                 ? "Der Link wird nicht angezeigt. Zum Ersetzen einen neuen Link eingeben."
                 : "Link zu einer M3U- oder M3U8-Senderliste. Sie wird zusätzlich zu den festen Sendern geladen.")
                .font(.system(size: 12))
                .foregroundColor(AbiTheme.textSecondary)

            TextField(hasSavedUrl ? "Neuer Link …" : "http://server.de/liste.m3u", text: $customM3uDraft)
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
                if hasSavedUrl {
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
                .disabled(customM3uDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
        .padding(14)
        .background(AbiTheme.bgElevated)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
    }

    /// Eigener Ordner mit M3U-Dateien (Dateien-App, iCloud Drive …): Unterordner werden eigene Reiter.
    private var folderCard: some View {
        let folderTree = trees.first { $0.id.hasPrefix("folder-tree:") }
        return VStack(alignment: .leading, spacing: 8) {
            Text(hasFolder ? "📁 Ordner: \(folderName)" : "📁 Eigener Ordner")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(AbiTheme.textPrimary)
            Text(hasFolder
                 ? "\(folderTree?.total ?? 0) Sender · steht ganz vorne als eigener Reiter · neue Dateien mit ↻ laden"
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
            trees = result.trees
            customGrouped = result.customGrouped
            // Beim ersten Öffnen gleich die erste eigene Liste zeigen
            if selectedCategory == "all", !userPickedTab, let first = result.trees.first {
                selectedCategory = Self.treePrefix + first.id
                treePath = []
            }
            // Reiter einer entfernten Liste/eines entfernten Ordners gibt es nicht mehr
            if selectedCategory.hasPrefix(Self.treePrefix), selectedRoot == nil {
                selectedCategory = "custom"
                treePath = []
            }
            isLoading = false
        }
    }

    private func saveM3u(_ value: String) {
        if LiveTvRepository.saveCustomM3uUrl(value) {
            hasSavedUrl = !LocalStorage.shared.customM3uUrl.isEmpty
            customM3uDraft = ""
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

                        if !channel.logoUrl.isEmpty {
                            CachedImage(url: channel.logoUrl) { image in
                                if let image {
                                    image
                                        .resizable()
                                        .scaledToFit()
                                        .padding(10)
                                } else {
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
