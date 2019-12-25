import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.HashMap;

public class Participant extends Role {

    private final Status status = Status.PARTICIPANT;

    private int coordinator;

    private TcpWriter coordTcpWriter;
    private TcpListener coordTcpListener;

    public Participant(Node node, Message coordAnswer) {
        super(node);
        joinCluster(coordAnswer);
    }

    private void joinCluster(Message answer) {
        clusterNames = new HashMap<>();
        coordinator = answer.getSender();
        String coordinatorName = answer.getText().split(" ")[2];
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
            // TODO: Re-Election
            System.out.println("Could not establish a connection with the coordinator. Let's trigger a re-election.");
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

        }


    }

    public void listenerDied(int port) {
        coordTcpListener.close();
        coordTcpListener = null;
        System.out.println("Seems like my coordTcpListener, the bastard, killed himself, so there is no need for me to be in this imperfect world anymore.");
        // TODO: initiateElection();
    }



    public void close() {
        super.close();

        // Close the TCP connection to the coordinator
        coordTcpWriter.close();
        coordTcpListener.close();
    }

    public Status getStatus() {
        return status;
    }
}
