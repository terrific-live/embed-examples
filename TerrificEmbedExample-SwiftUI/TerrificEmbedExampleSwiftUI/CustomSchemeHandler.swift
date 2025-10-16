//
//  CustomSchemeHandler.swift
//  HelloWorld
//
//  Created by Ido David on 15/10/2025.
//

import Foundation
import WebKit

class CustomSchemeHandler: NSObject, WKURLSchemeHandler {
    // The actual SDK URL
    private let terrificSDKURL = URL(string: "https://terrific.live/terrific-sdk.js")!
    // The origin you want to simulate
    private let simulatedOrigin = "https://<your domain>"

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        var request = URLRequest(url: terrificSDKURL)
        request.setValue(simulatedOrigin, forHTTPHeaderField: "Origin")

        // Optionally copy other headers from the WebView request
        request.setValue("Mozilla/5.0 (iPhone; iOS 17.0)", forHTTPHeaderField: "User-Agent")

        let task = URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                let errResponse = HTTPURLResponse(url: self.terrificSDKURL, statusCode: 500, httpVersion: nil, headerFields: nil)!
                urlSchemeTask.didReceive(errResponse)
                urlSchemeTask.didFinish()
                print("Error loading SDK:", error.localizedDescription)
                return
            }

            guard let data = data, let response = response else { return }

            urlSchemeTask.didReceive(response)
            urlSchemeTask.didReceive(data)
            urlSchemeTask.didFinish()
        }

        task.resume()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        // Nothing to clean up
    }
}
