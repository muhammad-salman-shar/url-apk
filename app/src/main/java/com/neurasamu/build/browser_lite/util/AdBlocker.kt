package com.neurasamu.build.browser_lite.util

import android.net.Uri

/**
 * Lightweight WebView-level ad blocker.
 *
 * Three layers:
 *  1. Request interception: shouldInterceptRequest returns an empty body for
 *     any request whose host matches the block list.
 *  2. Cosmetic hiding: injectAdHideCss() hides leftover ad containers via CSS.
 *  3. Popup + redirect blocking: onCreateWindow headless, and
 *     shouldOverrideUrlLoading rejects navigation to blocklist hosts.
 *
 * Block list: curated set of the most common third-party ad / tracker
 * networks. Not exhaustive - adding more is a one-line change. Does not
 * affect same-origin content, so sites that serve their own ads will still
 * show them (intentional: blocking same-origin would break the page).
 */
object AdBlocker {

    /** Hosts (and their subdomains) that should never load. */
    private val BLOCKED_HOSTS: Set<String> = setOf(
        // Google ads
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "partner.googleadservices.com",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "stats.g.doubleclick.net",

        // Facebook / Meta
        "connect.facebook.net",
        "graph.facebook.com",
        "pixel.facebook.com",
        "an.facebook.com",

        // Amazon ads
        "amazon-adsystem.com",
        "aax.amazon-adsystem.com",
        "assoc-amazon.com",

        // Common ad networks
        "ads.yahoo.com",
        "advertising.com",
        "adnxs.com",
        "adsrvr.org",
        "criteo.com",
        "criteo.net",
        "outbrain.com",
        "taboola.com",
        "revcontent.com",
        "mgid.com",
        "zergnet.com",
        "media.net",
        "bidswitch.net",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "indexexchange.com",
        "casalemedia.com",
        "smartadserver.com",
        "adform.net",
        "adsafeprotected.com",
        "moatads.com",
        "scorecardresearch.com",
        "quantserve.com",
        "quantcast.com",
        "serving-sys.com",
        "sizmek.com",
        "flashtalking.com",
        "simpli.fi",
        "sharethrough.com",
        "sonobi.com",
        "spotxchange.com",
        "spotx.tv",
        "teads.tv",
        "tremorhub.com",
        "unrulymedia.com",
        "videohub.tv",
        "yieldmo.com",
        "yieldoptimizer.com",
        "zedo.com",
        "adcolony.com",
        "applovin.com",
        "chartboost.com",
        "inmobi.com",
        "ironsrc.com",
        "mopub.com",
        "unityads.unity3d.com",
        "vungle.com",
        "supersonicads.com",
        "tapjoy.com",
        "fyber.com",

        // Pop / redirect networks (common on savefrom-like sites)
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerpops.com",
        "onclickads.net",
        "onclckds.com",
        "adcash.com",
        "exoclick.com",
        "exosrv.com",
        "juicyads.com",
        "trafficjunky.com",
        "trafficstars.com",
        "clickadu.com",
        "hilltopads.net",
        "adsterra.com",
        "clickaine.com",
        "adnium.com",
        "zeropark.com",
        "voluum.com",
        "revcontent.network",
        "mgid.net",
        "ad-maven.com",
        "admaven.com",
        "bidvertiser.com",
        "infolinks.com",
        "adf.ly",
        "sh.st",
        "ouo.io",
        "bc.vc",
        "adfoc.us",

        // Trackers / analytics
        "hotjar.com",
        "mouseflow.com",
        "crazyegg.com",
        "fullstory.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "heapanalytics.com",
        "matomo.cloud",
        "statcounter.com",
        "histats.com",
        "clarity.ms",
        "newrelic.com",
        "nr-data.net",
        "bugsnag.com",
        "sentry.io",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "kochava.com",
        "singular.net",
        "tenjin.io",

        // CDN-hosted ad scripts
        "adsafeprotected.com",
        "adroll.com",
        "semasio.net",
        "crwdcntrl.net",
        "demdex.net",
        "everesttech.net",
        "omtrdc.net",
        "2o7.net",
        "bluekai.com",
        "krxd.net",
        "rlcdn.com",
        "tribalfusion.com",
        "turn.com",
        "addthis.com",
        "sharethis.com",
        "addtoany.com"
    )

