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
    private static String MULTICAST_IP = "239.192.0.1"; // in local scope 239.*
    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5100;
    private final int MC_TIMEOUT = 1000;

    // Communication
    private Role role;
    // Ports:
    private int coordinator;
    private int port;
    public Map<Integer, String> getCluster() {
        return role.getCluster();
    }
    // Connections:
    private ServerSocket newConnectionsSocket;
    private NodeWriter writer;
    private Multicaster multicaster;
    // Status:
    private boolean running = true;
    private String name = null;
    private Status status;
    private boolean listening = false;



    public static void main(String[] args) {
        // Choose a random port in range of LOWER_PORT - UPPER_PORT
        Node node = null;
        boolean foundPort = false;
        while(!foundPort) {
            int port = new Random().nextInt(UPPER_PORT - LOWER_PORT) + LOWER_PORT;

            // Create new Node with that port
            try {
                node = new Node(port);
                node.searchCluster();
            } catch(ConnectException e) {
                e.printStackTrace();
                continue;
            }

            foundPort = true;
        }
    }

    public Node(int port) throws ConnectException {
        System.setProperty("java.net.preferIPv4Stack", "true");

        System.out.println("Started Node");
        this.port = port;

        multicaster = new Multicaster(this, MULTICAST_IP, port, MC_TIMEOUT);
        multicaster.start();
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
            for (int trials = 0; trials < 20; trials++) {
                Message received = multicaster.receive();

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


        // no coordinator found...
        role = new Coordinator(this);
    }



    public void shareStatus(Message message) {
        try {
            unicastMessageTo(status.toString(), message.getSender());
        } catch(IOException e) {
            // instance that requested no longer available. Ignore.
        }
    }


    public int getPort() {
        return port;
    }


    public void unicastMessageTo(String message, Integer to) throws IOException {
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

}
