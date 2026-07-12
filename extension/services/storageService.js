// Code Recall Assistant - Storage Service
const StorageService = {
    getToken: (callback) => {
        chrome.storage.local.get(["token"], (res) => callback(res.token));
    },
    setToken: (token, callback) => {
        chrome.storage.local.set({ token: token }, callback);
    },
    getProfile: (callback) => {
        chrome.storage.local.get(["profile"], (res) => callback(res.profile));
    },
    setProfile: (profile, callback) => {
        chrome.storage.local.set({ profile: profile }, callback);
    },
    clearAuth: (callback) => {
        chrome.storage.local.remove(["token", "profile"], callback);
    }
};

// Global export
globalThis.StorageService = StorageService;
