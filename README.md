# Backend Dev Assistant — Teams Bot

Chatbot cho backend developer trên MS Teams. Tự động search **Coda docs** và **Jira tickets** liên quan trước khi trả lời câu hỏi kỹ thuật.

## Yêu cầu

- Java 17+
- Maven 3.8+
- Docker (chạy Redis)
- Anthropic API Key
- Coda API Token
- Jira API Token

## Chạy local

### 1. Cấu hình

```bash
cp .env.example .env
# Điền các API keys vào .env
```

### 2. Chạy Redis

```bash
docker run -d -p 6379:6379 --name redis-bot redis
```

### 3. Build và chạy

```bash
export $(cat .env | grep -v '^#' | xargs)
mvn clean package -DskipTests
java -jar target/teams-troublebot-1.0.0-SNAPSHOT.jar
```

Bot lắng nghe tại `http://localhost:3978/api/messages`

### 4. Test với Bot Framework Emulator

Tải [Bot Framework Emulator](https://github.com/microsoft/BotFramework-Emulator/releases), kết nối tới `http://localhost:3978/api/messages` (để trống App ID/Password).

## Cấu hình Coda

```env
CODA_API_TOKEN=xxx        # Bắt buộc
CODA_DOC_ID=              # Optional: giới hạn search trong 1 doc cụ thể
                          # Lấy từ URL: coda.io/d/DocName_dABCDEFG → ABCDEFG
```

## Cấu hình Jira

```env
JIRA_BASE_URL=https://yourcompany.atlassian.net
JIRA_USERNAME=your-email@company.com
JIRA_API_TOKEN=xxx        # Tạo tại atlassian.com/account/api-tokens
JIRA_PROJECT_KEY=BACKEND  # Optional: giới hạn search trong project cụ thể
```

## Luồng xử lý mỗi message

Bot dùng **tool-calling** (agentic loop): Claude tự quyết định khi nào cần search.

```
User gửi message
      │
      └─► Claude API (system prompt + history + tools)
                │
                ├─ (nếu cần) gọi tool search_coda  ─► CodaService.search()
                ├─ (nếu cần) gọi tool search_jira  ─► JiraService.search()
                │      └─ kết quả tool quay lại Claude (lặp tối đa 10 vòng)
                │
                └─► Trả lời có link Coda + Jira key liên quan
```

Việc xử lý chạy bất đồng bộ trên thread pool riêng (`botTaskExecutor`) để
không block thread của Bot Framework adapter.

## Lệnh đặc biệt

- `/clear` — Reset conversation history

## Cấu trúc project

```
src/main/java/com/flownetworks/bot/
├── BotApplication.java
├── bot/TroubleshootingBot.java      # Nhận/gửi message Teams (async)
├── service/
│   ├── ClaudeService.java           # Gọi Claude + agentic tool loop
│   ├── CodaService.java             # Search Coda API
│   ├── JiraService.java             # Search Jira REST API v3 (/search/jql)
│   └── ConversationService.java     # Lưu history Redis
├── config/
│   ├── BotConfig.java               # Bot Framework adapter bean
│   ├── TenantAwareBotAdapter.java   # Set tenant cho single-tenant Azure Bot
│   ├── RestClientConfig.java        # RestTemplate dùng chung + timeout
│   └── AsyncConfig.java             # Thread pool cho xử lý message
└── dto/
    ├── CodaDoc.java
    ├── CodaSearchResponse.java
    ├── CodaPageSearchResponse.java
    ├── JiraIssue.java
    └── JiraSearchResponse.java
```

## Bảo mật

Tất cả secrets (API keys, tokens, app password) được đọc từ **environment
variables** — không hardcode trong source. Xem `.env.example` để biết danh
sách biến cần set. Đừng commit file `.env` (đã có trong `.gitignore`).
