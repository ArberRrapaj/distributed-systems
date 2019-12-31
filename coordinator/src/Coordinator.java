import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    private Map<Integer, File> tempFiles;

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
        // System.out.println("Coordinator " + node.getPort() + " creating new cluster...");

        clusterWriters = new HashMap<>();
        clusterListeners = new HashMap<>();
        listenerThreads = new HashMap<>();

        introductionWriters = new HashMap<>();
        introductionListeners = new HashMap<>();
        introductionListenerThreads = new HashMap<>();

        tempFiles = new HashMap<>();
    }

    public void run() {
       listenForNewConnections();
    }

    private void listenForNewConnections() {

        listening = true;

        while (listening) {
            // Wait for incoming connects: continuously accept new TCP connections for new cluster participants
            try {
                // System.out.println("I'll be listening for new connections on port: " + node.getPort());
                Socket newSocket = newConnectionsSocket.accept(); // blocks until new connection
                // System.out.println("New Connection: " + newSocket);
                int port = newSocket.getPort();
                TcpListener newTcpListener = new TcpListener(this, node, newSocket, port);

                // System.out.println(node.name + ": port=" + port + "; newTcpListener=" + newTcpListener);
                introductionListeners.put(port, newTcpListener);
                introductionListenerThreads.put(port, new Thread(newTcpListener, "TcpListener-" + node.getName() + "-" + port));
                introductionListenerThreads.get(port).start();
                TcpWriter newTcpWriter = new TcpWriter(newSocket, this, node);
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
        int currentClusterSize = portSet.size();
        node.setLatestClusterSize(currentClusterSize);

        StringBuilder currentlyOnline = new StringBuilder();
        int counter = 0;
        for (Integer port : portSet) {
            counter++;
            String name = clusterNames.get(port);
            message.append(" " + name + " " + port);
            currentlyOnline.append(name);
            if (currentClusterSize == counter) currentlyOnline.append(". ");
            else currentlyOnline.append(", ");
        }
        // System.out.println("  #INFO: There was a change in the chatroom, there are " + clusterNames.size() + " other people here: " + currentlyOnline.toString());
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
        // else; // System.out.println(node.name + ": writer is null for sendMessageTo");
    }

    public void actionOnMessage(Message message, boolean duringInformationExchange) {
        // System.out.println("Coordinator action on " + message);

        if (duringInformationExchange) {
            int localSenderPort = message.getSender();
            if (message.startsWith(StandardMessages.INTRODUCTION_PARTICIPANT.toString())) {
                // System.out.println(node.name + ": got an introduction from participant");

                String content = message.withoutStandardPart(StandardMessages.INTRODUCTION_PARTICIPANT);
                String[] contentSplit = content.split("\\$", 4); // 0 = Port, 1 = Name, 2 = ParticipantWriteIndex, 3 = line
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
                // listener.informationExchanged = true;

                // TcpWriter
                TcpWriter writer = introductionWriters.get(localSenderPort);
                introductionWriters.remove(localSenderPort);
                writer.setId(actualPort);
                clusterWriters.put(actualPort, writer);

                clusterNames.put(actualPort, contentSplit[1]);
                int participantWriteIndex = Integer.parseInt(contentSplit[2]);
                String participantLine = contentSplit[3].trim();

                String lineWithIndex = node.lookForIndexInFile(participantWriteIndex);
                if (lineWithIndex != null) lineWithIndex = lineWithIndex.trim();
                // System.out.println(node.name + ": lineWithIndex:" + lineWithIndex);
                // System.out.println(node.name + ": participantLine:" + participantLine);

                boolean matches = participantLine.equals(lineWithIndex);
                if (participantWriteIndex == -1 && node.getCurrentWriteIndex() == -1) matches = true;

                if (matches) {
                    // System.out.println(node.name + ": The participants messages seem to be good");
                    writer.write(StandardMessages.INTRODUCTION_COORDINATOR + " " + StandardMessages.MESSAGE_BASE_GOOD);
                } else {
                    // System.out.println(node.name + ": The participants messages are not good");

                    int fileSize = -1;
                    try {
                        File originalFile = new File(node.name + ".txt");
                        // System.out.println(originalFile.toPath());
                        if (originalFile.exists()) {


                            File tempFile = new File(node.name + contentSplit[1] + ".txt");
                            // tempFile.delete();
                            // System.out.println(tempFile.toPath());

                            Path hi = Files.copy(originalFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            // System.out.println(hi);

                            tempFiles.put(actualPort, tempFile);
                            fileSize = (int)tempFile.length();
                        } else fileSize = 0;
                        // System.out.println(node.name + ": My file size:" + fileSize);

                        writer.write(StandardMessages.INTRODUCTION_COORDINATOR + " " + StandardMessages.MESSAGE_BASE_BAD + " " + fileSize); // -1 = error, 0 = no file, else initiateSendMessage
                    } catch (IOException e) {
                        e.printStackTrace();
                        // System.out.println(node.name + ": Me dead lol, no message base at all");
                        listenerDied(actualPort);
                    } finally {
                        // if (tempFile != null) tempFile.delete();
                    }
                }

                // welcomeNewNodeToCluster(); // Not yet, wait for confirmation
            } else if (message.startsWith(StandardMessages.MESSAGE_BASE_FEEDBACK_RESPONSE.toString())) {
                // System.out.println(node.name + ": got a response to the message base feedback");

                String content = message.withoutStandardPart(StandardMessages.MESSAGE_BASE_FEEDBACK_RESPONSE);
                boolean needsFileTransmission = content.startsWith(StandardMessages.MESSAGE_BASE_READY_FOR_TRANSMISSION.toString());
                if (!needsFileTransmission) {
                    // System.out.println(node.name + ": messageIndex:" + message.getIndex() + ", sender: " + message.getSender());
                    clusterListeners.get(message.getSender()).informationExchanged = true;
                } else {
                    // Initialize file transfer
                    clusterWriters.get(message.getSender()).sendMessageFile();
                    clusterListeners.get(message.getSender()).informationExchanged = true;
                }
                welcomeNewNodeToCluster();
            }
        } else {
            if (message.startsWith(StandardMessages.WANNA_SEND_MESSAGE.toString())) {
                String content = message.withoutStandardPart(StandardMessages.WANNA_SEND_MESSAGE);
                // System.out.println(content + "/" + message.getSender() + "/");
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
        // System.out.println("killing: " + port);
        clusterNames.remove(port);

        // System.out.println("Node " + port + " yote him/herself out of the party!");
        clusterNames.remove(port);

        listenerThreads.get(port).interrupt();
        listenerThreads.remove(port);

        clusterListeners.get(port).close();
        clusterListeners.remove(port);

        clusterWriters.get(port).close();
        clusterWriters.remove(port);
    }

    public void listenerDied(int port) {
        // System.out.println(node.name + ": Coordinator-Listener died with port: " + port);
        // killClusterNode(port);
        handleDeathOf(port);
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
        // System.out.println("Closing coordinator...");
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

    public File getTempFileOf(int port) { return tempFiles.get(port); } // System.out.println(node.name + ": Requested tempFile of: " + port); return tempFiles.get(port); }
    public void removeTempFileOf(int port) { tempFiles.remove(port); }

}
