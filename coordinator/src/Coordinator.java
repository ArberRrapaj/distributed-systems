import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Thread.sleep;

public class Coordinator extends Role implements Runnable {
    private final Status status = Status.COORDINATOR;

    private ServerSocket newConnectionsSocket;
    private Map<Integer, TcpWriter> clusterWriters;
    Map<Integer, TcpListener> clusterListeners;
    private Map<Integer, Thread> listenerThreads;

    private volatile boolean listening;

    public Coordinator(Node node) throws ConnectException {
        super(node);

        try {
            setupSocketServer();
        } catch (IOException e) {
            try {
                sleep(2000);
                setupSocketServer();
            } catch (InterruptedException | IOException e1) {
                System.err.println("There was an error setting up the newConnectionsSocket");
                e.printStackTrace();
                node.suicide();
                throw new ConnectException(e.getMessage());
            }
        }

        createCluster();
    }

    private void setupSocketServer() throws IOException {
        newConnectionsSocket = new ServerSocket(node.getPort());
    }

    private void createCluster() {
        System.out.println("Coordinator " + node.getPort() + " creating new cluster...");

        clusterWriters = new HashMap<>();
        clusterListeners = new HashMap<>();
        listenerThreads = new HashMap<>();

    }

    public void run() {
       listenForNewConnections();
    }

    private void listenForNewConnections() {

        listening = true;

        while (listening) {
            // Wait for incoming connects: continuously accept new TCP connections for new cluster participants
            try {
                System.out.println("I'll be listening for new connections on port: " + node.getPort());
                Socket newSocket = newConnectionsSocket.accept(); // blocks until new connection
                System.out.println("New Connection: " + newSocket);
                int port = newSocket.getPort();
                TcpListener newTcpListener = new TcpListener(this, node, newSocket, port);

                clusterListeners.put(port, newTcpListener);
                listenerThreads.put(port, new Thread(newTcpListener, "TcpListener-"+node.getName()+"-"+port));
                listenerThreads.get(port).start();
                TcpWriter newTcpWriter = new TcpWriter(node.getPort(), newSocket, node);
                clusterWriters.put(port, newTcpWriter);

                welcomeNewNodeToCluster();

            } catch (IOException e) {
                node.suicide();
            }

        }
    }

    private void welcomeNewNodeToCluster() {
        shareUpdatedClusterInfo();
    }

    private String getCurrentClusterInfo() {
        // TODO: use upon newly established or broken Socket Connnection -> Share among all
        /* Message is of format:
           CLUSTER <Sequenz-Nr.> <Knoten1-Name> <Knoten1-Port> <Knoten2-Name> <Knoten2-Port>
        */
        StringBuilder message = new StringBuilder("CLUSTER " + node.getCurrentWriteIndex());
        for (Integer port : clusterNames.keySet()) {
            message.append(" " + clusterNames.get(port) + " " + port);
        }

        return message.toString();
    }

    void shareUpdatedClusterInfo() {
        String message = getCurrentClusterInfo();

        for(TcpWriter writer : clusterWriters.values()) {
            writer.write(message.toString());
        }

    }


    public void sendMessage(String message) {
        // TODO: Implement send Message – reserve index for yourself and send without request
        // No need to ask for timestamp, write ahead and send to other nodes
        try {
            Message newMessage = new Message(node.getPort(), node.getNewWriteAheadIndex(), node.name, new Timestamp(new Date().getTime()).toString(), message);
            node.multicaster.send(newMessage.asNewMessage());
            // TODO: Do it like this or use existing listen() in multicaster?
            node.messageQueue.handleNewMessage(newMessage);

        } catch (IOException e) {
            e.printStackTrace();
            // try again
        }
    }

    public void sendMessageTo(int port, String message) {
        TcpWriter writer = clusterWriters.get(port);
        if (writer != null) writer.write(message);
        else System.out.println(node.name + ": writer is null for sendMessageTo");
    }

    public void actionOnMessage(Message message) {
        System.out.println("Coordinator action on " + message);

        if (message.startsWith(StandardMessages.WANNA_SEND_MESSAGE.toString())) {
            String content = message.toString().substring(StandardMessages.WANNA_SEND_MESSAGE.length() + 1);
            System.out.println(content + "/");
            String[] contentSplit = content.split("\\$", 2); // [0] = Name; [1] = Message
            message.setName(contentSplit[0]);
            message.setText(contentSplit[1]);
            message.setIndex(node.getNewWriteAheadIndex());
            message.setTimestamp(new Timestamp(new Date().getTime()).toString());
            sendMessageTo(message.getSender(), message.asWannaSendResponse());
        }
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
        System.out.println("killing: " + port);
        clusterNames.remove(port);

        TcpWriter writerToRemove = clusterWriters.getOrDefault(port, null);
        if (writerToRemove != null) {
            writerToRemove.close();
            System.out.println("Node " + port + " yote him/herself out of the party!");
            int nodesLeft = printCurrentlyConnected();
            // TODO: last node leaves the gang
        } // else = not found - can this even happen?

        TcpListener listenerToRemove = clusterListeners.getOrDefault(port, null);
        if(listenerToRemove != null) {
            listenerToRemove.close();
        }
    }

    public void listenerDied(int port) {
        System.out.println(node.name + ": Coordinator-Listener died with port: " + port);
        killClusterNode(port);
        shareUpdatedClusterInfo();
    }

    public void handleDeathOf(Integer port) {
        clusterNames.remove(port);
        clusterWriters.get(port).close();
        listenerThreads.get(port).interrupt();
        clusterListeners.get(port).close();
        clusterListeners.remove(port);
        clusterWriters.remove(port);
        shareUpdatedClusterInfo();
    }

    public void close() {
        System.out.println("Closing coordinator...");
        if(listening) {
            listening = false;
            try {
                if (newConnectionsSocket != null) {
                    newConnectionsSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            listenerThreads.values().forEach(x -> x.interrupt());
            clusterListeners.values().forEach(x -> x.close());
            clusterWriters.values().forEach(x -> x.close());
            super.close();
        }
    }

    public Status getStatus() {
        return status;
    }
}
