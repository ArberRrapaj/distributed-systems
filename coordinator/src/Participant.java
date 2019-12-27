import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.HashMap;

public class Participant extends Role implements Runnable {

    private final Status status = Status.PARTICIPANT;

    private Integer coordinator;
    private String coordinatorName;

    private TcpWriter coordTcpWriter;
    private TcpListener coordTcpListener;

    public Participant(Node node, int coordinator, String coordinatorName) {
        super(node);
        this.coordinator = coordinator;
        this.coordinatorName = coordinatorName;
    }

    public void run() {
        joinCluster(coordinator, coordinatorName);
    }

    private void joinCluster(int coordinator, String coordinatorName) {
        clusterNames = new HashMap<>();
        addToCluster(coordinator, coordinatorName);

        System.out.println("Port " + node.getPort() + " joining cluster of: " + coordinatorName + " " + coordinator);
        establishCoordConnection(coordinator);

        // TODO: handleMessagesFile()

    }

    private void establishCoordConnection(int coordinator) {
        try {
            coordTcpWriter = new TcpWriter(node.getPort(), coordinator, this, node);
            coordTcpListener = new TcpListener(this, node, coordTcpWriter.getSocket());
            coordTcpListener.start();
        } catch(IOException e) {
            // Cannot connect to Coordinator -> Re-Election
            System.out.println("Could not establish a connection with the coordinator. Let's trigger a re-election.");
            initiateReElection();
        }
    }

    public void sendMessage(String message) {
        //TODO: Implement send Message – request index + timestamp and send
    }

    public void actionOnMessage(Message message) {
        // Cluster Update by the Coordinator
        // Pattern joinPattern = Pattern.compile("CLUSTER \\((\\d+)\\)\\(\\( [^\\s]+ [^\\s]+\\)*\\)");
        // Matcher joinMatcher = joinPattern.matcher(message.getText());
        // if (joinMatcher.find()) {
        if (message.startsWith("CLUSTER")) {
            // Message format: CLUSTER <Sequenz-Nr.> <Knoten1-Name> <Knoten1-Port> <Knoten2-Name> <Knoten2-Port>
            String[] messageSplit = message.split(" ");
            int coordinatorsIndex = Integer.parseInt(messageSplit[1]);
            // TODO: check coordinatorsIndex ?<=>? myIndex

            if (messageSplit.length > 2) {
                for (int i = 2; i < messageSplit.length; i += 2) {
                    int port = Integer.valueOf(messageSplit[i + 1]);
                    String name = messageSplit[i];
                    if(port != node.getPort()) {
                        addToCluster(port, name);
                    }
                }
            }

            node.setLatestClusterSize(clusterNames.size());
        }


    }

    public void listenerDied(int port) {
        close();
        System.out.println("Seems like my coordTcpListener, the bastard, killed himself, so there is no need for me to be in this imperfect world anymore.");
        initiateReElection();
    }


    public void close() {
        super.close();

        if(coordTcpListener != null) {
            coordTcpListener.close();
            coordTcpListener = null;
        }

        if(coordTcpWriter != null) {
            coordTcpWriter.close();
            coordTcpWriter.close();
        }
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public void handleDeathOf(Integer port) {
        if(coordinator.equals(port)) {
            coordinator = null;
            initiateReElection();
        } else {
            clusterNames.remove(port);
        }
    }

    private void initiateReElection() {
        node.reElection();
    }
}
