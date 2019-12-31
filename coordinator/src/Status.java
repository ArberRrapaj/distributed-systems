public enum Status {
    COORDINATOR("COORDINATOR ME", false, -1),
    SEARCHING("COORDINATOR SEARCH", false, -1),
    WAITING("I am waiting", false, -1),
    PARTICIPANT("PARTICIPANT", false, -1),
    DEAD("I am dead.", false, -1),
    ELECTION("ELECTION", true, 0),
    ADVERTISED("ELECTION", true, 1),
    EVALUATION("ELECTION", true, 2),
    ELECTED("ELECT", true, 3),
    VOTE_EVALUATION("ELECTION", true, 4);

    private final String messageRegex;
    private final boolean inElection;
    private final int electionSeqNum;

    Status(String messageRegex, boolean inElection, int electionSeqNum) {
        this.messageRegex = messageRegex;
        this.inElection = inElection;
        this.electionSeqNum = electionSeqNum;
    }

    public String toString() {
        return messageRegex;
    }

    public boolean isInElection() {
        return inElection;
    }

    public boolean hasAdvertised()  {
       return inElection && electionSeqNum > 0;
    }

    public boolean isEvaluating() {
        return inElection && electionSeqNum > 1;
    }

    public boolean hasElected() {
        return inElection && electionSeqNum > 2;
    }

    public boolean notDead() {
        return this.equals(Status.DEAD);
    }



}
