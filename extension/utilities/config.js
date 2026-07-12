// Code Recall Assistant - Configuration Settings
const CONFIG = {
    // Backend API base URL
    // Swap "http://localhost:8080" with your production Render deployment URL (e.g. "https://code-recall-assistant.onrender.com")
    API_URL: "http://localhost:8080"
};

// Support ES6 module import, CommonJS, and plain script loading
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CONFIG;
} else if (typeof globalThis !== 'undefined') {
    globalThis.CONFIG = CONFIG;
}
