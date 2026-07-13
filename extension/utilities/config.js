// RecallIQ - Configuration Settings
const CONFIG = {
    // Backend API base URL
    API_URL: "https://recall-iq.onrender.com"
};

// Support ES6 module import, CommonJS, and plain script loading
if (typeof module !== 'undefined' && module.exports) {
    module.exports = CONFIG;
} else if (typeof globalThis !== 'undefined') {
    globalThis.CONFIG = CONFIG;
}
