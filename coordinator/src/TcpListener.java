import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;


public class TcpListener extends Thread {


    protected BufferedReader in;
    private Role role;
    private Node node;
    private Socket socket;
    private volatile boolean running = true;
    private volatile boolean listening = true;

    public TcpListener(Role role, Node node, Socket socket) throws IOException {
        this.role = role;
        this.node = node;
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println(socket);
    }

    public void close() {
        running = false;
        System.out.println("Seems like my true love, port " + socket.getPort() + " has just died. That means, me, the TcpListener will die now too, see you in hell.");

        try {
            if (in != null) in.close();
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("Couldn't close input-stream ¯\\_(ツ)_/¯");
        }
    }

    public void run() {
        System.out.println("Me, a TcpListener will listen to (external) " + socket.getPort() + " on port (internal): " + socket.getLocalPort());
        while (running) {

            if (listening) {
                try {
                    String nextLine = in.readLine();

                    if (nextLine != null) {
                        System.out.println(nextLine);
                        role.actionOnMessage(nextLine);
                    }
                } catch (IOException ioe) {
                    System.out.println(ioe);
                    node.listenerDied();
                }
            }
        }

    }

    public void stopListening() {
        listening = false;
    }

    public void restartListening() {
        listening = true;
    }

}
