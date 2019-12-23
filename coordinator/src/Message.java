import com.sun.istack.internal.NotNull;

public class Message {

    public Integer getSender() {
        return sender;
    }

    private final Integer sender;

    public String getText() {
        return text;
    }

    private final String text;

    public Message(int sender, String text) {
        this.sender = sender;
        this.text = text;
    }

    public boolean startsWith(@NotNull String prefix) {
        return text.startsWith(prefix);
    }

    @NotNull
    public String[] split(@NotNull String regex) {
        return text.split(regex);
    }
}
