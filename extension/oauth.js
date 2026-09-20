console.log("🔑 Initializing OAuth token processor...");

const urlParams = new URLSearchParams(window.location.search);
const code = urlParams.get('code');
const tokenParam = urlParams.get('token');

const processJwtToken = (token) => {
    StorageService.setToken(token, () => {
        console.log("✅ Token successfully saved to extension storage.");
        ApiService.get("/api/profile", token, (profile) => {
            console.log("👤 Profile metadata saved.");
            StorageService.setProfile(profile, () => {
                setTimeout(() => {
                    window.close();
                }, 800);
            });
        }, (err) => {
            console.error("❌ Failed to fetch user profile:", err);
            setTimeout(() => {
                window.close();
            }, 800);
        });
    });
};

if (code) {
    console.log("🎟️ Exchanging single-use authorization code for JWT token...");
    ApiService.post("/api/auth/exchange", null, { code: code }, (res) => {
        if (res && res.success && res.token) {
            processJwtToken(res.token);
        } else {
            console.error("❌ Token exchange failed:", res ? res.message : "Empty response");
            document.querySelector('h1').textContent = "Authorization Failed";
            document.querySelector('p').textContent = res && res.message ? res.message : "Authorization exchange failed.";
            const spinner = document.querySelector('.spinner');
            if (spinner) spinner.style.display = 'none';
        }
    }, (err) => {
        console.error("❌ Token exchange request error:", err);
        document.querySelector('h1').textContent = "Authorization Failed";
        document.querySelector('p').textContent = "Unable to connect to authentication exchange endpoint.";
        const spinner = document.querySelector('.spinner');
        if (spinner) spinner.style.display = 'none';
    });
} else if (tokenParam) {
    processJwtToken(tokenParam);
} else {
    console.error("❌ Neither code nor token found in callback URL query parameters.");
    document.querySelector('h1').textContent = "Authorization Failed";
    document.querySelector('p').textContent = "No valid security token or exchange code was received from the backend service. Please try logging in again.";
    const spinner = document.querySelector('.spinner');
    if (spinner) spinner.style.display = 'none';
}
