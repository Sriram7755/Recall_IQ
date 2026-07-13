// Code Recall Assistant - Popup Orchestration Script
document.addEventListener("DOMContentLoaded", () => {
    init();
    
    // Wire up UI events
    document.getElementById("btn-login").addEventListener("click", loginWithGitHub);
    document.getElementById("btn-logout").addEventListener("click", logout);
    document.getElementById("btn-save-repo").addEventListener("click", saveRepository);
    document.getElementById("btn-change-repo").addEventListener("click", showRepoSetup);
    document.getElementById("btn-create-repo").addEventListener("click", createNewRepository);
});

function init() {
    showScreen("screen-loading");
    
    StorageService.getToken((token) => {
        if (!token) {
            showLoggedOut();
            return;
        }

        // Fetch fresh profile state from backend
        ApiService.get("/api/profile", token, (profile) => {
            // Cache updated profile info locally
            StorageService.setProfile(profile, () => {
                showLoggedIn(profile);
            });
        }, (err) => {
            console.error("Session verification failed:", err);
            logout(); // clean local state
        });
    });
}

function showScreen(screenId) {
    document.querySelectorAll(".screen").forEach(s => s.classList.remove("active"));
    document.getElementById(screenId).classList.add("active");
}

function showLoggedOut() {
    showScreen("screen-login");
    document.getElementById("profile-area").style.display = "none";
    document.getElementById("btn-logout").style.display = "none";
    document.getElementById("btn-change-repo").style.display = "none";
    
    const indicator = document.getElementById("status-indicator");
    indicator.className = "status-dot inactive";
    document.getElementById("repo-status-text").textContent = "Disconnected";
}

function showLoggedIn(profile) {
    // 1. Update Profile Area in header
    document.getElementById("user-name").textContent = profile.username;
    document.getElementById("user-avatar").src = profile.avatarUrl;
    document.getElementById("profile-area").style.display = "flex";
    document.getElementById("btn-logout").style.display = "block";

    // 2. Route screen based on Repo connectivity
    if (profile.repoConnected) {
        showScreen("screen-dashboard");
        
        // Load stats
        loadDashboardStats();
        loadRecentSubmissions();
        
        // Update footer
        const indicator = document.getElementById("status-indicator");
        indicator.className = "status-dot active";
        document.getElementById("repo-status-text").textContent = profile.activeRepo;
        document.getElementById("btn-change-repo").style.display = "block";
    } else {
        showScreen("screen-setup");
        loadRepositoriesDropdown();
        
        const indicator = document.getElementById("status-indicator");
        indicator.className = "status-dot inactive";
        document.getElementById("repo-status-text").textContent = "Repo Not Selected";
        document.getElementById("btn-change-repo").style.display = "none";
    }
}

function showRepoSetup() {
    showScreen("screen-setup");
    loadRepositoriesDropdown();
    document.getElementById("btn-change-repo").style.display = "none";
}

