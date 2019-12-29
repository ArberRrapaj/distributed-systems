public enum Status {
    COORDINATOR("COORDINATOR ME", false, -1),
    SEARCHING("I am searching", false, -1),
    WAITING("I am waiting", false, -1),
    PARTICIPANT("PARTICIPANT", false, -1),
    REQUEST("COORDINATOR SEARCH", false, -1),
    DEAD("I am dead.", false, -1),
    ELECTION("ELECTION", true, 0),
    ADVERTISED("ELECTION", true, 1),
    EVALUATION("ELECTION", true, 2),
    ELECTED("ELECT", true, 3),
    EVALVOTES("ELECTION", true, 4);

    private final String messageRegex;
    private final boolean inElection;
    private final int electionseqNum;

    Status(String messageRegex, boolean inElection, int electionSeqNum) {
        this.messageRegex = messageRegex;
        this.inElection = inElection;
        this.electionseqNum = electionSeqNum;
    }

    public String toString() {
        return messageRegex;
    }

    public boolean isInElection() {
        return inElection;
    }

    public boolean hasAdvertised()  {
       return inElection && electionseqNum > 0;
    }

    public boolean isEvaluating() {
        return inElection && electionseqNum > 1;
    }

    public boolean hasElected() {
        return inElection && electionseqNum > 2;
    }



}
