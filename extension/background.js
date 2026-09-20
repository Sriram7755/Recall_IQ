importScripts('utilities/config.js');
const BACKEND_URL = CONFIG.API_URL;

console.log("⚙️ RecallIQ Background Service Worker loaded");

let isAuthPending = false;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === "START_GITHUB_AUTH") {
        if (isAuthPending) {
            console.warn("[RecallIQ OAuth] OAuth flow already pending. Ignoring duplicate request.");
            sendResponse({ success: false, error: "Authentication flow already in progress." });
            return true;
        }

        const extensionId = chrome.runtime.id;
        const redirectUrlPattern = chrome.identity.getRedirectURL("oauth");

        console.log("[RecallIQ OAuth] Starting OAuth flow from Background Service Worker.");
        console.log("[RecallIQ OAuth] Extension ID:", extensionId);
        console.log("[RecallIQ OAuth] Redirect URL:", redirectUrlPattern);

        isAuthPending = true;

        // Clear existing storage before starting fresh login
        chrome.storage.local.remove(["token", "profile"], () => {
            fetch(`${BACKEND_URL}/api/auth/login?extensionId=${extensionId}`)
                .then(res => {
                    if (!res.ok) {
                        throw new Error(`Backend login endpoint HTTP ${res.status}`);
                    }
                    return res.json();
                })
                .then(data => {
                    if (!data || !data.authUrl) {
                        throw new Error("Backend returned empty authorization URL");
                    }

                    const authUrl = data.authUrl;
                    console.log("========== RECALLIQ OAUTH DEBUG ==========");
console.log("AUTH URL:", authUrl);
console.log("==========================================");
                    try {
    const parsedUrl = new URL(authUrl);

    console.log("[RecallIQ OAuth] OAuth URL host:", parsedUrl.hostname);
    console.log("[RecallIQ OAuth] OAuth URL pathname:", parsedUrl.pathname);
    console.log("[RecallIQ OAuth] OAuth client_id:", parsedUrl.searchParams.get("client_id"));
    console.log("[RecallIQ OAuth] OAuth redirect_uri:", parsedUrl.searchParams.get("redirect_uri"));
    console.log("[RecallIQ OAuth] OAuth scope:", parsedUrl.searchParams.get("scope"));
    console.log("[RecallIQ OAuth] OAuth state present:", parsedUrl.searchParams.has("state"));
    console.log(
        "[RecallIQ OAuth] OAuth state length:",
        parsedUrl.searchParams.get("state")?.length
    );
} catch (e) {
    console.error("[RecallIQ OAuth] Invalid auth URL structure");
}

                    console.log("[RecallIQ OAuth] Launching chrome.identity.launchWebAuthFlow...");
                    return new Promise((resolve, reject) => {
                        chrome.identity.launchWebAuthFlow({
                            url: authUrl,
                            interactive: true
                        }, (redirectUrl) => {
                            if (chrome.runtime.lastError) {
                                reject(new Error(chrome.runtime.lastError.message || "Authorization page could not be loaded."));
                            } else if (!redirectUrl) {
                                reject(new Error("Callback URL was empty."));
                            } else {
                                resolve(redirectUrl);
                            }
                        });
                    });
                })
                .then(redirectUrl => {
                    console.log("[RecallIQ OAuth] WebAuthFlow returned callback URL.");
                    const url = new URL(redirectUrl);
                    const code = url.searchParams.get("code");
                    const tokenParam = url.searchParams.get("token");

                    if (code) {
                        console.log("[RecallIQ OAuth] Exchanging single-use auth code for JWT...");
                        return fetch(`${BACKEND_URL}/api/auth/exchange`, {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ code: code })
                        })
                        .then(res => {
                            if (!res.ok) {
                                throw new Error(`Code exchange HTTP ${res.status}`);
                            }
                            return res.json();
                        })
                        .then(data => {
                            if (data && data.success && data.token) {
                                return data.token;
                            } else {
                                throw new Error(data ? data.message : "Code exchange failed.");
                            }
                        });
                    } else if (tokenParam) {
                        return tokenParam;
                    } else {
                        throw new Error("Neither code nor token present in callback URL.");
                    }
                })
                .then(jwtToken => {
                    console.log("[RecallIQ OAuth] Token obtained. Persisting to storage and fetching profile...");
                    return new Promise((resolve, reject) => {
                        chrome.storage.local.set({ token: jwtToken }, () => {
                            fetch(`${BACKEND_URL}/api/profile`, {
                                headers: { "Authorization": `Bearer ${jwtToken}` }
                            })
                            .then(res => {
                                if (!res.ok) {
                                    throw new Error(`Profile endpoint HTTP ${res.status}`);
                                }
                                return res.json();
                            })
                            .then(profile => {
                                chrome.storage.local.set({ profile: profile }, () => {
                                    resolve(profile);
                                });
                            })
                            .catch(reject);
                        });
                    });
                })
                .then(profile => {
                    isAuthPending = false;
                    console.log("[RecallIQ OAuth] OAuth authentication complete for user:", profile.username);
                    sendResponse({ success: true, profile: profile });
                })
                .catch(err => {
                    isAuthPending = false;
                    console.error("[RecallIQ OAuth] OAuth flow failed:", err.message);
                    const isBackendUnavailable = err.message.includes("HTTP") || err.message.includes("fetch");
                    sendResponse({
                        success: false,
                        error: isBackendUnavailable ? "Recall IQ could not reach the authentication server. Please try again in a moment." : "GitHub authorization could not be opened. Please try again.",
                        isBackendUnavailable: isBackendUnavailable,
                        details: err.message
                    });
                });
        });

        return true; // Keep sendResponse message channel open for async response
    }

    if (message.type === "LEETCODE_SUBMISSION") {
        console.log("📨 Received submission from content script bridge:", message.submission.title);

        chrome.storage.local.get(["token"], (result) => {
            const token = result.token;
            if (!token) {
                console.warn("⚠️ No JWT token found. User is not authenticated.");
                sendResponse({ success: false, error: "Please log in via the extension popup to sync submissions." });
                return;
            }

            fetch(`${BACKEND_URL}/api/submissions`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${token}`
                },
                body: JSON.stringify(message.submission)
            })
            .then(res => {
                if (!res.ok) {
                    throw new Error(`HTTP Error ${res.status}`);
                }
                return res.json();
            })
            .then(data => {
                console.log("✅ Submission synced successfully to backend and GitHub:", data);
                sendResponse({ success: true, data: data });
            })
            .catch(err => {
                console.error("❌ Error syncing submission to backend:", err);
                sendResponse({ success: false, error: err.message });
            });
        });

        return true;
    }
});
