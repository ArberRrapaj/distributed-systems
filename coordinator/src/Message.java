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

    public Integer getIndex() {
        return index;
    }

    private Integer index;

    public String getTimestamp() {
        return timestamp;
    }

    private String timestamp;

    public String getName() {
        return name;
    }

    private String name;

    public Message(int sender, String text) {
        this.sender = sender;
        this.text = text;
    }

    public Message(int sender, int index, String name, String timestamp, String text) {
        this.sender = sender;
        this.name = name;
        this.text = text;
        this.timestamp = timestamp;
        this.index = index;
    }

    public String getLine() {
        return this.index + " " + this.sender + " " + this.timestamp + " " + this.text;
    }

    @Override
    public String toString() {
        return text;
    }

    public boolean startsWith(@NotNull String prefix) {
        return text.startsWith(prefix);
    }

    @NotNull
    public String[] split(@NotNull String regex) {
        return text.split(regex);
    }
}
