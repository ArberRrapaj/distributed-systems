import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.*;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

class NodeTest {
    private static List<Node> nodes;
    private static Random rand;
    private static Node coordinator;

    private static Set<Integer> availablePorts;

    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5250;

    @BeforeAll
    static void setUp() {
        nodes = new ArrayList<>();
        rand = new Random();

        availablePorts = new HashSet<>();
        for (int port = LOWER_PORT; port <= UPPER_PORT; port++) {
            availablePorts.add(port);
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        /*
         * List<Node> delete = new ArrayList<Node>(); for(Node node : nodes){
         * node.close(); delete.add(node); } nodes.removeAll(delete);
         */
    }

    /**
     * Tests
     */

    @Test
    void startingTwoNodesSimultaneouslyYieldsACoordinator() {

        try {
            Node node1 = new Node(5001, "Alice");
            Node node2 = new Node(5002, "Bert");
            node1.getSearchClusterThread().start();
            node2.getSearchClusterThread().start();

            chillout(60000);
            assertTrue(node1.getClusterNames().values().stream().anyMatch(x -> x.equals("Bert")));
            assertTrue(node2.getClusterNames().values().stream().anyMatch(x -> x.equals("Alice")));
            assertTrue(node1.getRole().contains("Coordinator") || node2.getRole().contains("Coordinator"));
        } catch (ConnectException e) {
            fail(e.getMessage());
        }
    }

    // @Test
    void basicConversation() throws IOException {
        deleteMessagesFile("Abigail");
        deleteMessagesFile("Bertram");

        Node node1 = createStartAndWaitForNode(getRandomPort(), "Abigail");
        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");

        node1.messageQueue.sendMessage("Nachricht #1 von Abigail(Koordinator)");
        node2.messageQueue.sendMessage("Nachricht #2 von Bertram");
        node2.messageQueue.sendMessage("Nachricht #3 von Bertram");

        assertEquals(node1.getFileHash(), node2.getFileHash());
        assertEquals(node1.lookForIndexInFile(0), node2.lookForIndexInFile(0));
    }

    // @Test
    void initializeRightWriteIndex() throws IOException {
        deleteMessagesFile("nodeWithEntries");
        writeToFile("nodeWithEntries",
                "0 Abigail 2019-12-29 00:58:52.449 Nachricht von Koordinator\r\n1 Bertram 2019-12-29 00:58:52.451 Nachricht 1 von Participant\r\n2 Bertram 2019-12-29 00:58:52.451 Nachricht 2 von Participant");

        Node nodeWithEntries = createStartAndWaitForNode(getRandomPort(), "nodeWithEntries");
        assertEquals(2, nodeWithEntries.initializeWriteIndex());

        deleteMessagesFile("nodeWithoutEntries");
        writeToFile("nodeWithoutEntries", "");

        Node nodeWithoutEntries = createStartAndWaitForNode(getRandomPort(), "nodeWithoutEntries");
        assertEquals(-1, nodeWithoutEntries.initializeWriteIndex());

        deleteMessagesFile("nodeWithoutFile");
        Node nodeWithoutFile = createStartAndWaitForNode(getRandomPort(), "nodeWithoutFile");
        assertEquals(-1, nodeWithoutFile.initializeWriteIndex());
    }

    // @Test
    void closeNode() throws ConnectException {
        Node node1 = createStartAndWaitForNode(getRandomPort(), "Abigail");
        node1.role.printCurrentlyConnected();

        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        node1.role.printCurrentlyConnected();
        chillout(500);

        node2.close();
        chillout(500);

        assertEquals(0, node1.role.printCurrentlyConnected());

        Coordinator coordinator = (Coordinator) node1.role;
        assertEquals(0, coordinator.clusterNames.size());
        assertEquals(0, coordinator.clusterListeners.size());
        assertEquals(0, coordinator.listenerThreads.size());
        assertEquals(0, coordinator.clusterWriters.size());
        nodes.remove(node2);
    }

    // @Test
    void recoverFromDisconnect() throws ConnectException {
        deleteMessagesFile("Abigail");
        deleteMessagesFile("Bertram");
        deleteMessagesFile("Camille");

        Node node1 = createStartAndWaitForNode(getRandomPort(), "Abigail");
        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        Node node3 = createStartAndWaitForNode(getRandomPort(), "Camille");

        node1.messageQueue.sendMessage("Nachricht #1 von Koordinator");
        node2.messageQueue.sendMessage("Nachricht #2 von Bertram");

        chillout(500);
        node2.close();

        node3.messageQueue.sendMessage("Nachricht #3 von Camille");
        node1.messageQueue.sendMessage("Nachricht #4 von Koordinator");

        chillout(500);

        assertEquals(node3.getFileHash(), node1.getFileHash());
        assertNotEquals(node3.getFileHash(), node2.getFileHash());

        node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        assertEquals(1, node2.getCurrentWriteIndex());

        node2.messageQueue.sendMessage("Nachricht #5 von Bertram, nach Disconnect");

        chillout(500);

        assertEquals(node3.getFileHash(), node2.getFileHash());
        chillout(1000);
    }

    // @Test
    void correctClusterSizeCommunication() throws ConnectException {
        Node node1 = createStartAndWaitForNode(getRandomPort(), "Abigail");
        chillout(200);
        assertEquals(0, node1.getLatestClusterSize());

        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        chillout(200);
        assertEquals(1, node1.getLatestClusterSize());
        assertEquals(1, node2.getLatestClusterSize());

        Node node3 = createStartAndWaitForNode(getRandomPort(), "Camille");
        chillout(200);
        assertEquals(2, node1.getLatestClusterSize());
        assertEquals(2, node2.getLatestClusterSize());
        assertEquals(2, node3.getLatestClusterSize());

        /*
         * node1.close(); chillout(1000); assertEquals(1, node2.getLatestClusterSize());
         * assertEquals(1, node3.getLatestClusterSize());
         */

        node2.close();
        chillout(200);
        assertEquals(1, node1.getLatestClusterSize());
        assertEquals(1, node3.getLatestClusterSize());

        node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        chillout(200);
        assertEquals(2, node1.getLatestClusterSize());
        assertEquals(2, node2.getLatestClusterSize());
        assertEquals(2, node3.getLatestClusterSize());
    }

    // @Test
    void initializeMessageBaseRight_rightButMissingMessages() throws IOException {
        deleteMessagesFile("Abigail");
        writeToFile("Abigail",
                "0 Abigail 2019-12-29 00:58:52.449 Nachricht von Koordinator\r\n1 Bertram 2019-12-29 00:58:52.451 Nachricht 1 von Participant\r\n2 Bertram 2019-12-29 00:58:52.451 Nachricht 2 von Participant");

        Node node1 = createStartAndWaitForNode(getRandomPort(), "Abigail");
        assertEquals(2, node1.getCurrentWriteIndex());

        deleteMessagesFile("Betram");
        writeToFile("Bertram",
                "0 Abigail 2019-12-29 00:58:52.449 Nachricht von Koordinator\r\n1 Bertram 2019-12-29 00:58:52.451 Nachricht 1 von Participant");
        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        assertEquals(1, node2.getCurrentWriteIndex());

        chillout(2000);
        assertEquals(2, node2.getCurrentWriteIndex());
    }

    // @Test
    void initializeMessageBaseRight_faultyMessages() throws IOException {
        deleteMessagesFile("Abigail");
        writeToFile("Abigail",
                "0 Abigail 2019-12-29 00:58:52.449 Nachricht von Koordinator\r\n1 Bertram 2019-12-29 00:58:52.451 Nachricht 1 von Participant\r\n2 Bertram 2019-12-29 00:58:52.451 Nachricht 2 von Participant");

        Node node1 = createStartAndWaitForNode(getRandomPort(), "Abigail");
        assertEquals(2, node1.getCurrentWriteIndex());

        deleteMessagesFile("Bertram");
        writeToFile("Bertram",
                "0 Abigail 2019-12-29 00:58:52.449 Nachricht von Koordinator\r\n1 Bertram 2019-12-29 00:58:52.451 Nachricht 1 von Participan");

        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        chillout(1000);
        assertEquals(2, node2.getCurrentWriteIndex());
    }

    // @Test
    void initializeMessageBaseRight_NoMessageFile() throws IOException {
        deleteMessagesFile("Abigail");
        writeToFile("Abigail",
                "0 Abigail 2019-12-29 00:58:52.449 Nachricht von Koordinator\r\n1 Bertram 2019-12-29 00:58:52.451 Nachricht 1 von Participant\r\n2 Bertram 2019-12-29 00:58:52.451 Nachricht 2 von Participant");

        Node node1 = createStartAndWaitForNode(getRandomPort(), "Abigail");
        assertEquals(2, node1.getCurrentWriteIndex());

        deleteMessagesFile("Bertram");
        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bertram");
        chillout(1000);
        assertEquals(2, node2.getCurrentWriteIndex());
    }

    // @Test
    void killingCoordinatorTriggersReElection() throws IOException {
        try {
            setupThreeNodeCluster();
        } catch (IOException e) {
            e.printStackTrace();
            fail(e);
        }
        coordinator.suicide();
        nodes.remove(coordinator);
        try {
            sleep(12000);
        } catch (InterruptedException e) {
            System.exit(1);
        }
        assertTrue(nodes.stream().allMatch(x -> x.getClusterNames().size() > 0));
        assertTrue(nodes.stream().allMatch(x -> x.getRole() != null));
        assertTrue(nodes.stream().anyMatch(x -> x.getRole().contains("Coordinator")));
    } // Doesn't work

    /*
    @Test
    void send100Messages() throws ConnectException {
        deleteMessagesFile("Alice");
        deleteMessagesFile("Bob");
        deleteMessagesFile("Charlie");

        Node[] nodes = new Node[3];
        nodes[0] = createStartAndWaitForNode(getRandomPort(), "Alice");
        chillout(1000);

        nodes[1] = createStartAndWaitForNode(getRandomPort(), "Bob");
        chillout(1000);

        nodes[2] = createStartAndWaitForNode(getRandomPort(), "Charlie");
        chillout(2000);

        Random rand = new Random();

        for (int i = 0; i < 100; i++) {
            nodes[rand.nextInt(3)].messageQueue.sendMessage("Test-" + i);
        }
        chillout(10000);

        nodes[0].messageQueue.sendMessage("End1");
        nodes[1].messageQueue.sendMessage("End1");
        nodes[2].messageQueue.sendMessage("End1");

        assertEquals(101, nodes[0].getCurrentWriteIndex());
    }
    */


    
    /***
     * Helper methods
     */

    private static Node createStartAndWaitForNode(int port, String name) throws ConnectException {
        Node node = new Node(port, name);

        Thread searchClusterTh = node.getSearchClusterThread();
        searchClusterTh.start();
        try {
            searchClusterTh.join();
        } catch (InterruptedException e) {
            fail("Waiting for search cluster.");
        }
        nodes.add(node);
        return node;
    }

    private static void setupThreeNodeCluster() throws IOException {
        Node node = createStartAndWaitForNode(getRandomPort(), "Alice");
        assertTrue(node.getClusterNames().isEmpty());
        assertTrue(node.getRole().contains("Coordinator"));
        coordinator = node;

        Node node2 = createStartAndWaitForNode(getRandomPort(), "Bob");
        waitASec();
        assertTrue(node.getClusterNames().keySet().contains(node2.getPort()));
        assertTrue(node.getClusterNames().values().contains("Bob"));
        assertTrue(node2.getClusterNames().keySet().contains(node.getPort()));
        assertTrue(node2.getClusterNames().values().contains("Alice"));
        assertTrue(node2.getRole().contains("Participant"));

        Node node3 = createStartAndWaitForNode(getRandomPort(), "Charlie");
        waitASec();
        assertTrue(node3.getClusterNames().keySet().contains(node.getPort()));
        assertTrue(node3.getClusterNames().keySet().contains(node2.getPort()));
        assertTrue(node3.getClusterNames().values().contains("Alice"));
        assertTrue(node3.getClusterNames().values().contains("Bob"));
        assertTrue(node2.getRole().contains("Participant"));
        // System.out.println("\n\n\nThree node cluster - setup complete");
    }

    private static void waitASec() {
        try {
            sleep(1000);
        } catch (InterruptedException e) {
            System.exit(1);
        }
    }

    private static int getRandomPort() {
        int index = rand.nextInt(availablePorts.size());
        Iterator<Integer> iter = availablePorts.iterator();
        for (int i = 0; i < index; i++) {
            iter.next();
        }
        int port = iter.next();
        availablePorts.remove(port);
        return port;
    }

    public void writeToFile(String name, String message) {
        String line = message + "\r\n";

        File file = new File(name + ".txt");
        FileWriter fw = null;

        try {
            if (!file.exists())
                file.createNewFile();

            fw = new FileWriter(file.getAbsoluteFile(), false); // new file
            fw.write(line);
            // System.out.println("Written to file: " + new Timestamp(new Date().getTime()).toString());
        } catch (IOException e) {
            e.printStackTrace();
            // System.out.println("Error with writing to file");
        } finally {
            try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean deleteMessagesFile(String name) {
        File file = new File(name + ".txt");
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            // e.printStackTrace();
            // System.out.println("Couldn't delete file, flop");
            return false;
        }
    }

    private void chillout(int milliseconds) {
        try {
            sleep(milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}