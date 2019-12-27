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
            String message = inputScanner.nextLine(); // Read user input
            role.sendMessage(message);
        }
        inputScanner.close();
    }

    public void run() {
        running = true;
        getInput();
    }
}