function loginWithGitHub() {
    const btn = document.getElementById("btn-login");
    const originalText = btn.innerHTML;
    
    // Check if chrome.identity exists (requires extension reload in chrome://extensions)
    if (!chrome.identity) {
        console.error("❌ chrome.identity API is not available.");
        alert("⚠️ Extension API not ready: Please go to 'chrome://extensions' in your browser and click the Reload button on the Code Recall Assistant extension.");
        return;
    }

    // Set loading state
    btn.disabled = true;
    btn.innerHTML = "Connecting...";

    const extensionId = chrome.runtime.id;
    console.log("🆔 Current Extension ID (chrome.runtime.id):", extensionId);
    console.log("ℹ️ Expected Extension ID:", "lhepkgngdjiiapldacaojilhdpmiemed");
    if (extensionId !== "lhepkgngdjiiapldacaojilhdpmiemed") {
        console.warn("⚠️ Installed extension ID does not match the expected ID (lhepkgngdjiiapldacaojilhdpmiemed). Ensure Render/GitHub settings support this ID.");
    }
    
    console.log("📡 Requesting login URL from backend...");
    ApiService.get(`/api/auth/login?extensionId=${extensionId}`, null, (data) => {
        console.log("🔗 Auth redirect URL obtained successfully:", data.authUrl);
        
        console.log("🚀 Launching Chrome Web Auth Flow...");
        chrome.identity.launchWebAuthFlow({
            url: data.authUrl,
            interactive: true
        }, (redirectUrl) => {
            // Re-enable button when flow completes
            btn.disabled = false;
            btn.innerHTML = originalText;

            if (chrome.runtime.lastError) {
                console.error("❌ Identity WebAuthFlow failed/cancelled:", chrome.runtime.lastError.message);
                alert("Login cancelled or failed: " + chrome.runtime.lastError.message);
                return;
            }
            
            console.log("📥 Redirect URL received from WebAuthFlow:", redirectUrl);
            
            if (!redirectUrl) {
                console.error("❌ WebAuthFlow callback received null or empty redirectUrl.");
                alert("Login failed: callback URL is empty.");
                return;
            }

            try {
                // Parse the final redirect URL (expected format: https://<extension-id>.chromiumapp.org/oauth?token=<jwt>)
                const url = new URL(redirectUrl);
                console.log("🔍 Parsing callback URL query parameters...");
                const token = url.searchParams.get("token");
                
                if (!token) {
                    console.error("❌ Token parameter not found in redirect URL search params.");
                    console.log("❓ Query string contents:", url.search);
                    alert("Login failed: token not found in the redirect callback.");
                    return;
                }

                console.log("🔑 Token successfully extracted:", token.substring(0, 10) + "..." + (token.length > 10 ? "" : ""));
                
                console.log("💾 Persisting token to storage...");
                StorageService.setToken(token, () => {
                    console.log("✅ Token saved in local storage successfully.");
                    
                    // Fetch fresh profile state from backend
                    console.log("👤 Fetching user profile from backend...");
                    ApiService.get("/api/profile", token, (profile) => {
                        console.log("✅ Profile fetched successfully:", profile);
                        
                        // Cache updated profile info locally
                        StorageService.setProfile(profile, () => {
                            console.log("✅ Profile saved in local storage successfully.");
                            init();
                        });
                    }, (err) => {
                        console.error("❌ Session verification / profile fetch failed:", err);
                        alert("Profile verification failed: " + err.message);
                        logout(); // clean local state
                    });
                });

            } catch (parseErr) {
                console.error("❌ Failed to parse redirect URL:", parseErr);
                alert("Login failed: Unable to parse the OAuth redirection URL.");
            }
        });
    }, (err) => {
        btn.disabled = false;
        btn.innerHTML = originalText;
        console.error("❌ Login initiation failed:", err);
        alert(`❌ Connection Failed:\nUnable to connect to the backend server.\n\nPlease ensure your backend application is running.`);
    });
}

function logout() {
    StorageService.clearAuth(() => {
        showLoggedOut();
    });
}

function loadRepositoriesDropdown() {
    StorageService.getToken((token) => {
        const select = document.getElementById("repo-select");
        select.innerHTML = '<option value="" disabled selected>Loading repositories...</option>';

        ApiService.get("/api/repositories", token, (repos) => {
            select.innerHTML = '';
            if (repos.length === 0) {
                select.innerHTML = '<option value="" disabled>No public repositories found</option>';
                return;
            }

            repos.forEach(repo => {
                const opt = document.createElement("option");
                opt.value = JSON.stringify({
                    repoName: repo.name,
                    fullName: repo.full_name,
                    branch: "main"
                });
                opt.textContent = `${repo.full_name} ${repo.private ? '(Private)' : '(Public)'}`;
                select.appendChild(opt);
            });
        }, (err) => {
            select.innerHTML = '<option value="" disabled>Error fetching repositories</option>';
        });
    });
}

