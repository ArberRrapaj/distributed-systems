import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.*;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

class NodeTest {
    private static List<Node> nodes;
    private static Random rand;
    private static Node coordinator;

    private static Set<Integer> availablePorts;

    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5100;

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
        assertTrue(node3.getClusterNames().values().contains("Bob"));
    }

    private static void waitASec() {
        try {
            sleep(1000);
        } catch (InterruptedException e) { System.exit(1); }
    }

    @Test
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


    @org.junit.jupiter.api.AfterAll
    static void tearDown() {
        for(Node node: nodes) {
            //node.close();
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
}