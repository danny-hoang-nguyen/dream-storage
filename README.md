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

Bot lắng nghe tại `http://localhost:8888/telegram/webhook`

### 4. Cấu hình Telegram Webhook

```bash
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://your-domain.com/telegram/webhook"
```

Dùng [ngrok](https://ngrok.com) để test local:
```bash
ngrok http 8888
```

## Lệnh đặc biệt

- `/clear` — Reset conversation history
- `/reminders` — Liệt kê các nhắc nhở đang chờ

## Tính năng Reminder

Khi bạn nhắn dạng "nhắc tôi uống nước lúc 9h tối nay" hoặc "mai 7h sáng nhắc tôi họp standup", bot sẽ:

1. Gọi Claude Haiku phân tích tin nhắn (model rẻ/nhanh) → suy luận thời điểm theo múi giờ `Asia/Ho_Chi_Minh`.
2. Lưu reminder vào file JSON (mặc định `./reminders.json`, override qua env `REMINDER_FILE_PATH`).
3. Một scheduler chạy mỗi 30s quét reminder tới hạn, gửi `⏰ Nhắc bạn: {task}` về đúng chat rồi đánh dấu đã gửi.

Nếu Claude không chắc về thời điểm (mơ hồ / đã quá khứ), tin nhắn rơi xuống luồng chat thường — không spam reminder sai.

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
├── reminder/
│   ├── Reminder.java                    # Model
│   ├── ReminderStore.java               # File JSON store (thread-safe)
│   ├── ReminderParser.java              # Gọi Claude Haiku phân tích reminder
│   └── ReminderScheduler.java           # @Scheduled 30s quét + gửi
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
└── system-prompt.txt                    # System prompt cho Claude
```

## Scripts

- `start_only.sh` — Khởi động bot từ JAR đã build (đọc `.env`, ghi log `/tmp/bot.log`, PID `/tmp/bot.pid`).
- `stop_only.sh` — Dừng bot theo PID file.
- `deploy.sh` — Build local + SSH lên remote (`root@REDACTED:/root/telegram-bot`), stop → swap JAR → start.

## Bảo mật

Tất cả secrets được đọc từ **environment variables**. Xem `.env.example` để biết danh sách biến cần set. Không commit file `.env`.
