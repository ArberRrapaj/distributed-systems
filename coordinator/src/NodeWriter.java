
import com.oracle.tools.packager.IOUtils;

import java.util.NoSuchElementException;
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
            System.out.print("\n What do you want to send the other nodes?: ");
            try {
                String message = inputScanner.nextLine(); // Read user input
                role.sendMessage(message);
            } catch (NoSuchElementException e) {
                role.close();
            }
        }
        inputScanner.close();
    }

    public void run() {
        running = true;
        getInput();
    }

    public void close() {
        running = false;
        //inputScanner.close(); // TODO: seems to block reElection (close Responsibilities)
    }
}
