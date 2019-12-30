import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.Thread.getAllStackTraces;
import static java.lang.Thread.sleep;

public abstract class Elector {
    protected volatile Status status;
    private volatile Map<Integer, Integer> electionCandidates; // port : writeIndex
    private volatile Map<Integer, String> candidateNames;
    private volatile Map<Integer, Integer> electionVotes; // port : numVotes
    private Date lastElection;

    public void reElection() {
        System.out.println(getName() + " issue reElection " + getStatus());
        if(!getStatus().isInElection() && !hasRecentlyElected()
                && getStatus() != Status.DEAD) {

            this.status = Status.ELECTION;
            lastElection = new Date();

            this.electionCandidates = new HashMap<>();
            this.candidateNames = new HashMap<>();
            this.electionVotes = new HashMap<>();

            System.out.println(getName() + " start reElection " + getStatus());

            advertiseElection();
            resetResponsibilities();

            new Thread(() -> {
                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    suicide();
                }

                evaluateElectionCandidates();
            }, "evaluateElection").start();
        }
    }

    protected abstract Status getStatus();
    protected abstract void suicide();
    protected abstract void advertiseElection();
    protected abstract void resetResponsibilities();
    protected abstract int getLatestClusterSize();

    private boolean hasRecentlyElected() {
        if(lastElection == null) return false;
        if((new Date().compareTo(lastElection)) < 2000) {
            return true;
        }
        return false;
    }

    public void documentCandidate(Message received) {
        if(getStatus().isInElection()) {
            // Format: ELECTION <Name> <writeIndex>
            String[] receiveSplit = received.split(" ");
            int port = received.getSender();
            String name = receiveSplit[1];
            Integer writeIndex = Integer.parseInt(receiveSplit[2]);

            candidateNames.put(port, name);
            electionCandidates.put(port, writeIndex);
            if(electionCandidates.size() + 1 >= 0.8 * getLatestClusterSize()) {
                evaluateElectionCandidates();
            }
        }
    }

    private void evaluateElectionCandidates() {
        if(!getStatus().isEvaluating()) {
            status = Status.EVALUATION;
            Map.Entry<Integer, Integer> bestCandidate = getBestCandidate();
            Map.Entry<Integer, Integer> myCandidature = getMyCandidature();
            if(bestCandidate != null) {
                int compare = compareTwoCandidates(myCandidature, bestCandidate);
                if(compare > 0) {
                    promoteCandidate(electionCandidates.size(), myCandidature, getName());
                } else if(compare < 0) {
                    promoteCandidate(electionCandidates.size(), bestCandidate, candidateNames.get(
                            bestCandidate.getKey()));
                } else {
                    // impossible.
                }
            } else {
                try {
                    becomeCoordinator();
                } catch (IOException e) {
                    suicide();
                }
            }


            new Thread(() -> {
                try {
                    sleep(5000);
                } catch(InterruptedException e) {
                    suicide();
                }

                evaluateVotes();
            }, "evaluateVotes").start();
        }
    }

    protected abstract String getName();
    protected abstract Map.Entry<Integer,Integer> getMyCandidature();
    protected abstract void promoteCandidate(int numReceived, Map.Entry<Integer, Integer> candidate, String name);

    private int compareTwoCandidates(Map.Entry<Integer, Integer> c1, Map.Entry<Integer, Integer> c2) {
        // key = port, value = writeIndex
        if (c1.getValue().compareTo(c2.getValue()) == 0) {
            return c1.getKey().compareTo(c2.getKey());
        } else {
            return c1.getValue().compareTo(c2.getValue());
        }
    }

    private Map.Entry<Integer, Integer> getBestCandidate() {
        Optional<Map.Entry<Integer, Integer>> bestCandidateOpt = electionCandidates.entrySet().stream()
                .max((c1, c2) -> compareTwoCandidates(c1, c2));

        return bestCandidateOpt.orElse(null);
    }

    protected void documentElected(Message message) {
        // Format: ELECT <numReceived> <port> <name> <writeIndex>
        String[] msgSplit = message.split(" ");
        int numReceived = Integer.parseInt(msgSplit[1]);
        int port = Integer.parseInt(msgSplit[2]);
        String name = msgSplit[3];
        int writeIndex = Integer.parseInt(msgSplit[4]);

        if(!electionCandidates.keySet().contains(port)) {
           electionCandidates.put(port, writeIndex);
           candidateNames.put(port, name);
        }

        electionVotes.put(port, electionVotes.getOrDefault(port, 0) + 1);
        if(electionVotes.size() + 1 >= 0.8 * getLatestClusterSize()) {
            evaluateVotes();
        }
    }

    private void evaluateVotes() {
        if(getStatus().hasElected()) {
            status = Status.EVALVOTES;
            Optional<Map.Entry<Integer, Integer>> voteWinner = electionVotes.entrySet().stream()
                    .max((c1, c2) -> compareTwoCandidates(c1, c2));

            Map.Entry<Integer, Integer> winner = voteWinner.orElse(null);
            if(winner == null || winner.getKey().equals(getPort())) {
                try {
                    becomeCoordinator();
                } catch (IOException e) {
                    suicide();
                }
            } else {
                try {
                    becomeParticipant(winner.getKey(), candidateNames.getOrDefault(winner.getKey(), "UNKNOWN"));
                } catch (IOException e) {
                    waitAndRestartElection();
                }

            }
        }
    }

    private void waitAndRestartElection() {
        new Thread(() -> {
            try {
                sleep(5000);
            } catch (InterruptedException e) {
                suicide();
            }
            reElection();
        }, "reElection").start();
    }


    public abstract int getPort();
    protected abstract void becomeCoordinator() throws IOException;
    protected abstract void becomeParticipant(int coordinator, String coordinatorName) throws IOException;


}
