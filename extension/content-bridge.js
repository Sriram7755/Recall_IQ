console.log("🚀 LeetCode content bridge loaded");

window.addEventListener("message", (event) => {
    // We only accept messages from ourselves
    if (event.source !== window) {
        return;
    }

    if (event.data && event.data.type === "LEETCODE_SUBMISSION") {
        console.log("🌉 Bridge forwarding submission to background worker...");
        chrome.runtime.sendMessage({
            type: "LEETCODE_SUBMISSION",
            submission: event.data.submission
        }, (response) => {
            if (chrome.runtime.lastError) {
                console.error("❌ Bridge fail to forward to background:", chrome.runtime.lastError.message);
            } else {
                console.log("✅ Background processed submission:", response);
            }
        });
    }
});