function saveRepository() {
    const select = document.getElementById("repo-select");
    if (!select.value) {
        alert("Please select a repository!");
        return;
    }

    const repoInfo = JSON.parse(select.value);

    StorageService.getToken((token) => {
        ApiService.post("/api/repositories/select", token, repoInfo, (data) => {
            if (data.success) {
                init();
            } else {
                alert("Failed to configure repository: " + data.message);
            }
        }, (err) => {
            console.error("Save repository failed:", err);
            alert("Error configuring repository.");
        });
    });
}

function loadDashboardStats() {
    StorageService.getToken((token) => {
        ApiService.get("/api/dashboard", token, (data) => {
            document.getElementById("stats-total").textContent = data.totalSolved;
            document.getElementById("stats-easy").textContent = data.easyCount;
            document.getElementById("stats-medium").textContent = data.mediumCount;
            document.getElementById("stats-hard").textContent = data.hardCount;

            // Load topics distribution
            const topicsContainer = document.getElementById("stats-topics");
            topicsContainer.innerHTML = '';

            const topics = Object.entries(data.topicDistribution || {});
            if (topics.length === 0) {
                topicsContainer.innerHTML = '<span class="text-muted" style="font-size: 11px;">No topics recorded.</span>';
                return;
            }

            // Sort topics by solved count desc
            topics.sort((a, b) => b[1] - a[1]);

            topics.slice(0, 5).forEach(([name, count]) => {
                const badge = document.createElement("span");
                badge.className = "topic-badge";
                badge.textContent = `${name} (${count})`;
                topicsContainer.appendChild(badge);
            });
        }, (err) => {
            console.error("Fetch dashboard data failed:", err);
        });
    });
}

function loadRecentSubmissions() {
    StorageService.getToken((token) => {
        ApiService.get("/api/submissions/recent?limit=3", token, (data) => {
            const list = document.getElementById("recent-list");
            list.innerHTML = '';

            if (data.length === 0) {
                list.innerHTML = '<div class="text-muted" style="font-size: 11px; text-align: center; padding: 10px;">Solve problems on LeetCode to see them here!</div>';
                return;
            }

            data.forEach(sub => {
                const item = document.createElement("div");
                item.className = "sub-item glass";

                const diffLower = sub.difficulty.toLowerCase();

                item.innerHTML = `
                    <div class="sub-info">
                        <div class="sub-title">${sub.problemId ? sub.problemId + '.' : ''} ${sub.title}</div>
                        <div class="sub-meta">
                            <span class="diff-badge ${diffLower}">${sub.difficulty}</span>
                            <span>${sub.language}</span>
                            <span>${sub.topic}</span>
                        </div>
                    </div>
                `;
                list.appendChild(item);
            });
        }, (err) => {
            console.error("Fetch recent submissions failed:", err);
        });
    });
}

function createNewRepository() {
    const nameInput = document.getElementById("new-repo-name");
    const repoName = nameInput.value.trim();
    const isPrivate = document.getElementById("new-repo-private").checked;

    if (!repoName) {
        alert("Please enter a repository name!");
        return;
    }

    const btn = document.getElementById("btn-create-repo");
    const originalText = btn.innerHTML;

    btn.disabled = true;
    btn.innerHTML = "Creating...";

    StorageService.getToken((token) => {
        ApiService.post("/api/repositories/create", token, {
            repoName: repoName,
            private: isPrivate
        }, (data) => {
            btn.disabled = false;
            btn.innerHTML = originalText;

            if (data.success) {
                nameInput.value = "";
                init();
            } else {
                alert("Failed to create repository: " + data.message);
            }
        }, (err) => {
            btn.disabled = false;
            btn.innerHTML = originalText;
            console.error("Create repository failed:", err);
            alert("Error occurred while creating repository.");
        });
    });
}