// Recall IQ Assistant - Popup Orchestration Script
let isLoggingIn = false;

document.addEventListener("DOMContentLoaded", () => {
    init();
    
    // Wire up UI events
    document.getElementById("btn-login").addEventListener("click", loginWithGitHub);
    document.getElementById("btn-logout").addEventListener("click", logout);
    document.getElementById("btn-save-repo").addEventListener("click", saveRepository);
    document.getElementById("btn-change-repo").addEventListener("click", showRepoSetup);
    document.getElementById("btn-create-repo").addEventListener("click", createNewRepository);
    document.getElementById("btn-refresh").addEventListener("click", refreshData);
    document.getElementById("btn-retry-login").addEventListener("click", loginWithGitHub);
    document.getElementById("btn-toggle-diag").addEventListener("click", toggleDiagnostics);
});

function init() {
    isLoggingIn = false;
    showScreen("screen-loading");
    hideLoginError();
    
    StorageService.getToken((token) => {
        if (!token) {
            console.log("[RecallIQ] No existing token found in storage.");
            showLoggedOut();
            return;
        }

        console.log("[RecallIQ] Existing token found:", token.substring(0, 10) + "...");
        
        // Fetch fresh profile state from backend to verify token validity
        console.log("[RecallIQ] Fetching user profile from backend...");
        ApiService.get("/api/profile", token, (profile) => {
            console.log("[RecallIQ] Token valid. Profile loaded:", profile.username);
            StorageService.setProfile(profile, () => {
                showLoggedIn(profile);
            });
        }, (err) => {
            console.error("[RecallIQ] Session verification failed:", err);
            // If token invalid/expired, log out cleanly
            logout();
        });
    });
}

function showScreen(screenId) {
    document.querySelectorAll(".screen").forEach(s => s.classList.remove("active"));
    document.getElementById(screenId).classList.add("active");
}

function showLoggedOut() {
    console.log("[RecallIQ] Transitioning to disconnected state...");
    isLoggingIn = false;
    
    // Clear user header
    document.getElementById("user-name").textContent = "";
    document.getElementById("user-avatar").src = "";
    document.getElementById("profile-area").style.display = "none";
    document.getElementById("btn-logout").style.display = "none";
    document.getElementById("btn-change-repo").style.display = "none";
    
    // Reset login button state
    const btn = document.getElementById("btn-login");
    const btnSpinner = document.getElementById("btn-login-spinner");
    const btnText = document.getElementById("btn-login-text");
    btn.disabled = false;
    btnSpinner.style.display = "none";
    btnText.textContent = "Connect GitHub";

    // Reset status pill
    const pill = document.getElementById("login-status-pill");
    pill.className = "status-pill disconnected";
    pill.innerHTML = '<span class="status-dot inactive"></span><span>Disconnected</span>';
    
    // Footer status
    const indicator = document.getElementById("status-indicator");
    indicator.className = "status-dot inactive";
    document.getElementById("repo-status-text").textContent = "Disconnected";

    showScreen("screen-login");
}

function showConnectingState() {
    hideLoginError();
    const btn = document.getElementById("btn-login");
    const btnSpinner = document.getElementById("btn-login-spinner");
    const btnText = document.getElementById("btn-login-text");

    btn.disabled = true;
    btnSpinner.style.display = "inline-block";
    btnText.textContent = "Opening GitHub authorization...";

    const pill = document.getElementById("login-status-pill");
    pill.className = "status-pill";
    pill.innerHTML = '<span class="btn-spinner" style="width: 10px; height: 10px; border-width: 1.5px;"></span><span>Connecting...</span>';
}

function showLoginError(title, message, isBackendUnavailable, diagInfo) {
    isLoggingIn = false;
    const btn = document.getElementById("btn-login");
    const btnSpinner = document.getElementById("btn-login-spinner");
    const btnText = document.getElementById("btn-login-text");

    btn.disabled = false;
    btnSpinner.style.display = "none";
    btnText.textContent = "Connect GitHub";

    const errorCard = document.getElementById("login-error-card");
    document.getElementById("login-error-title").textContent = title;
    document.getElementById("login-error-desc").textContent = message;

    if (diagInfo) {
        document.getElementById("diag-details").textContent = diagInfo;
        document.getElementById("btn-toggle-diag").style.display = "inline-block";
    } else {
        document.getElementById("btn-toggle-diag").style.display = "none";
        document.getElementById("diag-details").style.display = "none";
    }

    if (isBackendUnavailable) {
        errorCard.className = "alert-card warning active";
    } else {
        errorCard.className = "alert-card error active";
    }

    const pill = document.getElementById("login-status-pill");
    pill.className = "status-pill disconnected";
    pill.innerHTML = '<span class="status-dot inactive"></span><span>Connection Failed</span>';
}