    /**
     * URL path fragments that almost always indicate an ad even when the
     * host is not on the list (e.g. ad iframes served from a first-party
     * CDN with a tell-tale path).
     */
    private val BLOCKED_PATH_FRAGMENTS: List<String> = listOf(
        "/ads/",
        "/ad/",
        "/adserver/",
        "/adframe/",
        "/banner/",
        "/popunder/",
        "/popup/",
        "/pagead/",
        "/advertisement/",
        "/advert/",
        "/sponsored/"
    )

    /** True if this URL should be blocked outright. */
    fun isAd(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            if (matchesBlockedHost(host)) return true
            val path = uri.path?.lowercase() ?: ""
            // Path fragment check only applies to non-first-party-looking
            // paths; we do NOT check against the current page's host here
            // because we have no context. Keeping it global is a safe default.
            BLOCKED_PATH_FRAGMENTS.any { path.contains(it) }
        } catch (t: Throwable) {
            false
        }
    }

    private fun matchesBlockedHost(host: String): Boolean {
        if (BLOCKED_HOSTS.contains(host)) return true
        // Subdomain match: a.doubleclick.net -> doubleclick.net
        for (blocked in BLOCKED_HOSTS) {
            if (host.endsWith(".$blocked")) return true
        }
        return false
    }

    /**
     * CSS to hide remaining ad containers that slipped past request blocking
     * (same-origin ad slots, empty wrappers). Applied on every page finish.
     */
    fun injectAdHideCss(): String = """
        (function() {
            try {
                if (document.getElementById('neura-adhide')) return;
                var s = document.createElement('style');
                s.id = 'neura-adhide';
                s.innerHTML =
                    '[id*="google_ads"],[class*="google-ad"],[id*="aswift"]' +
                    ',[class*="ad-slot"],[class*="adslot"],[id*="ad-slot"]' +
                    ',[class*="ad-banner"],[class*="adsbygoogle"]' +
                    ',[class^="ad-"],[class*="-ad-"],[class$="-ad"]' +
                    ',[id^="ad-"],[id*="-ad-"],[id$="-ad"]' +
                    ',[data-ad],[data-adsbygoogle-status]' +
                    ',[class*="popunder"],[id*="popunder"]' +
                    ',[class*="sponsored"],[class*="advert"] { display: none !important; }';
                (document.head || document.documentElement).appendChild(s);
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Injected on every page finish. Hides common ad overlay/popup
     * elements and neutralises window.open for ad hosts.
     */
    fun injectAdDefense(): String = """
        (function() {
            try {
                // Neutralise ad-triggered popups
                if (!window.__neura_popup_hook) {
                    window.__neura_popup_hook = true;
                    var origOpen = window.open;
                    window.open = function(url) {
                        if (!url) return null;
                        var blocked = [
                            'popads','popcash','propellerads','onclickads','onclckds',
                            'adcash','exoclick','exosrv','juicyads','trafficjunky',
                            'trafficstars','clickadu','hilltopads','adsterra',
                            'clickaine','adnium','zeropark','voluum','ad-maven',
                            'admaven','bidvertiser','infolinks','adf.ly','sh.st',
                            'ouo.io','bc.vc','adfoc.us','doubleclick','googlesyndication',
                            'googleadservices','adservice.google'
                        ];
                        var u = String(url).toLowerCase();
                        for (var i = 0; i < blocked.length; i++) {
                            if (u.indexOf(blocked[i]) !== -1) return null;
                        }
                        return origOpen.apply(window, arguments);
                    };
                }
                // Block anchor hijacks that redirect on click
                if (!window.__neura_click_hook) {
                    window.__neura_click_hook = true;
                    document.addEventListener('click', function(e) {
                        var a = e.target && e.target.closest ? e.target.closest('a') : null;
                        if (!a) return;
                        var href = (a.getAttribute('href') || '').toLowerCase();
                        var bad = ['popads','propellerads','onclickads','exoclick',
                            'clickadu','adsterra','ad-maven','admaven','zeropark',
                            'adf.ly','sh.st','ouo.io','bc.vc','adfoc.us'];
                        for (var i = 0; i < bad.length; i++) {
                            if (href.indexOf(bad[i]) !== -1) {
                                e.preventDefault();
                                e.stopPropagation();
                                return false;
                            }
                        }
                    }, true);
                }
            } catch (e) {}
        })();
    """.trimIndent()
}
