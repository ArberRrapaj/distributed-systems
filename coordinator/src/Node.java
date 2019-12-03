import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;
// import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Node extends Thread {

    private Socket socket;
    private ServerSocket newConnectionsSocket;
    private boolean running = true;
    private String id = null;
    private Status status;
    private boolean listening = false;
    private int coordinator;

    private Map<Integer, ClusterNode> cluster;


    private static String IP = "localhost";
    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5100;
    private int port;
    private final int TIMEOUT = 100;
    private NodeWriter writer;


    public static void main(String[] args) throws IOException, InterruptedException {
        // Choose a random port in range of LOWER_PORT - UPPER_PORT
        Set<Integer> availablePorts = new HashSet<>();
        for (int port = LOWER_PORT; port <= UPPER_PORT; port++) {
            availablePorts.add(port);
        }
        int index = new Random().nextInt(availablePorts.size());
        Iterator<Integer> iter = availablePorts.iterator();
        for (int i = 0; i < index; i++) {
            iter.next();
        }
        int port = iter.next();

        // Create new Node with that port
        Node node = new Node(IP, port);
    }

    public Node(String ip, int port) throws IOException, InterruptedException {
        System.out.println("Started Node");
        this.port = port;

        // Start by searching for a cluster
        searchCluster();
    }


    public void searchCluster() {

        status = Status.SEARCHING; // Set the node's status to SEARCHING

        // Iterate through every possible port in boundaries
        for (int port = LOWER_PORT; port <= UPPER_PORT; port++) {
            System.out.println("Port " + this.port + " checking: " + port);
            if (port == this.port) continue; // Skip same port

            // Create a new socket that tries to connect to that port
            Socket crawlSocket;
            try {
                crawlSocket = new Socket();
                SocketAddress address = new InetSocketAddress(port);
                try {
                    crawlSocket.connect(address, TIMEOUT);
                } catch (IOException e) {
                    throw new ConnectException("Failed to connect to: " + port + e);
                }
                System.out.println("TCPClient connected socket: " + crawlSocket);

            } catch (ConnectException e) {
                // couldn't build connection -> nothing running on that address/port
                continue;
            }


            // If the connection was successfull, ask if she's a coordinator
            PrintWriter out;
            BufferedReader in;
            try {
                out = new PrintWriter(crawlSocket.getOutputStream());
                in = new BufferedReader(new InputStreamReader(crawlSocket.getInputStream()));

                out.println("Hello I am: " + this.port
                        + "; are you a coordinator?");
                // System.out.println(new Timestamp(new Date().getTime()));
                out.flush();
                System.out.println("Written: " + "Hello I am: " + this.port
                        + "; are you a coordinator?");

            } catch (IOException e) {
                // e.printStackTrace();
                // TODO: only problem: this node is the real coordinator
                System.out.println("Error setting up In-/Output, continuing to next port");
                continue;
            }

            // Wait for an answer
            String answer = "";
            String inputLine;
            while (true) {
                // TODO: add timeout here?
                // System.out.println("Waiting for answer");
                try {
                    if ((inputLine = in.readLine()) == null) break;
                    if (inputLine.contains("STATUS")) {
                        answer = inputLine;
                        break;
                    }
                    System.out.print("initial - ");
                    System.out.println(crawlSocket.getLocalPort() + " read: " + inputLine);

                    // Send a response if the answer was right
                    String response = null;
                    response = getAnswer(answer, null);

                    if (response != null) {
                        System.out.println(crawlSocket.getPort() + " answers: " + response);
                        out.println(response);
                        out.flush();
                    }

                } catch (IOException ioe) {
                    System.out.println("Not exactly sure what fucked up here");
                }
            } // got an answer

            // Close the crawlSocket, we don't need it anymore
            try {
                in.close();
                out.println("exit");
                out.flush();
                out.close();
                crawlSocket.close();
            } catch (IOException e) {
                System.out.println("Failed to close crawlSocket");
            }

            // There was an actual answer
            if (!answer.isEmpty()) {
                System.out.println("Answer: " + answer);

                if (answer.contains(Status.COORDINATOR.toString())) {
                    // Found coordinator, join his cluster

                    System.out.println("Found coordinator: " + port);
                    coordinator = port;
                    joinCluster(answer, port);
                    return;

                } else if (answer.equals("I am searching.")) {
                    // There is another mf, who is searching atm, so we wait and try again later
                    status = Status.WAITING;
                    try {
                        sleep(5000);
                    } catch (InterruptedException eI) {
                        System.out.println("I can't sleep, insomnia just killed me :(");
                        System.exit(1);
                    }

                    // Search again
                    searchCluster();
                    return;
                }
            } // else = answer is empty

        } // for-loop

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


    private void joinCluster(String answer, int coordinator) {
        status = Status.NO_COORDINATOR;

        System.out.println("Port " + port + " joining cluster of: " + coordinator);

        cluster = new HashMap<>();

        // Yes I am!!1! This my gang: [1023, 1025, ...]
        Pattern p = Pattern.compile(String.format("%s This is my gang: \\[((\\d+(, )?)*)\\]", Status.COORDINATOR.toString()));
        Matcher m = p.matcher(answer);

        if (m.find()) {
            String clusterList = m.group(1);
            System.out.println("Cluster List: " + clusterList);

            if (!clusterList.isEmpty()) {
                String[] members = clusterList.split(", ");
                System.out.println("Size of " + members.length);
                for (String memberPortString : members) {
                    Integer memberPort = Integer.valueOf(memberPortString);
                    cluster.put(memberPort, null);
                }

                Iterator<Integer> iterator = cluster.keySet().iterator();
                while (iterator.hasNext()) {
                    int memberPort = iterator.next();
                    try {
                        ClusterNode clusterNode = new ClusterNode(port, memberPort, this);
                        clusterNode.communicateJoin();
                        cluster.put(memberPort, clusterNode);
                    } catch (ConnectException e) {
                        System.out.println("Failed to connect to: " + port);
                        // ClusterNode will die on it's own, let's remove it from the cluster-list
                        iterator.remove();
                        continue;
                    }
                } // cluster-loop

            } // else = list is empty

            // Add coordinator TODO: DRY!
            try {
                ClusterNode clusterNode = new ClusterNode(port, coordinator, this);
                clusterNode.communicateJoin();
                cluster.put(coordinator, clusterNode);
                clusterNode.handleMessagesFile();
            } catch (ConnectException e) {
                System.out.println("Failed to connect to coordinator: " + port);
            }
        } // else = pattern not found

        printCurrentlyConnected();
        // Start listening for new connections
        start();
    }


    public void run() {
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
                // Don't add to cluster yet, let ClusterNode class examine first
                new ClusterNode(newConnectionsSocket.getLocalPort(), newConnectionsSocket.accept(), this).start();
            } catch (IOException e) {
                // e.printStackTrace();
                System.out.println("Failed to accept connection");
                // System.exit(1);
            }

        }
    }

    private ClusterNode addToCluster(int nodePort, ClusterNode node) {
        cluster.put(nodePort, node);
        return node;
    }


    public int printCurrentlyConnected() {
        int connectedUsers = cluster.values().size();
        System.out.println("Cluster status:");
        switch (connectedUsers) {
            case 0:
                System.out.println("  --> I have no gang");
                break;
            case 1:
                System.out.println("  --> There is one person in my gang");
                break;
            default:
                System.out.println("  --> My gang consists of " + connectedUsers + " people");
                break;
        }
        return connectedUsers;
    }

    public Set<Integer> getCluster() {
        return cluster.keySet();
    }

    public int getPort() {
        return port;
    }

    public String getAnswer(String message, ClusterNode clusterNode) {
        System.out.println("GetAnswer for " + message);

        // Initial request, looking for nodes
        Pattern p = Pattern.compile("I am: (\\d+); are you a coordinator\\?");
        Matcher m = p.matcher(message);
        if (m.find()) {
            if (status == Status.COORDINATOR) {
                return Status.COORDINATOR.toString() + " This is my gang: " + cluster.keySet().toString();
            } else if (status == Status.SEARCHING) {
                return Status.SEARCHING.toString();
            } else {
                return Status.NO_COORDINATOR.toString();
            }
        }

        // Concrete Connection attempt
        Pattern joinPattern = Pattern.compile("I \\((\\d+)\\) want to JOIN");
        Matcher joinMatcher = joinPattern.matcher(message);
        if (joinMatcher.find()) {
            int joiningPort = Integer.parseInt(joinMatcher.group(1));
            addToCluster(joiningPort, clusterNode);
            clusterNode.setId(joiningPort);
            // System.out.println("I will accept that fella, it's added to the cluster.");
            return "ACCEPT";
        }

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

        return null;
    }

    public void killClusterNode(int id) {
        ClusterNode clusterNodeToRemove = cluster.get(id);
        if (clusterNodeToRemove != null) {
            clusterNodeToRemove.close();
            cluster.remove(id);
            System.out.println("Node " + id + " yote him/herself out of the party!");
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

    public void clusterNodeDied(int id) {
        killClusterNode(id);
        System.out.println("Oh my god, those over-dramatic assholes, this is some serious Romeo and Juliet shit.");
    }

    public void sendMessage(String message) {
        String timestamp;
        if(status == Status.COORDINATOR) {
            timestamp = new Timestamp(new Date().getTime()).toString();
            System.out.println("I am the coordinator, so I don't have to ask for a timestamp");
        } else {
            ClusterNode coordinatorNode = cluster.get(coordinator);
            timestamp = coordinatorNode.requestTimestamp();
        }

        message = timestamp + "@" + port + ": " + message;
        writeTextToFile(message);

        for (Map.Entry<Integer, ClusterNode> entry : cluster.entrySet()) {
            entry.getValue().write(message);
        }
    }

    public void close() {
        System.out.println("Closing...");
        listening = false;
        for (int port : cluster.keySet()) {
            cluster.get(port).close();
        }
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



}
