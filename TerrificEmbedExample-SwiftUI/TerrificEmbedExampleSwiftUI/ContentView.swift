//
//  ContentView.swift
//  HelloWorld
//
//  Created by Ido David on 15/10/2025.
//

import SwiftUI

struct ContentView: View {
    var body: some View {
        WebView(storeId: "<your store id>", embeddingId: "<your embedding id>")
            .edgesIgnoringSafeArea(.all)
    }
}

#Preview {
    ContentView()
}
