package alt_approach;

public enum StandardMessages {

    REQUEST_TIME("Hey coordinator, can you please tell me the time?"),
    ANSWER_TIME("I am daddy and I'll tell you what time it is"),
    RESPONSE_TIME("The current time is: ([0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1]) (2[0-3]|[01][0-9]):[0-5][0-9]:[0-5][0-9].[0-9]{1,3})");

    private final String text;

    StandardMessages(String text) {
        this.text = text;
    }

    public String toString() {
        return text;
    }

}
