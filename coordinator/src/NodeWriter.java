import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.*;

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
                    role.sendMessage(message);
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
                System.out.println("Thread is interrupted.. breaking from loop");
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
        inputScanner.close();
        System.out.println(role.node.name + ": NodeWriter closed");
    }
}
