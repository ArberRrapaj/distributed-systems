package alt_approach;

import java.util.Scanner;

public class NodeWriter extends Thread {

    private Node node;
    private Scanner inputScanner;
    private boolean running;

    public NodeWriter(Node node) {
        this.node = node;
        System.out.println("Node Writer active for node: " + node.getPort());
        inputScanner = new Scanner(System.in); // Create a Scanner object
    }

    public void getInput() {
        while (running) {
            System.out.print("\n What do you want to send the other nodes?: ");
            String message = inputScanner.nextLine(); // Read user input
            node.sendMessage(message);
        }
        inputScanner.close();
    }

    public void run() {
        running = true;
        getInput();
    }
}
