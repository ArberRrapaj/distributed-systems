import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;


public class TcpListener extends Thread {

    protected BufferedReader in;
    private Role role;
    private Node node;
    Socket socket;
    private volatile boolean listening = true;

    public TcpListener(Role role, Node node, Socket socket) throws IOException {
        this.role = role;
        this.node = node;
        this.socket = socket;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        System.out.println(socket);
    }

    public void close() {

        if(listening) {
            listening = false;
            System.out.println("Seems like my true love, port " + socket.getPort() + " has just died. That means, me, the TcpListener of " + node.getName() + " will die now too, see you in hell.");
            try {
                if (in != null) in.close(); // TODO: blocks
                socket.close();
            } catch (IOException e) {
                // e.printStackTrace();
                System.out.println("Couldn't close input-stream or socket ¯\\_(ツ)_/¯");
            }
        }
        System.out.println(node.name + ": TcpListener closed");
    }

    // TODO: call handleDeathOf as coordinator when fails
    public void run() {
        System.out.println("Me, a TcpListener will listen to (external) " + socket.getPort() + " on port (internal): " + socket.getLocalPort());
        while (listening) {

            try {
                String nextLine = in.readLine();

                if (nextLine != null) {
                    System.out.println("TcpListener: " + node.getPort() + " : " + nextLine);
                    Message message = new Message(socket.getPort(), nextLine);
                    role.actionOnMessage(message);
                }
            } catch (IOException ioe) {
                System.out.println(node.name + ": TcpListener-RUN Exception");
                role.listenerDied(socket.getPort());
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
