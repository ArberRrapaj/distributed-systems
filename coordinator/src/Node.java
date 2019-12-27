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
    private static String MULTICAST_IP = "230.0.0.0"; // in local scope 239.*, else 230.*
    private static int MULTICAST_PORT = 4321;
    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5100;
    private static final int MC_TIMEOUT = 1000;

    // Communication
    Role role; // Public for testing purpose
    int writeIndex = 0;
    MessageQueue messageQueue = new MessageQueue(this);
    // Ports:
    private int coordinator;
    private int port;
    public Map<Integer, String> getClusterNames() {
        return role.getClusterNames();
    }
    // Connections:
    private ServerSocket newConnectionsSocket;
    private NodeWriter writer;
    public Multicaster multicaster;
    // Status:
    private boolean running = true;
    private String name = null;
    private Status status;


    public static void main(String[] args) {
        // Choose a random port in range of LOWER_PORT - UPPER_PORT
        Node node = null;
        boolean foundPort = false;
        while(!foundPort) {
            int port = new Random().nextInt(UPPER_PORT - LOWER_PORT) + LOWER_PORT;

            // Create new Node with that port
            try {
                node = new Node(port, "Alice");
            } catch(ConnectException e) {
                e.printStackTrace();
                continue;
            }

            foundPort = true;
        }
    }

    public Node(int port, String name) throws ConnectException {
        System.setProperty("java.net.preferIPv4Stack", "true");

        System.out.println("\nStarted Node with name: " + name);
        this.name = name;
        this.port = port;

        multicaster = new Multicaster(this, MULTICAST_IP, MULTICAST_PORT, MC_TIMEOUT);

        searchCluster();
        multicaster.start();
    }


    public void searchCluster() {

        status = Status.SEARCHING; // Set the node's status to SEARCHING

        try {
            multicaster.send(StandardMessages.CLUSTER_SEARCH.toString() + " " + name);
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
            for (int trials = 0; trials < 20; trials++) {
                Message received = multicaster.receive();

                if (received == null) continue; // own message
                if (received.getText().startsWith(Status.COORDINATOR.toString())) {
                    role = new Participant(this, received);
                    return;
                } else if (received.getText().equals(Status.SEARCHING.toString())) {
                    // There is another mf, who is searching atm, so we wait and try again later
                    status = Status.WAITING;

                    sleep(5000);

                    // Search again
                    searchCluster();
                    return;
                }
            }
        } catch(SocketTimeoutException e) {
            // The socket timed out => no answers were received -> proceed to create Cluster
        } catch(InterruptedException | IOException e) {
            System.err.println("Failed to receive COORDINATOR REPLY ");
            e.printStackTrace();
            System.exit(1);
        }


        // no coordinator found... -> create
        Coordinator coordinator = new Coordinator(this);
        role = coordinator;
        // start listening
        Thread coordinatorThread = new Thread(coordinator);
        coordinatorThread.start();
    }



    public void answerSearchRequest(Message message) {

        Status status = null;
        if(role != null) {
            status = role.getStatus();
            role.addToCluster(message.getSender(), message.getText().split(" ")[2]); // COORDINATOR SEARCH <NAME>
        } else {
            status = this.status;
        }

        try {
            multicaster.send(status.toString() + " " + name);
        } catch(IOException e) {
            // instance that requested no longer available. Ignore.
            e.printStackTrace();
        }
    }


    public int getPort() {
        return port;
    }



    /* MESSAGE, FILE ETC.
    public void sendMessage(String message) {
        String timestamp;

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

    */
    public void writeToFile(Message message) {
        String line = message.getLine() + "\r\n";

        FileWriter fw;
        try {
            fw = new FileWriter(port + ".txt", true); // the true will append the new data
            fw.write(line);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getNewIndex() {
        return writeIndex++;
    }

}
