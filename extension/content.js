console.log("🚀 LeetCode content script loaded");

let currentUrl = location.href;
let currentSubmitButton = null;
let resultObserver = null;
let submissionInProgress = false;

// =====================================
// Extract Code From Monaco
// =====================================
function extractCode() {
    try {
        const models = window.monaco?.editor?.getModels() || [];

        if (models.length === 0) {
            console.warn("❌ Monaco models not found");
            return "";
            
        }

        return models
            .map(m => m.getValue())
            .sort((a, b) => b.length - a.length)[0];

    } catch (err) {
        console.error("❌ Monaco extraction failed:", err);
        return "";
    }
}

// =====================================
// Detect Language
// =====================================
function detectLanguage() {

    const raw = [...document.querySelectorAll("*")]
        .find(el =>
            /^(C\+\+|Java|Python|Python3|Go|Rust|JavaScript|TypeScript|C#|Kotlin|Swift|PHP|Dart|Scala|Elixir|Erlang|Racket)/
                .test(el.innerText)
        )?.innerText.trim();

    const match = raw?.match(
        /^(C\+\+|Java|Python|Python3|Go|Rust|JavaScript|TypeScript|C#|Kotlin|Swift|PHP|Dart|Scala|Elixir|Erlang|Racket)/
    );

    return match ? match[1] : "Unknown";
}

// =====================================
// Detect Difficulty
// =====================================
function detectDifficulty() {

    const el = [...document.querySelectorAll("*")]
        .find(el =>
            /^(Easy|Medium|Hard)$/i.test(
                el.textContent.trim()
            )
        );

    return el
        ? el.textContent.trim()
        : "Unknown";
}

// =====================================
// Extract Description
// =====================================
function extractDescription() {

    const descriptionElement =
        document.querySelector(
            '[data-track-load="description_content"]'
        );

    return descriptionElement
        ? descriptionElement.innerText.trim()
        : "";
}

// =====================================
// Extract Topics
// =====================================
function extractTopics() {

    return [...document.querySelectorAll('a[href^="/tag/"]')]
        .map(tag => tag.innerText.trim())
        .filter(Boolean);
}

// =====================================
// Build Submission Object
// =====================================
function getLeetCodeSubmissionData(capturedCode) {

    if (!capturedCode) {
        return null;
    }

    const slug =
        window.location.pathname.split("/")[2];

    const titleElement =
        document.querySelector(
            `a[href="/problems/${slug}/"]`
        );

    let title = slug
        .split("-")
        .map(word =>
            word.charAt(0).toUpperCase() +
            word.slice(1)
        )
        .join(" ");

    let problemId = "";

    if (titleElement) {

        const fullTitle =
            titleElement.textContent.trim();

        problemId =
            fullTitle.match(/^\d+/)?.[0] || "";

        title =
            fullTitle.replace(
                /^\d+\.\s*/,
                ""
            );
    }

    return {
        problemId,
        title,
        slug,
        difficulty: detectDifficulty(),
        language: detectLanguage(),
        platform: "LeetCode",
        description: extractDescription(),
        leetcodeTopics: extractTopics(),
        code: capturedCode
    };
}

// =====================================
// Send To Backend
// =====================================
function sendSubmissionToBackend(submission) {
    console.log("📤 Sending Submission to bridge script...");
    window.postMessage({
        type: "LEETCODE_SUBMISSION",
        submission: submission
    }, "*");
}

// =====================================
// Observe Judge Result
// =====================================
function waitForResultWithObserver(
    capturedCode
) {

    if (resultObserver) {
        return;
    }

    console.log(
        "👀 Waiting for submission result..."
    );

    resultObserver =
        new MutationObserver(() => {

            const pageText =
                document.body.innerText;

            const match =
                pageText.match(
                    /(Accepted|Wrong Answer|Runtime Error|Time Limit Exceeded|Compilation Error|Memory Limit Exceeded)/i
                );

            if (!match) {
                return;
            }

            const status = match[0];

            console.log(
                "🏁 Result:",
                status
            );

            if (
                status === "Accepted"
            ) {

                const submission =
                    getLeetCodeSubmissionData(
                        capturedCode
                    );

                if (submission) {

                    submission.status =
                        status;

                    submission.submittedAt =
                        new Date()
                            .toISOString();

                    sendSubmissionToBackend(
                        submission
                    );
                }
            }

            resultObserver.disconnect();
            resultObserver = null;
            submissionInProgress = false;
        });

    resultObserver.observe(
        document.body,
        {
            childList: true,
            subtree: true
        }
    );
}

// =====================================
// Attach Submit Listener
// =====================================
function attachSubmitListener() {

    const submitBtn =
        [...document.querySelectorAll("button")]
            .find(btn =>
                btn.textContent.trim() ===
                "Submit"
            );

    if (!submitBtn) {
        return;
    }

    if (
        currentSubmitButton === submitBtn
    ) {
        return;
    }

    currentSubmitButton =
        submitBtn;

    console.log(
        "✅ Submit button detected"
    );

    submitBtn.addEventListener(
        "click",
        () => {

            if (
                submissionInProgress
            ) {
                return;
            }

            submissionInProgress =
                true;

            console.log(
                "🟡 Submit clicked"
            );

            const capturedCode =
                extractCode();

            waitForResultWithObserver(
                capturedCode
            );
        }
    );
}

// =====================================
// Watch DOM Changes
// =====================================
function monitorSubmitButton() {

    const observer =
        new MutationObserver(() => {

            attachSubmitListener();

        });

    observer.observe(
        document.body,
        {
            childList: true,
            subtree: true
        }
    );
}

// =====================================
// Detect SPA Navigation
// =====================================
setInterval(() => {

    if (
        location.href !== currentUrl
    ) {

        currentUrl =
            location.href;

        currentSubmitButton =
            null;

        console.log(
            "🔄 URL changed:",
            currentUrl
        );

        setTimeout(() => {

            attachSubmitListener();

        }, 1000);
    }

}, 1000);

// =====================================
// Start Extension
// =====================================
monitorSubmitButton();
attachSubmitListener();