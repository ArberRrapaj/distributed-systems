import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;

public class Participant extends Role {

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

    private void joinCluster(int coordinator, String coordinatorName) throws IOException {
        clusterNames = new HashMap<>();
        // addToCluster(coordinator, coordinatorName);

        // System.out.println("Port " + node.getPort() + "(" + node.name + ") joining cluster of: " + coordinator + "(" + coordinatorName + ")");
        establishCoordConnection(coordinator);
    }


    private void establishCoordConnection(int coordinator) throws IOException {
        coordTcpWriter = new TcpWriter(this, node);
        Socket socket = coordTcpWriter.connect(coordinator);
        coordTcpListener = new TcpListener(this, node, socket, node.getPort());
        listenerThread = new Thread(coordTcpListener, "coordTcpListener-"+node.getName());
        listenerThread.start();
        coordTcpWriter.write(StandardMessages.INTRODUCTION_PARTICIPANT + " " + node.getPort() + "$" + node.name + "$" + node.getCurrentWriteIndex() + "$" + node.lookForIndexInFile(node.getCurrentWriteIndex()));    }

    public void sendMessage(String message) {
        // TODO: Implement send Message – request index + timestamp and send
        // Request index and timestamp from coordinator
        coordTcpWriter.write(StandardMessages.WANNA_SEND_MESSAGE + " " + node.name + "$" + message);
    }

    public void actionOnMessage(Message message, boolean duringInformationExchange) {
        // Cluster Update by the Coordinator
        // Pattern joinPattern = Pattern.compile("CLUSTER \\((\\d+)\\)\\(\\( [^\\s]+ [^\\s]+\\)*\\)");
        // Matcher joinMatcher = joinPattern.matcher(message.getText());
        // if (joinMatcher.find()) {

        if (duringInformationExchange) {
            int localSenderPort = message.getSender();
            if (message.startsWith(StandardMessages.INTRODUCTION_COORDINATOR.toString())) {
                String content = message.withoutStandardPart(StandardMessages.INTRODUCTION_COORDINATOR);
                if (content.startsWith(StandardMessages.MESSAGE_BASE_GOOD.toString())) {
                    // System.out.println(node.name + ": My message base seems to be good, we gucci");
                    // we good, information is exchanged
                    coordTcpListener.informationExchanged = true;
                    coordTcpWriter.write(StandardMessages.MESSAGE_BASE_FEEDBACK_RESPONSE + " " + StandardMessages.MESSAGE_BASE_DONE);
                } else {
                    // initialize file transfer
                    int fileSize = Integer.parseInt(content.substring(StandardMessages.MESSAGE_BASE_BAD.length() + 1));
                    if (fileSize == -1) {
                        // I don't think this will happen, since pipe will break. but suicide just to be sure
                        // System.out.println(node.name + ": Seems like the coordinator had problems");
                        close();
                    } else if (fileSize == 0) {
                        // Just delete file -> sync with coordinator
                        // System.out.println(node.name + ": Coordinator told me to delete my messages");
                        node.deleteMessagesFile();
                        coordTcpWriter.write(StandardMessages.MESSAGE_BASE_FEEDBACK_RESPONSE + " " + StandardMessages.MESSAGE_BASE_DONE);
                    } else {
                        // prepare for file transfer
                        // System.out.println(node.name + ": Preparing for file transfer, my messages weren't good");

                        node.deleteMessagesFile();
                        coordTcpListener.FILE_SIZE = fileSize;
                        coordTcpListener.receivingFile = true;
                        coordTcpWriter.write(StandardMessages.MESSAGE_BASE_FEEDBACK_RESPONSE + " " + StandardMessages.MESSAGE_BASE_READY_FOR_TRANSMISSION);
                    }
                    coordTcpListener.informationExchanged = true;

                }
                // int clusterSize = Integer.parseInt(content);
                // Check current writeIndex with own
                // System.out.println(node.name + ": got introduction back from coordinator, will send out queued messages now");

                // node.messageQueue.sendQueuedMessages();

                /*
                coordTcpWriter.write(StandardMessages.INTRODUCTION_PARTICIPANT + " " + node.getPort() + "$" + node.name);
                coordTcpListener.informationExchanged = true;
                node.messageQueue.sendQueuedMessages();
                 */
            }
        } else {
            if (message.startsWith("CLUSTER")) {
                // Message format: CLUSTER <Sequenz-Nr.> <Knoten1-Name> <Knoten1-Port> <Knoten2-Name> <Knoten2-Port>
                String[] messageSplit = message.split(" ");
                int coordinatorsIndex = Integer.parseInt(messageSplit[1]);
                node.messageQueue.requestMissingMessages(coordinatorsIndex);
                // TODO: check coordinatorsIndex ?<=>? myIndex
                clusterNames.clear();

                StringBuilder currentlyOnline = new StringBuilder();

                addToCluster(coordinator, coordinatorName);
                if (messageSplit.length > 2) {
                    for (int i = 2; i < messageSplit.length; i += 2) {
                        int port = Integer.valueOf(messageSplit[i + 1]);
                        String name = messageSplit[i];
                        if(port != node.getPort()) {
                            addToCluster(port, name);
                            currentlyOnline.append(name).append(", ");
                            // System.out.println(node.name + ": Added " + name + " to cluster");
                        }
                    }
                }
                currentlyOnline.append(coordinatorName).append(".");
                // System.out.println("  #INFO: There was a change in the chatroom, there are " + (clusterNames.size() + 1) + " other people here: " + currentlyOnline.toString());
                node.setLatestClusterSize(clusterNames.size());
            } else if (message.startsWith(StandardMessages.WANNA_SEND_RESPONSE.toString())) {
                message.sanitizeMessage(StandardMessages.WANNA_SEND_RESPONSE);
                try {
                    node.multicaster.send(message.asNewMessage());
                    node.messageQueue.handleNewMessage(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (message.startsWith(StandardMessages.CLOSE.toString())) {
                node.close();
            }
        }


    }

    public void listenerDied(int port) {
        close();
        // System.out.println("Seems like my coordTcpListener, the bastard, killed himself, so there is no need for me to be in this imperfect world anymore.");
        // initiateReElection(); TODO: Why?
    }

    @Override
    public boolean informationExchanged() {
        return coordTcpListener.informationExchanged;
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
            coordTcpWriter = null;
        }
        // System.out.println(node.name + ": Participant closed");
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public void handleDeathOf(Integer port) {
        if (port.equals(coordinator)) {
            coordinator = null;
            initiateReElection();
        }
    }

    private void initiateReElection() {
        new Thread(() -> node.reElection(), "reElection-pc-"+node.getName()).start();
    }
}
