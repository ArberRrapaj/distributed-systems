import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import sun.rmi.transport.tcp.TCPConnection;

import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;

public class Node extends Thread {

    // CONFIGURATION
    private static String IP = "localhost";
    private static String MULTICAST_IP = "239.6.5.4"; // in local scope 239.*
    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5100;
    private final int TIMEOUT = 100;

    // Communication
    private Socket socket;
    private ServerSocket newConnectionsSocket;
    private NodeWriter writer;
    private Multicaster multicaster;
    private TcpWriter tcpWriter;
    private TcpListener tcpListener;

    private boolean running = true;
    private String name = null;
    private Status status;
    private boolean listening = false;

    private int coordinator;

    private Map<Integer, String> cluster;
    public Map<Integer, String> getCluster() {
        return cluster;
    }
    private Map<Integer, TcpWriter> clusterWriters;
    private int port;


    public static void main(String[] args) {
        // Choose a random port in range of LOWER_PORT - UPPER_PORT
        Node node;
        boolean foundPort = false;
        while(!foundPort) {
            int port = new Random().nextInt(UPPER_PORT - LOWER_PORT) + LOWER_PORT;

            // Create new Node with that port
            try {
                node = new Node(IP, port);
            } catch(ConnectException e) {
                e.printStackTrace();
                continue;
            }

            foundPort = true;
        }

    }

    public Node(String ip, int port) throws ConnectException {
        System.out.println("Started Node");
        this.port = port;

        Multicaster multicaster = new Multicaster(this, MULTICAST_IP, port);

        // Start by searching for a cluster
        searchCluster();
    }


    public void searchCluster() {

        status = Status.SEARCHING; // Set the node's status to SEARCHING

        try {
            multicaster.send(StandardMessages.CLUSTER_SEARCH.toString());
        } catch (IOException e) {
            System.err.println("Failed to send CLUSTER_SEARCH");
            e.printStackTrace();
            System.exit(1);
        }
        try {
            sleep(3000);
        } catch(InterruptedException e) {
            System.exit(1);
        }

        try {
            for(int trials = 0; trials < 20; trials++) {
                sleep(1000);
                Message received = multicaster.receive();

                if (received.getText().startsWith(Status.COORDINATOR.toString())) {
                    joinCluster(received);
                    break;
                } else if (received.getText().equals(Status.SEARCHING.toString())) {
                    // There is another mf, who is searching atm, so we wait and try again later
                    status = Status.WAITING;

                    sleep(5000);

                    // Search again
                    searchCluster();
                    return;
                }
            }

        } catch(InterruptedException | IOException e) {
            System.err.println("Failed to receive COORDINATOR REPLY");
            System.exit(1);
        }


        // no coordinator found...
        createCluster();
    }

    private void createCluster() {
        System.out.println("Port " + this.port + " creating new cluster...");

        status = Status.COORDINATOR;
        cluster = new HashMap<>();

        // start listening
        start();
    }


    private void joinCluster(Message answer) {
        status = Status.NO_COORDINATOR;

        cluster = new HashMap<>();

        String[] messageSplit = answer.getText().split(" ");
        coordinator = Integer.valueOf(messageSplit[2]);
        System.out.println("Port " + port + " joining cluster of: " + coordinator);

        // establish TCP connection to Coordinator
        try {
            tcpWriter = new TcpWriter(port, coordinator, this);
            tcpListener = new TcpListener(this, socket);
        } catch(IOException e) {
            // Harakiri
            System.exit(1);
        }


        // TODO: handleMessagesFile()

        start();
    }

    public void run() {
        multicaster.start();

        writer = new NodeWriter(this);
        writer.start();

        listenForNewConnections();
    }



    private void listenForNewConnections() {
        listening = true;

        while (listening) {

            // Setup the socket for new connections
            try {
                newConnectionsSocket = new ServerSocket(port);
                System.out.println("\nI'll be listening for new connections on port: " + port);
            } catch (IOException e) {
                // e.printStackTrace();
                System.out.println("There was an error setting up the newConnectionsSocket");
                // close();
                // System.exit(1);
            }

            // Create a clusterNode for new connection
            try {
                // Don't add to cluster yet, let TcpWriter class examine first
                Socket newSocket = newConnectionsSocket.accept(); // blocks until new connection
                new TcpListener(this, newSocket);
                new TcpWriter(newConnectionsSocket.getLocalPort(), newSocket, this).start();

            } catch (IOException e) {
                // e.printStackTrace();
                System.out.println("Failed to accept connection");
                // System.exit(1);
            }

        }
    }

    public int printCurrentlyConnected() {
        int connectedUsers = cluster.keySet().size();
        System.out.println("Cluster status:");
        switch (connectedUsers) {
            case 0:
                System.out.println("  --> I have no gang");
                break;
            case 1:
                System.out.println("  --> There is one person in my gang: " + connectedUsers);
                break;
            default:
                System.out.println("  --> My gang consists of " + connectedUsers);
                break;
        }
        return connectedUsers;
    }

