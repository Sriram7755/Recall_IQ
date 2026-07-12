console.log("🔑 Initializing OAuth token processor...");

const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('token');

if (token) {
    // Save JWT token using StorageService
    StorageService.setToken(token, () => {
        console.log("✅ Token successfully saved to extension storage.");
        
        // Fetch user profile immediately using ApiService
        ApiService.get("/api/profile", token, (profile) => {
            console.log("👤 Profile metadata saved.");
            StorageService.setProfile(profile, () => {
                // Short timeout to let the user see connection success
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
} else {
    console.error("❌ Token not found in callback URL query parameters.");
    document.querySelector('h1').textContent = "Authorization Failed";
    document.querySelector('p').textContent = "No valid security token was received from the backend service. Please try logging in again.";
    const spinner = document.querySelector('.spinner');
    if (spinner) spinner.style.display = 'none';
}
