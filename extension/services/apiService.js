// Code Recall Assistant - API Service
const ApiService = {
    get: (endpoint, token, callback, errorCallback) => {
        const headers = {};
        if (token) {
            headers["Authorization"] = `Bearer ${token}`;
        }

        fetch(`${CONFIG.API_URL}${endpoint}`, { headers })
        .then(res => {
            if (!res.ok) {
                throw new Error(`HTTP error! status: ${res.status}`);
            }
            return res.json();
        })
        .then(callback)
        .catch(errorCallback);
    },

    post: (endpoint, token, body, callback, errorCallback) => {
        const headers = {
            "Content-Type": "application/json"
        };
        if (token) {
            headers["Authorization"] = `Bearer ${token}`;
        }

        fetch(`${CONFIG.API_URL}${endpoint}`, {
            method: "POST",
            headers: headers,
            body: JSON.stringify(body)
        })
        .then(res => {
            if (!res.ok) {
                throw new Error(`HTTP error! status: ${res.status}`);
            }
            return res.json();
        })
        .then(callback)
        .catch(errorCallback);
    }
};

// Global export
globalThis.ApiService = ApiService;
