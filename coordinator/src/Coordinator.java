import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.lang.Thread.sleep;

public class Coordinator extends Role implements Runnable {
    private final Status status = Status.COORDINATOR;

    private ServerSocket newConnectionsSocket;
    Map<Integer, TcpWriter> clusterWriters;
    Map<Integer, TcpListener> clusterListeners;
    Map<Integer, Thread> listenerThreads;

    private Map<Integer, TcpWriter> introductionWriters;
    private Map<Integer, TcpListener> introductionListeners;
    private Map<Integer, Thread> introductionListenerThreads;

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

        introductionWriters = new HashMap<>();
        introductionListeners = new HashMap<>();
        introductionListenerThreads = new HashMap<>();
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
                // System.out.println("New Connection: " + newSocket);
                int port = newSocket.getPort();
                TcpListener newTcpListener = new TcpListener(this, node, newSocket, port);

                // System.out.println(node.name + ": port=" + port + "; newTcpListener=" + newTcpListener);
                introductionListeners.put(port, newTcpListener);
                introductionListenerThreads.put(port, new Thread(newTcpListener, "TcpListener-" + node.getName() + "-" + port));
                introductionListenerThreads.get(port).start();
                TcpWriter newTcpWriter = new TcpWriter(newSocket, node);
                introductionWriters.put(port, newTcpWriter);

                // welcomeNewNodeToCluster();

            } catch (IOException e) {
                node.suicide();
            }

        }
    }

    private void welcomeNewNodeToCluster() {
        shareUpdatedClusterInfo();
    }

    /* Message is of format:
        CLUSTER <Sequenz-Nr.> <Knoten1-Name> <Knoten1-Port> <Knoten2-Name> <Knoten2-Port>
    */
    private String getCurrentClusterInfo() {
        // TODO: use upon broken Socket Connnection -> Share among all

        StringBuilder message = new StringBuilder("CLUSTER " + node.getCurrentWriteIndex());
        Set<Integer> portSet = clusterNames.keySet();
        node.setLatestClusterSize(portSet.size());

        for (Integer port : portSet) {
            message.append(" " + clusterNames.get(port) + " " + port);
        }

        return message.toString();
    }

    private void shareUpdatedClusterInfo() {
        String message = getCurrentClusterInfo();

        for(TcpWriter writer : clusterWriters.values()) {
            writer.write(message);
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

    public void actionOnMessage(Message message, boolean duringInformationExchange) {
        System.out.println("Coordinator action on " + message);

        if (duringInformationExchange) {
            int localSenderPort = message.getSender();
            if (message.startsWith(StandardMessages.INTRODUCTION_PARTICIPANT.toString())) {
                System.out.println(node.name + ": got an introduction from participant");

                String content = message.withoutStandardPart(StandardMessages.INTRODUCTION_PARTICIPANT);
                String[] contentSplit = content.split("\\$", 2);
                int actualPort = Integer.parseInt(contentSplit[0]);
                // TcpListener
                TcpListener listener = introductionListeners.get(localSenderPort);
                listener.setPort(actualPort);
                introductionListeners.remove(localSenderPort);
                clusterListeners.put(actualPort, listener);

                // ListenerThread
                Thread listenerThread = introductionListenerThreads.get(localSenderPort);
                listenerThread.setName("TcpListener-" + node.getName() + "-" + actualPort);
                introductionListenerThreads.remove(localSenderPort);
                listenerThreads.put(actualPort, listenerThread);
                listener.informationExchanged = true;

                // TcpWriter
                TcpWriter writer = introductionWriters.get(localSenderPort);
                introductionWriters.remove(localSenderPort);
                clusterWriters.put(actualPort, writer);

                clusterNames.put(actualPort, contentSplit[1]);
                writer.write(StandardMessages.INTRODUCTION_COORDINATOR + " " + clusterNames.size());

                welcomeNewNodeToCluster();
            }
        } else {
            if (message.startsWith(StandardMessages.WANNA_SEND_MESSAGE.toString())) {
                String content = message.withoutStandardPart(StandardMessages.WANNA_SEND_MESSAGE);
                System.out.println(content + "/" + message.getSender() + "/");
                String[] contentSplit = content.split("\\$", 2); // [0] = Name; [1] = Message
                message.setName(contentSplit[0]);
                message.setText(contentSplit[1]);
                message.setIndex(node.getNewWriteAheadIndex());
                message.setTimestamp(new Timestamp(new Date().getTime()).toString());
                sendMessageTo(message.getSender(), message.asWannaSendResponse());
            }
        }

    }

    private void killClusterNode(int port) {
        System.out.println("killing: " + port);
        clusterNames.remove(port);

        TcpWriter writerToRemove = clusterWriters.getOrDefault(port, null);
        if (writerToRemove != null) {
            writerToRemove.close();
            System.out.println("Node " + port + " yote him/herself out of the party!");
        } // else = not found - can this even happen?

        TcpListener listenerToRemove = clusterListeners.getOrDefault(port, null);
        if(listenerToRemove != null) {
            listenerToRemove.close();
        }
    }

    public void listenerDied(int port) {
        System.out.println(node.name + ": Coordinator-Listener died with port: " + port);
        // killClusterNode(port);
        handleDeathOf(port);
        shareUpdatedClusterInfo();
    }

    @Override
    public boolean informationExchanged() {
        return true;
    }

    public void handleDeathOf(Integer port) {
        killClusterNode(port);
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
