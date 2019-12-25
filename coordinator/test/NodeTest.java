import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {
    private static List<Node> nodes;
    private static Random rand;

    private static Set<Integer> availablePorts;

    private static final int LOWER_PORT = 5050;
    private static final int UPPER_PORT = 5100;

    @org.junit.jupiter.api.BeforeAll
    static void setUp() {
        nodes = new ArrayList<>();
        rand = new Random();

        availablePorts = new HashSet<>();
        for(int port = LOWER_PORT; port<=UPPER_PORT; port++) {
            availablePorts.add(port);
        }
    }

    @Test
    void secondAndThirdNodeJoinCluster() throws IOException {
        Node node = new Node(getRandomPort(), "Alice");
        nodes.add(node);
        assertNotNull(node.getClusterNames());
        assertTrue(node.getClusterNames().isEmpty());

        Node node2 = new Node(getRandomPort(), "Bob");
        nodes.add(node2);
        assertNotNull(node2.getClusterNames());
        assertTrue(node.getClusterNames().values().contains(node2.getPort()));
        assertTrue(node2.getClusterNames().values().contains(node.getPort()));

        Node node3 = new Node(getRandomPort(), "Charlie");
        nodes.add(node3);
        assertNotNull(node3.getClusterNames());
        assertTrue(node3.getClusterNames().values().contains(node.getPort()));
        assertTrue(node3.getClusterNames().values().contains(node2.getPort()));
    }


    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        for(Node node: nodes) {
            //node.close();
        }

    }

    public int getRandomPort() {
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