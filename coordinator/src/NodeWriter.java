import java.io.IOException;
import java.util.Scanner;

public class NodeWriter extends Thread {

    private Role role;
    private Scanner inputScanner;
    private boolean running;

    public NodeWriter(Role role) {
        this.role = role;
        System.out.println("Node Writer active for node: " + role.getPort());
        inputScanner = new Scanner(System.in); // Create a Scanner object
    }

    public void getInput() {
        while (running) {
            System.out.print("\n What do you want to send to the other nodes?: ");
            String message = null;
            try {
                while (hasNextLine()) {
                    message = inputScanner.nextLine();
                    role.node.messageQueue.sendMessage(message);
                    // role.sendMessage(message);
                }
            } catch (IOException e) {
                running = false;
            }
        }
        inputScanner.close();
    }

    private boolean hasNextLine() throws IOException {
        while (System.in.available() == 0) {
            try {
                sleep(50);
            } catch (InterruptedException e) {
                System.out.println("NodeWriter is interrupted.. breaking from loop");
                return false;
            }
        }
        return inputScanner.hasNextLine();
    }

    public void run() {
        running = true;
        getInput();
    }

    public void close() {
        running = false;
        System.out.println(role.node.name + ": NodeWriter closed");
        // inputScanner.close(); // TODO: seems to block reElection (close Responsibilities)
    }
}
