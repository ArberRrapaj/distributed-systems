import com.sun.istack.internal.NotNull;

public class Message {

    public Integer getSender() {
        return sender;
    }
    private final Integer sender;

    public String getText() {
        return text;
    }
    public void setText(String text) { this.text = text; }
    private String text;

    public Integer getIndex() {
        return index;
    }
    public void setIndex(int index) { this.index = index; }
    private Integer index;

    public String getTimestamp() {  return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    private String timestamp;

    public String getName() {
        return name;
    }
    public void setName(String name) { this.name = name; }
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
        return this.index + " " + getName() + " " + this.timestamp + " " + this.text;
    }
    public String getStandardLine() { return this.index + "$" + getName() + "$" + this.timestamp + "$" + this.text; }

    @Override
    public String toString() {
        return text;
    }

    public boolean startsWith(@NotNull String prefix) {
        return text.startsWith(prefix);
    }

    public String asNewMessage() {
        return StandardMessages.NEW_MESSAGE.toString() + " " + getStandardLine();
    }
    public String asRequestedMessage() { return asStandardMessage(StandardMessages.REQUEST_MESSAGE_ANSWER); }

    public String asWannaSendResponse() {
        return StandardMessages.WANNA_SEND_RESPONSE.toString() + " " + getStandardLine();
    }

    private String asStandardMessage(StandardMessages standardMessage) {
        return standardMessage.toString() + " " + getStandardLine();
    }

    public String withoutStandardPart(StandardMessages standardMessage) {
        return toString().substring(standardMessage.length() + 1);
    }

    public String asChatMessage() {
        return this.timestamp.split(" ", 2)[1] + " @" + this.name + ": " + this.text;
    }

    public Message sanitizeMessage(StandardMessages standardMessage) {
        String[] messageSplit = getText().substring(standardMessage.length() + 1).split("\\$", 4);
        setName(messageSplit[1]);
        setText(messageSplit[3]);

        setIndex(Integer.parseInt(messageSplit[0]));
        setTimestamp(messageSplit[2]);
        return this;
    }

    @NotNull
    public String[] split(@NotNull String regex) {
        return text.split(regex);
    }

    public String fileLineToRequested() {
        String[] messageSplit = getText().split(" ", 5);
        setName(messageSplit[1]);
        setText(messageSplit[4]);

        setIndex(Integer.parseInt(messageSplit[0]));
        setTimestamp(messageSplit[2] + " " + messageSplit[3]);
        return asRequestedMessage();
    }
}
