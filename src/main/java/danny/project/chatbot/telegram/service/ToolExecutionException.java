package danny.project.chatbot.telegram.service;

public class ToolExecutionException extends RuntimeException {

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ToolExecutionException(String message) {
        super(message);
    }
}