    public int getPort() {
        return port;
    }

    public void actionOnMessage(Message message) {
        System.out.println("GetAnswer for " + message);

        // Initial request, looking for nodes
        if (message.startsWith("COORDINATOR SEARCH")) {
            try {
                String answer = null;
                if (status == Status.COORDINATOR) {
                    answer = Status.COORDINATOR + " " + name;


                } else if (status == Status.SEARCHING) {
                    Status.SEARCHING.toString();
                } else {
                    Status.NO_COORDINATOR.toString();
                }

                unicastMessage(answer, message.getSender());
            } catch (IOException e) {
                // Node that just requested is now not reachable. Ignore.
            }
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

    public void actionOnCoordinatorMessage(String message) {
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
                    cluster.put(Integer.valueOf(messageSplit[i + 1]), messageSplit[i + 1]);
                }
            }

        }


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

    public void unicastMessage(String message, Integer to) throws IOException {
        if (message == null || message.isEmpty()) { return; }
        Socket socket = new Socket();
        SocketAddress address = new InetSocketAddress(MULTICAST_IP, port);
        socket.connect(address);
        PrintWriter out = new PrintWriter(socket.getOutputStream());

        out.println(message);
        out.flush();
        System.out.println("Unicasted: " + message);

        out.close();
        socket.close();
    }

    public void sendMessage(String message) {
        String timestamp;

        /*
        if(status == Status.COORDINATOR) {
            timestamp = new Timestamp(new Date().getTime()).toString();
            System.out.println("I am the coordinator, so I don't have to ask for a timestamp");
        } else {
            TcpWriter coordinatorNode = cluster.get(coordinator);
            timestamp = coordinatorNode.requestTimestamp();
        }

        message = timestamp + "@" + port + ": " + message;
        writeTextToFile(message);

        for (Map.Entry<Integer, TcpWriter> entry : cluster.entrySet()) {
            entry.getValue().write(message);
        }
        */
    }

    private void shareCurrentClusterInfo() {
        // TODO: use upon newly established or broken Socket Connnection -> Share among all
        /* Message is of format:
         CLUSTER <NAME> ...
            ... <PARTICIPANT-1-NAME> <PARTICIPANT-1-PORT> ...
            ... <PARTICIPANT-2-NAME> <PARTICIPANT-2-PORT> ...
        */
        StringBuilder answer = new StringBuilder("CLUSTER " + name);
        for (Integer port : cluster.keySet()) {
            answer.append(" " + cluster.get(port) + " " + port);
        }

        for(TcpWriter writer : clusterWriters.values()) {
            writer.write(answer.toString());
        }

    }

    public void close() {
        System.out.println("Closing...");
        listening = false;

        // TODO: coordinatorConnection.close();
        tcpWriter.close();
        tcpListener.close();
    }

    public void writeTextToFile(String text) {
        // Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        String line = text + "\r\n";

        FileWriter fw;
        try {
            fw = new FileWriter(port + ".txt", true); // the true will append the new data
            fw.write(line);
            // String last = line.substring(line.lastIndexOf('-') + 1).replace("\n", "").replace("\r", "");
            // System.out.println("line written: " + text);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String readLastLineOfFile() {
        File file = new File(port + ".txt");
        RandomAccessFile rafile = null;
        try {
            rafile = new RandomAccessFile(file, "r");
            long fileLength = rafile.length() - 1;
            StringBuilder sb = new StringBuilder();

            for (long filePointer = fileLength; filePointer != -1; filePointer--) {
                rafile.seek(filePointer);
                int readByte = rafile.readByte();

                if (readByte == 0xA) { // \n
                    if (filePointer == fileLength)
                        continue;
                    break;
                } else if (readByte == 0xD) { // \r
                    if (filePointer == fileLength - 1)
                        continue;
                    break;
                }
                sb.append((char) readByte);
            }
            String lastLine = sb.reverse().toString();
            return lastLine;
        } catch (java.io.IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (rafile != null)
                try {
                    rafile.close();
                } catch (IOException e) {
                }
        }
    }

    public String getFilesHash() {
        byte[] b = new byte[0];
        try {
            b = Files.readAllBytes(Paths.get(port + ".txt"));
            byte[] hash = MessageDigest.getInstance("MD5").digest(b);
            return DatatypeConverter.printHexBinary(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            // e.printStackTrace();
            System.out.println("There is no file, thus no hash");
            return null;
        }
    }

    public boolean deleteMessagesFile() {
        File file = new File(port + ".txt");
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("Couldn't delete file, flop");
            return false;
        }
    }

    public void listenerDied() {
        tcpListener.close();
        tcpListener = null;
        System.out.println("Seems like my tcpListener, the bastard, killed himself, so there is no need for me to be in this imperfect world anymore.");
        // TODO: initiateElection();
    }



}
