import javax.xml.bind.DatatypeConverter;
import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import java.io.*;
import java.net.*;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;
import java.util.concurrent.TimeoutException;
import static java.lang.Thread.sleep;

public class Node extends Elector {

    // CONFIGURATION
    private static String IP = "localhost";
    private static String MULTICAST_IP = "230.0.0.0"; // in local scope 239.*, else 230.*
    private static int MULTICAST_PORT = 4321;
    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5100;
    private static final int MC_TIMEOUT = 1000;

    // Communication
    Role role; // Public for testing purpose
    int writeIndex = -1;
    int writeAheadIndex = -1;
    MessageQueue messageQueue = new MessageQueue(this);
    // Ports:
    private int coordinator;
    private int port;
    // private volatile int writeIndex; TODO: merge relic
    private int latestClusterSize;
    // Connections:
    private ServerSocket newConnectionsSocket;
    // private NodeWriter writer;
    public Multicaster multicaster;
    // Status:
    private boolean running = true;
    public String name = null; // Public for testing purpose
    private Status status;
    private Thread coordinatorThread;


    public static void main(String[] args) {
        // Choose a random port in range of LOWER_PORT - UPPER_PORT
        Node node = null;
        boolean foundPort = false;
        while(!foundPort) {
            int port = new Random().nextInt(UPPER_PORT - LOWER_PORT) + LOWER_PORT;

            // Create new Node with that port
            try {
                node = new Node(port, "Bertram");
                /*
                Thread searchClusterTh = node.getSearchClusterThread();
                searchClusterTh.start();
                searchClusterTh.join();
                */
            } catch(ConnectException e) {
                e.printStackTrace();
                continue;
            }

            foundPort = true;
        }

        // TODO: non-static getSearchClusterThread().start();
    }

    public Node(int port, String name) throws ConnectException {
        System.setProperty("java.net.preferIPv4Stack", "true");

        System.out.println("\nStarted Node with name: " + name);
        this.name = name;
        this.port = port;
        initializeWriteIndex();
        writeAheadIndex = writeIndex;
        multicaster = new Multicaster(this, MULTICAST_IP, MULTICAST_PORT, MC_TIMEOUT);
    }


    public Thread getSearchClusterThread() {

        status = Status.SEARCHING;
        advertiseSearch();
        return new Thread(() -> {
            try {
                sleep(3000);
            } catch(InterruptedException e) {
                suicide();
            }
            evaluateSearchAnswers();
            multicaster.start();
        }, "evaluateSearch");
    }

