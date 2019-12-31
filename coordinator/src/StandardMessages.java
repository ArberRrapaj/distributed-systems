public enum StandardMessages {

    NEW_MESSAGE("NEWMSG"),
    REQUEST_MESSAGE("NEEDMSG"),
    REQUEST_MESSAGE_ANSWER("HRYOUGO"),
    WANNA_SEND_MESSAGE("WANNASNDMSG"),
    WANNA_SEND_RESPONSE("PLSSNDTHISMSG"),
    CLOSE("PLSKLLYRSLF"),
    INTRODUCTION_PARTICIPANT("HELLOMYNAMEIS"),
    INTRODUCTION_COORDINATOR("HELLOMEISCOORDBOILOOKHOWMNYNDSIHV");

    private final String text;

    StandardMessages(String text) { this.text = text; }

    public String toString() {
        return text;
    }

    public int length() {
        return text.length();
    }


}
