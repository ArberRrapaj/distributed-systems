import com.sun.tools.internal.ws.wsdl.document.jaxws.Exception;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
        for(int port = LOWER_PORT; port<=UPPER_PORT; port++) {
            availablePorts.add(port);
        }

        try {
            setupThreeNodeCluster();
        } catch (IOException e) {
            e.printStackTrace();
            fail(e);
        }
    }
  
    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        /*
        List<Node> delete = new ArrayList<Node>();
        for(Node node : nodes){
            node.close();
            delete.add(node);
        }
        nodes.removeAll(delete);
        */
    }

    private static Node createAndStartNode(int port, String name) throws ConnectException {
        Node node = new Node(port,name);

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
        Node node = createAndStartNode(getRandomPort(), "Alice");
        assertTrue(node.getClusterNames().isEmpty());
        coordinator = node;

        Node node2 = createAndStartNode(getRandomPort(), "Bob");
        waitASec();
        assertFalse(node2.getClusterNames().isEmpty());
        assertTrue(node.getClusterNames().keySet().contains(node2.getPort()));
        assertTrue(node.getClusterNames().values().contains("Bob"));
        assertTrue(node2.getClusterNames().keySet().contains(node.getPort()));
        assertTrue(node2.getClusterNames().values().contains("Alice"));

        Node node3 = createAndStartNode(getRandomPort(), "Charlie");
        waitASec();
        assertNotNull(node3.getClusterNames());
        assertTrue(node3.getClusterNames().keySet().contains(node.getPort()));
        assertTrue(node3.getClusterNames().keySet().contains(node2.getPort()));
        assertTrue(node3.getClusterNames().values().contains("Alice"));

        chillout(3000);
        assertTrue(node3.getClusterNames().values().contains("Bob"));
    }
  
    // @Test
    void basicConversation() throws IOException {
        /*
        for (int i = 0; i < 2; i++) {
            nodes.add( new Node(getRandomPort(), "a" + i) );
        }
        */
        deleteMessagesFile("Abigail");
        deleteMessagesFile("Bertram");

        Node node1 = new Node(getRandomPort(), "Abigail");
        Node node2 = new Node(getRandomPort(), "Bertram");

        System.out.println("\n\n\n\nOkay, all of the nodes are active, let's send a message");
        System.out.println("\n\n\n\nGo: " + new Timestamp(new Date().getTime()).toString());

        node1.role.sendMessage("Nachricht #1 von Koordinator");
        node2.role.sendMessage("Nachricht #2 von Participant1");
        node2.role.sendMessage("Nachricht #3 von Participant1");

        // System.out.println("\n\n\n\nGo2" + new Timestamp(new Date().getTime()).toString());
        // Go: 21:28:16.847
        // Go2: 21:28:16.86
        // Last 'written to file': 21:28:16.859

        chillout(500);
        assertEquals(node1.getFileHash(), node2.getFileHash());
        node1.close();
        node2.close();
    }
  
    // @Test
    void initializeRightWriteIndex() throws IOException {
        Node nodeWithEntries = new Node(getRandomPort(), "nodeWithEntries");
        Node nodeWithoutEntries = new Node(getRandomPort(), "nodeWithoutEntries");
        Node nodeWithoutFile = new Node(getRandomPort(), "nodeWithoutFile");

        System.out.println("\n\n\n\ninitializeRightWriteIndex: " + new Timestamp(new Date().getTime()).toString());

        deleteMessagesFile("nodeWithEntries");
        writeToFile("nodeWithEntries", "0 Abigail 2019-12-29 00:58:52.449 Nachricht von Koordinator\r\n1 Bertram 2019-12-29 00:58:52.451 Nachricht 1 von Participant\r\n2 Bertram 2019-12-29 00:58:52.451 Nachricht 2 von Participant");
        assertEquals(2, nodeWithEntries.initializeWriteIndex());

        deleteMessagesFile("nodeWithoutEntries");
        writeToFile("nodeWithoutEntries", "");
        assertEquals(-1, nodeWithoutEntries.initializeWriteIndex());

        deleteMessagesFile("nodeWithoutFile");
        assertEquals(-1, nodeWithoutFile.initializeWriteIndex());
    }
  
    // @Test
    void closeNode() throws ConnectException {
        Node node1 = new Node(getRandomPort(), "Abigail");
        Node node2 = new Node(getRandomPort(), "Bertram");

        node2.close();
        chillout(1000);
        node2 = null;
        node2.role.sendMessage("Hi");
    }
  
    @Test
    void recoverFromDisconnect() throws ConnectException {
        deleteMessagesFile("Abigail");
        deleteMessagesFile("Bertram");
        deleteMessagesFile("Camille");

        Node node1 = new Node(getRandomPort(), "Abigail");
        Node node2 = new Node(getRandomPort(), "Bertram");
        Node node3 = new Node(getRandomPort(), "Camille");

        node1.role.sendMessage("Nachricht #1 von Koordinator");
        node2.role.sendMessage("Nachricht #2 von Bertram");

        // TODO: Do smth about this need for timeout
        chillout(500);
        node2.close();

        node3.role.sendMessage("Nachricht #3 von Camille");
        chillout(200);

        assertEquals(node3.getFileHash(), node1.getFileHash());
        assertNotEquals(node3.getFileHash(), node2.getFileHash());

        node2 = new Node(getRandomPort(), "Bertram");
        assertEquals(1, node2.getCurrentWriteIndex());

        node2.role.sendMessage("Nachricht #4 von Betram, nach Disconnect");

        chillout(1000);

        assertEquals(node3.getFileHash(), node2.getFileHash());
    }
  
    // @Test
    void killingCoordinatorTriggersReElection() throws IOException {
        coordinator.suicide();
        nodes.remove(coordinator);
        try {
            sleep(12000);
        } catch (InterruptedException e) { System.exit(1); }
        assertTrue(nodes.stream().allMatch(x -> x.getRole() != null));
        assertTrue(nodes.stream().anyMatch(
                x -> x.getRole().contains("Coordinator")));
        assertTrue(nodes.stream().allMatch(x -> x.getClusterNames().size() > 0));
    }
  
    private static void waitASec() {
        try {
            sleep(1000);
        } catch (InterruptedException e) { System.exit(1); }
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

        File file = new File (name + ".txt");
        FileWriter fw = null;

        try {
            if (!file.exists()) file.createNewFile();

            fw = new FileWriter(file.getAbsoluteFile(), false); // new file
            fw.write(line);
            System.out.println("Written to file: " + new Timestamp(new Date().getTime()).toString());
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

    public boolean deleteMessagesFile(String name) {
        File file = new File(name + ".txt");
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("Couldn't delete file, flop");
            return false;
        }
    }

    private void chillout(int milliseconds) {
        try { Thread.sleep(milliseconds); }
        catch (InterruptedException e) { e.printStackTrace(); }
    }
}