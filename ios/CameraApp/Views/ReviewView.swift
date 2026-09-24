import SwiftUI

// MARK: - Review View

/// Photo review screen with filter selection and save/retake controls — matches Android ReviewScreen
struct ReviewView: View {
    let imageData: Data
    @Binding var isProcessing: Bool
    let onSave: () -> Void
    let onRetake: () -> Void
    let onFilterSelected: (FilterType) -> Void

    @State private var selectedFilter: FilterType = .none

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                CameraTokens.swiftBg.ignoresSafeArea()

                // Photo display
                if let uiImage = UIImage(data: imageData) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .ignoresSafeArea()
                }

                // Processing badge
                if isProcessing {
                    processingBadge
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                        .padding(.top, geometry.safeAreaInsets.top + 12)
                }

                // Bottom controls
                VStack {
                    Spacer()

                    VStack(spacing: 0) {
                        // Filter selector
                        filterSelector

                        Spacer().frame(height: 16)

                        // Retake / Save buttons
                        actionButtons
                    }
                    .padding(.bottom, geometry.safeAreaInsets.bottom + 16)
                }
            }
        }
    }

    // MARK: - Processing Badge

    private var processingBadge: some View {
        HStack(spacing: 6) {
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: CameraTokens.swiftTextMuted))
                .scaleEffect(0.8)

            Text("Processing")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(CameraTokens.swiftTextMuted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.black.opacity(0.7))
        )
    }

    // MARK: - Filter Selector

    private var filterSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(FilterType.allCases) { filter in
                    filterButton(filter)
                }
            }
            .padding(.horizontal, 20)
        }
    }

    private func filterButton(_ filter: FilterType) -> some View {
        let isActive = selectedFilter == filter
        return Button {
            selectedFilter = filter
            onFilterSelected(filter)
        } label: {
            Text(filter.rawValue)
                .font(.system(size: 12, weight: .medium))
                .tracking(0.2)
                .foregroundColor(isActive ? CameraTokens.swiftBg : CameraTokens.swiftTextMuted)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 14)
                        .fill(isActive ? CameraTokens.swiftTextPrimary : Color.clear)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .strokeBorder(isActive ? Color.clear : CameraTokens.swiftAccentDim, lineWidth: 1.5)
                )
        }
    }

    // MARK: - Action Buttons

    private var actionButtons: some View {
        HStack {
            // Retake
            Button {
                onRetake()
            } label: {
                Text("Retake")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(CameraTokens.swiftTextPrimary)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(Color.clear)
                    )
            }

            Spacer()

            // Save
            Button {
                onSave()
            } label: {
                Text("Save")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(CameraTokens.swiftBg)
                    .padding(.horizontal, 28)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(CameraTokens.swiftTextPrimary)
                    )
            }
        }
        .padding(.horizontal, 20)
    }
}