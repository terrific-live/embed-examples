import SwiftUI

struct ContentView: View {
    private let storeId = "nzRdWaBc1JPk2XN3B9bp"
    private let embeddingId = "9iM1LIQ3DHqs06jyxuuq"

    @State private var shouldMountCarousel = false
    @State private var isCarouselVisibleInHost = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                nativeHeaderPlaceholder

                carouselSlot
                    .background(
                        GeometryReader { geometry in
                            Color.clear.preference(
                                key: CarouselSlotVisibilityKey.self,
                                value: geometry.frame(in: .global)
                            )
                        }
                    )
            }
        }
        .onPreferenceChange(CarouselSlotVisibilityKey.self) { frame in
            let isVisible = frame.intersects(UIScreen.main.bounds)
            guard isCarouselVisibleInHost != isVisible else { return }
            isCarouselVisibleInHost = isVisible
        }
    }

    private var nativeHeaderPlaceholder: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Native app content")
                .font(.title2.bold())
            Text("Scroll down to the Terrific carousel. The WebView mounts when this slot is on screen, then reports host visibility after IFRAME_READY.")
                .font(.body)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(24)
        .frame(minHeight: 640)
        .background(Color(.systemGroupedBackground))
    }

    @ViewBuilder
    private var carouselSlot: some View {
        if shouldMountCarousel {
            WebView(
                storeId: storeId,
                embeddingId: embeddingId,
                isCarouselVisibleInHost: $isCarouselVisibleInHost
            )
            .frame(height: 450)
        } else {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(.secondarySystemGroupedBackground))
                .overlay {
                    Text("Carousel loads when visible")
                        .foregroundStyle(.secondary)
                }
                .frame(height: 450)
                .padding(.horizontal, 16)
                .onAppear {
                    shouldMountCarousel = true
                }
        }
    }
}

private struct CarouselSlotVisibilityKey: PreferenceKey {
    static var defaultValue: CGRect = .zero

    static func reduce(value: inout CGRect, nextValue: () -> CGRect) {
        value = nextValue()
    }
}

#Preview {
    ContentView()
}
