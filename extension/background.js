importScripts('utilities/config.js');
const BACKEND_URL = CONFIG.API_URL;

console.log("⚙️ RecallIQ Background Service Worker loaded");

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
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

        return true; // Keep the message channel open for async response
    }
});
