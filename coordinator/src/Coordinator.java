import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class Coordinator extends Role implements Runnable {
    private Node node;
    private final Status status = Status.COORDINATOR;

    private Map<Integer, TcpWriter> clusterWriters;
    private Map<Integer, TcpListener> clusterListeners;

    private boolean listening;

    public Coordinator(Node node){
        super(node);
    }

    private void createCluster() {
        System.out.println("Coordinator " + node.getPort() + " creating new cluster...");

        clusterWriters = new HashMap<>();
        clusterListeners = new HashMap<>();

        nodeWriter.start();

        // start listening
        Thread t1 = new Thread(this);
        t1.start();
    }

    public void run() {
       listenForNewConnections();
    }

    private void listenForNewConnections() {

        listening = true;

        while (listening) {
            // Wait for incoming connects: continuously accept new TCP connections for new cluster participants
            try {
                Socket newSocket = newConnectionsSocket.accept(); // blocks until new connection
                TcpListener newTcpListener = new TcpListener(this, node, newSocket);
                clusterListeners.put(newSocket.getPort(), newTcpListener);
                TcpWriter newTcpWriter = new TcpWriter(newConnectionsSocket.getLocalPort(), newSocket, node);
                clusterWriters.put(newSocket.getPort(), newTcpWriter);

                welcomeNewNodeToCluster();

            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("Failed to accept connection");
                System.exit(1);
            }

        }
    }

    private void welcomeNewNodeToCluster() {

    }

    private void shareCurrentClusterInfo() {
        // TODO: use upon newly established or broken Socket Connnection -> Share among all
        /* Message is of format:
         CLUSTER <NAME> ...
            ... <PARTICIPANT-1-NAME> <PARTICIPANT-1-PORT> ...
            ... <PARTICIPANT-2-NAME> <PARTICIPANT-2-PORT> ...
        */
        StringBuilder answer = new StringBuilder("CLUSTER " + node.getName());
        for (Integer port : cluster.keySet()) {
            answer.append(" " + cluster.get(port) + " " + port);
        }

        for(TcpWriter writer : clusterWriters.values()) {
            writer.write(answer.toString());
        }

    }

    public void sendMessage(String message) {
        //TODO: Implement send Message – reserve index for yourself and send without request
    }

    public void actionOnMessage(Message message) {
        System.out.println("Coordinator action on " + message);

        /* from ClusterNodeListener
        if (nextLine.equals(StandardMessages.SEND_FILE_HASH.toString())) {
            clusterNode.write(StandardMessages.ANSWER_TIME.toString());
            clusterNode.write("The file's hash is: " + clusterNode.headNode.getFilesHash());
            clusterNode.handleHandshake("THANKS");
            continue;
        }
        if (nextLine.equals(StandardMessages.ANSWER_TIME.toString())) continue;
        if (nextLine.equals(StandardMessages.REQUEST_TIME.toString())) {
            clusterNode.write(StandardMessages.ANSWER_TIME.toString());
            clusterNode.write("The current time is: " + new Timestamp(new Date().getTime()));
            clusterNode.handleHandshake("THANKS");
            continue;
        }
        clusterNode.receivedMessageToWrite(nextLine);
       */

        /*
        Pattern timestampPattern = Pattern.compile("The current time is: ([0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1]) (2[0-3]|[01][0-9]):[0-5][0-9]:[0-5][0-9].[0-9]{1,3})");
        Matcher timestampMatcher = timestampPattern.matcher(message);
        if (timestampMatcher.find()) {
            // System.out.println(timestampMatcher.group(1));
            clusterNode.setTimestamp(timestampMatcher.group(1));
            return "THANKS";
        }

        Pattern fileHashPattern = Pattern.compile("The file's hash is: (.+)");
        Matcher fileHashMatcher = fileHashPattern.matcher(message);
        if (fileHashMatcher.find()) {
            // System.out.println(fileHashMatcher.group(1));
            clusterNode.setHash(fileHashMatcher.group(1));
            return "THANKS";
        }
        */

    }

    public void killClusterNode(int port) {
        TcpWriter writerToRemove = clusterWriters.get(port);
        if (writerToRemove != null) {
            writerToRemove.close();
            cluster.remove(port);
            System.out.println("Node " + port + " yote him/herself out of the party!");
            int nodesLeft = printCurrentlyConnected();
            // TODO: last node leaves the gang
            if (nodesLeft == 0) {
                // make myself coordinator
                System.out.println("https://youtu.be/0bGjlvukgHU");
                // maybe searchCluster again?
                // but check possible Thread start conflicts
            }
        } // else = not found - can this even happen?
    }

    public void close() {
        super.close();
        System.out.println("Closing...");
        listening = false;

    }
}
