import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {
    private static List<Node> nodes;
    private static Random rand;

    private static Set<Integer> availablePorts;

    private String IP = "localhost";
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
    void secondAndThirdNodeJoinCluster() throws IOException, InterruptedException {
        Node node = new Node(IP, getRandomPort());
        nodes.add(node);
        node.searchCluster();
        assertNotNull(node.getCluster());
        assertTrue(node.getCluster().isEmpty());

        Node node2 = new Node(IP, getRandomPort());
        nodes.add(node2);
        node2.searchCluster();
        assertNotNull(node2.getCluster());
        assertTrue(node.getCluster().values().contains(node2.getPort()));
        assertTrue(node2.getCluster().values().contains(node.getPort()));

        Node node3 = new Node(IP, getRandomPort());
        nodes.add(node3);
        node3.searchCluster();
        assertNotNull(node3.getCluster());
        assertTrue(node3.getCluster().values().contains(node.getPort()));
        assertTrue(node3.getCluster().values().contains(node2.getPort()));
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