function hideLoginError() {
    const errorCard = document.getElementById("login-error-card");
    errorCard.className = "alert-card";
    document.getElementById("diag-details").style.display = "none";
}

function toggleDiagnostics() {
    const diag = document.getElementById("diag-details");
    if (diag.style.display === "block") {
        diag.style.display = "none";
    } else {
        diag.style.display = "block";
    }
}

function showLoggedIn(profile) {
    console.log("[RecallIQ] Logged in as:", profile.username);
    isLoggingIn = false;
    hideLoginError();

    // 1. Update Profile Area in header
    document.getElementById("user-name").textContent = profile.username;
    document.getElementById("user-avatar").src = profile.avatarUrl;
    document.getElementById("profile-area").style.display = "flex";
    document.getElementById("btn-logout").style.display = "block";

    // 2. Route screen based on Repo connectivity
    if (profile.repoConnected) {
        showScreen("screen-dashboard");
        loadDashboardStats();
        loadRecentSubmissions();
        
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
    if (isLoggingIn) {
        console.warn("[RecallIQ OAuth] Login process already in progress. Ignoring duplicate click.");
        return;
    }

    isLoggingIn = true;
    showConnectingState();

    console.log("[RecallIQ OAuth] Requesting authentication via Background Service Worker...");
    console.log("[RecallIQ OAuth] Extension ID:", chrome.runtime.id);
    if (chrome.identity && chrome.identity.getRedirectURL) {
        console.log("[RecallIQ OAuth] Redirect URL:", chrome.identity.getRedirectURL("oauth"));
    }

    chrome.runtime.sendMessage({ type: "START_GITHUB_AUTH" }, (response) => {
        isLoggingIn = false;

        if (chrome.runtime.lastError) {
            console.error("[RecallIQ OAuth] Service Worker message error:", chrome.runtime.lastError.message);
            showLoginError(
                "Unable to connect GitHub",
                "Extension background worker unavailable. Please reload the extension.",
                true,
                `Runtime Error: ${chrome.runtime.lastError.message}`
            );
            return;
        }

        if (response && response.success && response.profile) {
            console.log("[RecallIQ OAuth] Authentication successful. Updating UI...");
            showLoggedIn(response.profile);
        } else {
            console.error("[RecallIQ OAuth] Authentication failed:", response ? response.error : "Unknown error");
            showLoginError(
                "Unable to connect GitHub",
                response && response.error ? response.error : "GitHub authorization could not be opened. Please try again.",
                response && response.isBackendUnavailable,
                response && response.details
            );
        }
    });
}

function logout() {
    console.log("[RecallIQ] Initiating logout & clearing storage...");
    isLoggingIn = false;
    StorageService.clearAuth(() => {
        showLoggedOut();
    });
}

function loadRepositoriesDropdown(callback) {
    StorageService.getToken((token) => {
        const select = document.getElementById("repo-select");
        select.innerHTML = '<option value="" disabled selected>Loading repositories...</option>';

        ApiService.get("/api/repositories", token, (repos) => {
            select.innerHTML = '';
            if (repos.length === 0) {
                select.innerHTML = '<option value="" disabled>No public/private repositories found</option>';
                if (callback) callback();
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
            if (callback) callback();
        }, (err) => {
            select.innerHTML = '<option value="" disabled>Error fetching repositories</option>';
            if (callback) callback();
        });
    });
}

function saveRepository() {
    const select = document.getElementById("repo-select");
    if (!select.value) {
        return;
    }

    const repoInfo = JSON.parse(select.value);

    StorageService.getToken((token) => {
        ApiService.post("/api/repositories/select", token, repoInfo, (data) => {
            if (data.success) {
                init();
            }
        }, (err) => {
            console.error("Save repository failed:", err);
        });
    });
}

function loadDashboardStats(callback) {
    StorageService.getToken((token) => {
        ApiService.get("/api/dashboard", token, (data) => {
            document.getElementById("stats-total").textContent = data.totalSolved;
            document.getElementById("stats-easy").textContent = data.easyCount;
            document.getElementById("stats-medium").textContent = data.mediumCount;
            document.getElementById("stats-hard").textContent = data.hardCount;

            const topicsContainer = document.getElementById("stats-topics");
            topicsContainer.innerHTML = '';

            const topics = Object.entries(data.topicDistribution || {});
            if (topics.length === 0) {
                topicsContainer.innerHTML = '<span class="text-muted" style="font-size: 11px;">No topics recorded.</span>';
                if (callback) callback();
                return;
            }

            topics.sort((a, b) => b[1] - a[1]);

            topics.slice(0, 5).forEach(([name, count]) => {
                const badge = document.createElement("span");
                badge.className = "topic-badge";
                badge.textContent = `${name} (${count})`;
                topicsContainer.appendChild(badge);
            });
            if (callback) callback();
        }, (err) => {
            console.error("Fetch dashboard data failed:", err);
            if (callback) callback();
        });
    });
}

function loadRecentSubmissions(callback) {
    StorageService.getToken((token) => {
        ApiService.get("/api/submissions/recent?limit=3", token, (data) => {
            const list = document.getElementById("recent-list");
            list.innerHTML = '';

            if (!data || data.length === 0) {
                list.innerHTML = '<div class="text-muted" style="font-size: 11px; text-align: center; padding: 8px;">Solve problems on LeetCode to see them here!</div>';
                if (callback) callback();
                return;
            }

            data.forEach(sub => {
                const item = document.createElement("div");
                item.className = "sub-item glass";
                const diffLower = sub.difficulty ? sub.difficulty.toLowerCase() : "easy";

                item.innerHTML = `
                    <div class="sub-info">
                        <div class="sub-title">${sub.problemId ? sub.problemId + '.' : ''} ${sub.title || 'Untitled Problem'}</div>
                        <div class="sub-meta">
                            <span class="diff-badge ${diffLower}">${sub.difficulty || 'Easy'}</span>
                            <span>${sub.language || 'Code'}</span>
                            <span>${sub.topic || ''}</span>
                        </div>
                    </div>
                `;
                list.appendChild(item);
            });
            if (callback) callback();
        }, (err) => {
            console.error("Fetch recent submissions failed:", err);
            if (callback) callback();
        });
    });
}

function createNewRepository() {
    const nameInput = document.getElementById("new-repo-name");
    const repoName = nameInput.value.trim();
    const isPrivate = document.getElementById("new-repo-private").checked;

    if (!repoName) {
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
            }
        }, (err) => {
            btn.disabled = false;
            btn.innerHTML = originalText;
            console.error("Create repository failed:", err);
        });
    });
}

function refreshData() {
    const btn = document.getElementById("btn-refresh");
    if (btn) {
        btn.classList.add("spinning");
        btn.disabled = true;
    }

    StorageService.getToken((token) => {
        if (!token) {
            if (btn) {
                btn.classList.remove("spinning");
                btn.disabled = false;
            }
            return;
        }

        ApiService.get("/api/profile", token, (profile) => {
            StorageService.setProfile(profile, () => {
                document.getElementById("user-name").textContent = profile.username;
                document.getElementById("user-avatar").src = profile.avatarUrl;

                const finish = () => {
                    if (btn) {
                        btn.classList.remove("spinning");
                        btn.disabled = false;
                    }
                };

                if (profile.repoConnected) {
                    document.getElementById("repo-status-text").textContent = profile.activeRepo;
                    
                    let pending = 2;
                    const done = () => {
                        pending--;
                        if (pending === 0) finish();
                    };
                    loadDashboardStats(done);
                    loadRecentSubmissions(done);
                } else {
                    loadRepositoriesDropdown(finish);
                }
            });
        }, (err) => {
            console.error("Refresh failed:", err);
            if (btn) {
                btn.classList.remove("spinning");
                btn.disabled = false;
            }
        });
    });
}