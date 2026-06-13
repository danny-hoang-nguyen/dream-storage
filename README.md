# Telegram Claude Bot

Telegram chatbot cá nhân sử dụng Anthropic Claude API. Hỗ trợ conversation history qua Redis.

## Yêu cầu

- Java 17+
- Maven 3.8+
- Docker (chạy Redis)
- Anthropic API Key
- Telegram Bot Token (tạo tại [@BotFather](https://t.me/BotFather))

## Chạy local

### 1. Cấu hình

```bash
cp .env.example .env
# Điền ANTHROPIC_API_KEY và TELEGRAM_BOT_TOKEN vào .env
```

### 2. Chạy Redis

```bash
docker run -d -p 6379:6379 --name redis-bot redis
```

### 3. Build và chạy

```bash
export $(cat .env | grep -v '^#' | xargs)
mvn clean package -DskipTests
java -jar target/chatbot-telegram-1.0.0-SNAPSHOT.jar
```

Bot lắng nghe tại `http://localhost:8080/telegram/webhook`

### 4. Cấu hình Telegram Webhook

```bash
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://your-domain.com/telegram/webhook"
```

Dùng [ngrok](https://ngrok.com) để test local:
```bash
ngrok http 8080
```

## Lệnh đặc biệt

- `/clear` — Reset conversation history

## Tuỳ chỉnh System Prompt

System prompt (persona/behavior của bot) được tách ra file `src/main/resources/system-prompt.txt`. Sửa file rồi rebuild là xong.

Khi đã build thành JAR, file prompt nằm **bên trong JAR**. Muốn đổi prompt mà không cần rebuild, override bằng env var trỏ tới file ngoài:

```bash
export CLAUDE_SYSTEM_PROMPT_PATH=file:./system-prompt.txt
java -jar target/chatbot-telegram-1.0.0-SNAPSHOT.jar
```

Sửa `system-prompt.txt` cạnh JAR rồi restart → apply ngay. Hỗ trợ path tuyệt đối (`file:/etc/bot/system-prompt.txt`) hoặc classpath (`classpath:system-prompt.txt`, mặc định).

## Cấu trúc project

```
src/main/java/danny/project/chatbot/telegram/
├── BotApplication.java
├── controller/
│   └── TelegramWebhookController.java   # Nhận webhook từ Telegram
├── service/
│   ├── ClaudeService.java               # Gọi Anthropic API (system prompt nạp từ file)
│   ├── TelegramService.java             # Gửi message + Markdown→HTML
│   └── ConversationService.java         # Lưu history trong Redis
├── config/
│   ├── TelegramProperties.java          # Config Telegram
│   ├── RestClientConfig.java            # RestTemplate với timeout
│   └── AsyncConfig.java                 # Thread pool xử lý bất đồng bộ
└── dto/telegram/
    ├── TelegramUpdate.java
    ├── TelegramMessage.java
    ├── TelegramUser.java
    └── TelegramChat.java

src/main/resources/
├── application.yml                      # Spring config
├── application.properties               # Microsoft Bot Framework config
└── system-prompt.txt                    # System prompt cho Claude
```

## Bảo mật

Tất cả secrets được đọc từ **environment variables**. Xem `.env.example` để biết danh sách biến cần set. Không commit file `.env`.
