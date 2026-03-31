//
//  ContentView.swift
//  HelloWorld
//
//  Created by Ido David on 15/10/2025.
//

import SwiftUI

struct ContentView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var isFullscreen = false

    var body: some View {
        WebView(
            storeId: "nzRdWaBc1JPk2XN3B9bp",
            embeddingId: "9iM1LIQ3DHqs06jyxuuq",
            onEvent: { event in
                switch event {
                case .openDisplay:
                    isFullscreen = true
                case .closeFsrIframe:
                    isFullscreen = false
                    dismiss()
                }
            }
        )
        .ignoresSafeArea()
        .statusBarHidden(isFullscreen)
    }
}

#Preview {
    ContentView()
}
