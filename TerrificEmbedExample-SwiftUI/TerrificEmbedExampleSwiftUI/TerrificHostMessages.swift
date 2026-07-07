import Foundation

enum TerrificHostMessages {
    static let carouselHostVisible = "CAROUSEL_HOST_VISIBLE"
    static let iframeReady = "IFRAME_READY"
    static let timelineIframeId = "terrific-timeline-iframe"

    static let notifyCarouselHostVisibleScript = """
    (function(isVisible) {
        function findCarouselIframe() {
            const embedding = document.querySelector('terrific-embedding,[data-source="terrific"]');
            const shadowRoot = embedding?.shadowRoot;
            if (shadowRoot) {
                const iframe = shadowRoot.querySelector('iframe');
                if (iframe) return iframe;
            }
            return document.getElementById('\(timelineIframeId)');
        }

        const iframe = findCarouselIframe();
        iframe?.contentWindow?.postMessage(
            { type: '\(carouselHostVisible)', isVisible: isVisible },
            '*'
        );
    })
    """
}