    private void advertiseSearch() {
        try {
            multicaster.send(Status.REQUEST.toString() + " " + name);
        } catch (IOException e) {
            System.err.println("Failed to send CLUSTER_SEARCH");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void evaluateSearchAnswers() {

        for (int trials = 0; trials < 500; trials++) {
            Message received = null; 
            try {
                received = multicaster.receive();
            } catch (SocketTimeoutException e) {
                break;
            } catch (IOException e) {
                suicide();
            }
            
            if(received == null || received.getText().isEmpty()) continue;
                
            if (received.getText().startsWith(Status.COORDINATOR.toString())) {
                int coordinator = received.getSender();
                String coordinatorName = received.getText().split(" ")[2];
                try {
                    becomeParticipant(coordinator, coordinatorName);
                } catch (IOException e) {
                    waitAndRedoSearch();
                }
                
                return;
            } else if (received.getText().equals(Status.SEARCHING.toString())) {
                // There is another mf, who is searching atm, so we wait and try again later
                status = Status.WAITING;
                waitAndRedoSearch();
                return;
            }
        }

        try {
            becomeCoordinator();
        } catch (IOException e) {
            suicide();
        }
    }

    private void waitAndRedoSearch() {
        new Thread(() -> {
            try {
                sleep(5000);
            } catch (InterruptedException e) {
                suicide();
            }
            getSearchClusterThread().start();
        }, "searchCluster").start();
    }

    protected void becomeParticipant(int coordinator, String coordinatorName) throws IOException {
        Participant part = new Participant(this, coordinator, coordinatorName);
        role = part;
        new Thread(part, "Participant-" + name).start();
    }

    protected void becomeCoordinator() throws IOException {
        Coordinator coordinator = new Coordinator(this);
        role = coordinator;
        coordinatorThread = new Thread(coordinator, "Coordinator");
        coordinatorThread.start();
    }


    public void answerSearchRequest(Message message) {

        Status status = null;
        if (role != null) status = role.getStatus();
        else status = this.status;

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

    public Map<Integer, String> getClusterNames() {
        if (role != null) return role.getClusterNames();
        return null;
    }

    public String getRole() {
        if (role != null) return role.getClass().toString();
        return null;
    }

    public void announceYourDeath() {
        try {
            this.multicaster.send(Status.DEAD.toString() + " " + name);
        } catch (NullPointerException | IOException e) {
            // We did all we could...
        }
    }

    public void suicide() {
        if(status != Status.DEAD) {
            status = Status.DEAD;
            announceYourDeath();
            if(role != null) {
                role.close();
                role = null;
            }
            if(multicaster != null) {
                multicaster.close();
                multicaster = null;
            }
        }
    }

    public void handleDeathOf(Integer port) {
        role.handleDeathOf(port);
    }


    public int getWriteIndex() {
        System.err.println("getCurrentIndex() not implemented.");
        return writeIndex;
    }

    public Status getStatus() {
        if (role != null) return role.getStatus();
        return status;
    }


    // ELECTION

    @Override
    protected void resetResponsibilities() {
        if(this.role != null) {
            this.role.close();
            this.role = null;
        }
    }

    protected void advertiseElection() {
        if(!status.hasAdvertised()) {
            try {
                multicaster.send(Status.ELECTION.toString() + " " + name + " " + writeIndex);
                status = Status.ADVERTISED;
            } catch (IOException e) {
                suicide();
            }
        }
    }

    public void setLatestClusterSize(int latestClusterSize) {
        this.latestClusterSize = latestClusterSize;
    }

    @Override
    protected int getLatestClusterSize() {
        return latestClusterSize;
    }

    @Override
    protected String getName() {
        return name;
    }

    @Override
    protected Map.Entry<Integer, Integer> getMyCandidature() {
        return new HashMap.SimpleEntry<>(this.port, this.writeIndex);
    }

    protected void promoteCandidate(int numReceived, Map.Entry<Integer, Integer> candidate, String cName) {
        status = Status.ELECTED;
        int cPort = candidate.getKey();
        int writeIndex = candidate.getValue();
        try {
            // Format: ELECT <numReceived> <port> <name> <writeIndex>
            multicaster.send(Status.ELECTED.toString()
                    + " " + numReceived + " " + cPort + " " + cName + " " + writeIndex);
        } catch (IOException e) {
            suicide();
        }
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

        File file = new File (name + ".txt");
        FileWriter fw = null;

        try {
            if (!file.exists()) file.createNewFile();

            fw = new FileWriter(file.getAbsoluteFile(), true); // append the new data to end
            fw.write(line);
            // System.out.println(name + ": Written to file: " + new Timestamp(new Date().getTime()).toString());
            System.out.println(name + ": Written to file: " + line);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error with writing to file");
        } finally {
            try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String readLastLineOfFile() {
        File file = new File(name + ".txt");
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
            // e.printStackTrace();
            // System.out.println("Can't access file");
            return null;
        } finally {
            if (rafile != null)
                try {
                    rafile.close();
                } catch (IOException e) {}
        }
    }

    public String lookForIndexInFile(int index) {
        File file = new File(name + ".txt");

        if (file.exists()) {
            try(BufferedReader br = new BufferedReader(new FileReader(file))) {
                for(String line; (line = br.readLine()) != null; ) {
                    if (!line.trim().equals("")) {
                        int lineIndex = Integer.parseInt(line.split(" ", 2)[0]);
                        if (index == lineIndex) {
                            Message messageToReturn = new Message(getPort(), line);
                            return messageToReturn.fileLineToRequested();
                        }
                        // messageQueue.putIntoMessages(lineIndex, line);
                    }
                }
                return null;
            } catch (FileNotFoundException e) {
                // e.printStackTrace();
                return null;
            } catch (IOException e) {
                // e.printStackTrace();
                return null;
            }
        } else return null;
    }

    public boolean deleteMessagesFile() {
        File file = new File(name + ".txt");
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("Couldn't delete file, flop");
            return false;
        }
    }

    public int initializeWriteIndex() {
        String lastLine = readLastLineOfFile();
        // System.out.println("The last line: " + lastLine);
        if (lastLine == null || lastLine.trim().equals("")) writeIndex = -1;
        else writeIndex = Integer.parseInt(lastLine.split(" ", 2)[0]);
        return getCurrentWriteIndex();
    }


    public String getFileHash() {
        byte[] b = new byte[0];
        try {
            b = Files.readAllBytes(Paths.get(name + ".txt"));
            byte[] hash = MessageDigest.getInstance("MD5").digest(b);
            return DatatypeConverter.printHexBinary(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            // e.printStackTrace();
            System.out.println("There is no file, thus no hash");
            return null;
        }
    }

    public int getNewWriteIndex() {
        return ++writeIndex;
    }

    public int getNextWriteIndex() {
        return writeIndex + 1;
    }

    public int getCurrentWriteIndex() {
        return writeIndex;
    }

    public int getNewWriteAheadIndex() {
        return ++writeAheadIndex;
    }

    public int getCurrentWriteAheadIndex() {
        return writeAheadIndex;
    }

    public void close() {
        System.out.println(name + ": Closing node");
        multicaster.close();
        role.close();
        System.out.println(name + ": Node closed");
    }
}
