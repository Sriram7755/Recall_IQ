// Code Recall Assistant - Footer Component
document.addEventListener("DOMContentLoaded", () => {
    const footerElement = document.querySelector("footer");
    if (!footerElement) return;

    // Check if the branding division is already present
    if (document.getElementById("sri-footer-brand")) return;

    const brandDiv = document.createElement("div");
    brandDiv.id = "sri-footer-brand";
    brandDiv.style.textAlign = "center";
    brandDiv.style.width = "100%";
    brandDiv.style.borderTop = "1px solid var(--border-glass)";
    brandDiv.style.paddingTop = "8px";
    brandDiv.style.marginTop = "8px";
    brandDiv.style.display = "flex";
    brandDiv.style.flexDirection = "column";
    brandDiv.style.gap = "4px";
    brandDiv.style.fontSize = "10px";
    brandDiv.style.color = "var(--text-muted)";

    brandDiv.innerHTML = `
        <div style="font-weight: 500; color: var(--text-secondary);">Developed with Integrity and Innovation</div>
        <div>Powered by <span style="font-weight: 600; color: var(--primary);">Sriii Technologies</span> • Version 1.0.0</div>
        <div style="display: flex; justify-content: center; gap: 8px; margin-top: 4px;">
            <a href="#" id="link-about" style="color: var(--text-secondary); text-decoration: none; font-weight: 500;">About</a> |
            <a href="#" id="link-privacy" style="color: var(--text-secondary); text-decoration: none; font-weight: 500;">Privacy</a> |
            <a href="#" id="link-terms" style="color: var(--text-secondary); text-decoration: none; font-weight: 500;">Terms</a> |
            <a href="#" id="link-security" style="color: var(--text-secondary); text-decoration: none; font-weight: 500;">Security</a>
        </div>
    `;

    footerElement.appendChild(brandDiv);

    // Apply layout overrides to accommodate branding content
    footerElement.style.flexDirection = "column";
    footerElement.style.height = "auto";
    footerElement.style.gap = "8px";
    footerElement.style.paddingBottom = "12px";

    // Bind navigation click handlers to launch policies in full browser tabs
    const links = {
        "link-about": "pages/about.html",
        "link-privacy": "pages/privacy.html",
        "link-terms": "pages/terms.html",
        "link-security": "pages/security.html"
    };

    Object.entries(links).forEach(([id, path]) => {
        const el = document.getElementById(id);
        if (el) {
            el.addEventListener("click", (e) => {
                e.preventDefault();
                chrome.tabs.create({ url: chrome.runtime.getURL(path) });
            });
        }
    });
});
