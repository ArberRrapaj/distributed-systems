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
    private Thread listenerThread;

    public Participant(Node node, int coordinator, String coordinatorName) throws IOException {
        super(node);
        this.coordinator = coordinator;
        this.coordinatorName = coordinatorName;

        joinCluster(coordinator, coordinatorName);
    }

    public void run() {
        listenerThread = new Thread(coordTcpListener, "coordTcpListener-"+node.getName());
        listenerThread.start();
    }

    private void joinCluster(int coordinator, String coordinatorName) throws IOException {
        clusterNames = new HashMap<>();
        addToCluster(coordinator, coordinatorName);

        System.out.println("Port " + node.getPort() + " joining cluster of: " + coordinatorName + " " + coordinator);
        establishCoordConnection(coordinator);

        // TODO: handleMessagesFile()

    }


    private void establishCoordConnection(int coordinator) throws IOException {
        coordTcpWriter = new TcpWriter(node.getPort(), coordinator, this, node);
        coordTcpListener = new TcpListener(this, node, coordTcpWriter.getSocket());
    }

    public void sendMessage(String message) {
        // TODO: Implement send Message – request index + timestamp and send
        // Request index and timestamp from coordinator
        coordTcpWriter.write(StandardMessages.WANNA_SEND_MESSAGE + " " + node.name + "$" + message);
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
                        System.out.println("Added " + name + " to cluster");
                    }
                }
            }
          node.setLatestClusterSize(clusterNames.size());
        } else if (message.startsWith(StandardMessages.WANNA_SEND_RESPONSE.toString())) {
            message.sanitizeMessage(StandardMessages.WANNA_SEND_RESPONSE);
            try {
                node.multicaster.send(message.asNewMessage());
                // TODO: Do it like this or use existing listen() in multicaster?
                node.messageQueue.handleNewMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (message.startsWith(StandardMessages.CLOSE.toString())) {
            node.close();
        }


    }

    public void listenerDied(int port) {
        close();
        System.out.println("Seems like my coordTcpListener, the bastard, killed himself, so there is no need for me to be in this imperfect world anymore.");
        initiateReElection();
    }


    public void close() {
        super.close();
        if(listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        if(coordTcpListener != null) {
            coordTcpListener.close();
            coordTcpListener = null;
        }

        if (coordTcpWriter != null) {
            coordTcpWriter.close();
            coordTcpWriter.close(); // TODO: why twice?
        }
        System.out.println(node.name + ": Participant closed");
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
        new Thread(() -> node.reElection(), "reElection-pc").start();
    }
}
