import java.util.Random;

public class Main {

    static String IP = "localhost";
    static int LOWER_PORT = 5050;
    static int UPPER_PORT = 5100;

    public static Random rand;

    public static void main(String[] args) {
        Node node = new Node(5075);
        node.searchCluster();

        rand = new Random();
        int randPort = getRandomPort();
        Node node2 = new Node(randPort);
        node2.searchCluster();

        Random rand = new Random();
        int randPort2;
        while((randPort2 = getRandomPort()) == randPort);
        Node node3 = new Node(randPort2);
        node3.searchCluster();

    }

    public static int getRandomPort() {
        return rand.nextInt(UPPER_PORT - LOWER_PORT) + LOWER_PORT;
    }


}
