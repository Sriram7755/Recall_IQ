# Recall IQ - Backend Service ⚙️

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-4EA94B?style=for-the-badge&logo=mongodb&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=JSON%20web%20tokens&logoColor=white)

The **Recall IQ Backend Service** is a high-performance RESTful web application built with Spring Boot 3 and Java 21. It handles user authentication, GitHub OAuth token exchange, AES-256 token encryption, LeetCode submission parsing, AI topic classification via OpenRouter, automated GitHub repository management, and data persistence with MongoDB.

---

## 🚀 Features

- **GitHub OAuth 2.0 Authentication**: Seamless authentication flow exchanging temporary authorization codes for GitHub tokens and issuing secure JWTs.
- **Token Security**: OAuth access tokens are encrypted at rest in MongoDB using AES-256 encryption.
- **Automated GitHub Integration**: Creates target repositories automatically or links existing repositories for automatic solution synchronization.
- **LeetCode Submission Ingestion**: Validates and stores solved problems, preventing duplicate entries while keeping history up-to-date.
- **AI Topic Categorization**: Categorizes problem topics using OpenRouter API integration.
- **Analytics & Dashboard API**: Provides endpoints for problem-solving statistics (Easy/Medium/Hard breakdown, topic distributions, recent submissions).

---

## 🛠️ Tech Stack

- **Framework**: Spring Boot 3.5.x
- **Language**: Java 21
- **Database**: MongoDB (Spring Data MongoDB)
- **Security**: Spring Security + JJWT (Java JWT)
- **Encryption**: AES-256
- **External APIs**: GitHub REST API, OpenRouter API
- **Build Tool**: Apache Maven

---

## 📂 Repository Structure

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/coderecall/backend/
│   │   │   ├── config/          # CORS, Security, RestClient configs
│   │   │   ├── controller/      # REST API Controllers (Auth, Dashboard, Repos, Submissions)
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   ├── model/           # MongoDB Document Entities (User, Submission, Repository, OAuthToken)
│   │   │   ├── repository/      # Spring Data MongoDB Repositories
│   │   │   ├── security/        # JWT Filter, Provider, Encryption utilities
│   │   │   └── service/         # Business logic (GitHub, Submissions, User management)
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── application-example.properties
│   │       └── application-local.properties
├── Dockerfile                   # Multi-stage containerization build
├── pom.xml                      # Maven project configuration
└── HELP.md
```

---

## 📋 Environment Configuration

Create an `.env` file or define environment variables in system/hosting environment:

| Environment Variable | Description | Default / Example |
| :--- | :--- | :--- |
| `SPRING_DATA_MONGODB_URI` | MongoDB Connection URI | `mongodb+srv://<user>:<password>@cluster.mongodb.net/recalliq` |
| `GITHUB_CLIENT_ID` | GitHub OAuth App Client ID | `your_github_client_id` |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App Client Secret | `your_github_client_secret` |
| `GITHUB_REDIRECT_URI` | OAuth Callback URL | `http://localhost:8080/api/auth/callback` |
| `JWT_SECRET` | Secret key for signing JWTs | `min_256_bit_secret_key_string` |
| `JWT_EXPIRATION_MS` | JWT expiration duration in ms | `86400000` (24 Hours) |
| `ENCRYPTION_SECRET_KEY` | 16/24/32 character key for AES token encryption | `16ByteSecretKey!` |
| `OPENROUTER_API_KEY` | OpenRouter API Key for topic tagging | `sk-or-v1-...` |
| `PORT` | HTTP Server Port | `8080` |

---

## 📡 API Reference

### 🔐 Authentication (`/api/auth`)
- `GET /api/auth/login?extensionId={id}` — Generates GitHub OAuth authorization URL carrying the Chrome extension ID state.
- `GET /api/auth/callback?code={code}&state={extensionId}` — Handles GitHub OAuth callback, exchanges token, saves encrypted credentials, and redirects back to Chrome Extension.

### 👤 Profile & Dashboard (`/api`)
- `GET /api/profile` — Retrieves current user profile, email, avatar, and active repository status.
- `GET /api/dashboard` — Returns stats breakdown: total solved, count by difficulty, and topic distribution.

### 📁 Repository Management (`/api/repositories`)
- `GET /api/repositories` — Fetches user's available repositories from GitHub.
- `POST /api/repositories/select` — Sets an existing GitHub repository as the active solution target.
- `POST /api/repositories/create` — Creates a new public/private GitHub repository and initializes it as active target.

### 📝 Submissions (`/api/submissions`)
- `POST /api/submissions` — Receives solution payload from extension, checks for duplicates, pushes to GitHub repo, and stores record in MongoDB.
- `GET /api/submissions` — Lists all submissions for the logged-in user.
- `GET /api/submissions/recent?limit={n}` — Gets the `n` most recent submissions sorted by creation time.

### 🏥 System Health (`/api/health`)
- `GET /api/health` — Service health check status endpoint.

---

## 🚦 Getting Started Locally

### Prerequisites
- **Java 21 JDK** installed (`java -version`)
- **Maven 3.8+** installed (`mvn -version`) or use bundled `./mvnw`
- **MongoDB** instance running locally or via MongoDB Atlas

### Execution Steps

1. **Clone the repository & navigate to backend directory**:
   ```bash
   cd backend
   ```

2. **Configure local properties**:
   Copy `src/main/resources/application-example.properties` to `src/main/resources/application-local.properties` and fill in your credentials.

3. **Build the application**:
   ```bash
   ./mvnw clean package -DskipTests
   ```

4. **Run the Spring Boot server**:
   ```bash
   ./mvnw spring-boot:run
   ```
   The backend service will start on `http://localhost:8080`.

---

## 🐳 Running with Docker

Build and run the containerized backend:

```bash
# Build Docker image
docker build -t recall-iq-backend .

# Run Docker container
docker run -d \
  -p 8080:8080 \
  -e SPRING_DATA_MONGODB_URI="your_mongodb_uri" \
  -e GITHUB_CLIENT_ID="your_client_id" \
  -e GITHUB_CLIENT_SECRET="your_client_secret" \
  -e JWT_SECRET="your_jwt_secret" \
  -e ENCRYPTION_SECRET_KEY="16ByteSecretKey!" \
  --name recall-iq-backend recall-iq-backend
```
