public enum Status {
    COORDINATOR("COORDINATOR ME"),
    SEARCHING("I am searching"),
    WAITING("I am waiting"),
    NO_COORDINATOR("No, I am no coordinator"),
    REQUEST("I am: (\\d+); are you a coordinator\\?");
    private final String messageRegex;

    Status(String messageRegex) {
        this.messageRegex = messageRegex;
    }

    public String toString() {
        return "STATUS: " + messageRegex;
    }


}
