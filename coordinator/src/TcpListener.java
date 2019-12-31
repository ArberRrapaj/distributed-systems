import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;


public class TcpListener extends Thread {

    protected BufferedReader in;
    private Role role;
    private Node node;
    private int port;
    Socket socket;
    private volatile boolean listening = true;
    volatile boolean informationExchanged = false;

    public TcpListener(Role role, Node node, Socket socket, int port) throws IOException {
        this.role = role;
        this.node = node;
        this.socket = socket;
        this.port = port;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // System.out.println(socket);
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
        // informationExchanged = true;
        System.out.println("Me, a TcpListener will listen to (external) " + socket.getPort() + " on port (internal): " + socket.getLocalPort());

        /*
            Wait for initial node information, message to wait for depends on role
            TODO: not really necessary to do it like this, only need the duringInformationExchange flag, this is only for debugging(prints)
         */

        while (listening && !informationExchanged) {
            try {
                String nextLine = in.readLine();

                if (nextLine != null) {
                    System.out.println("TcpListener: " + node.getPort() + " : " + nextLine);
                    Message message = new Message(socket.getPort(), nextLine);
                    role.actionOnMessage(message, true);
                } else {
                    // listener died
                    System.out.println(node.name + ": TcpListener died during information exchange");
                    role.listenerDied(port);
                }
            } catch (IOException ioe) {
                System.out.println(node.name + ": TcpListener Exception during information exchange");
                role.listenerDied(port);
            }
        }

        System.out.println(node.name + ": information was exchanged, I'll listen the right way now");

        while (listening) {
            try {
                String nextLine = in.readLine();
                System.out.println(node.name + ": nextLine from readLine() = " + nextLine);

                if (nextLine != null) {
                    System.out.println("TcpListener: " + node.getPort() + " : " + nextLine);
                    Message message = new Message(port, nextLine);
                    role.actionOnMessage(message, false);
                } else {
                    // listener died
                    System.out.println(node.name + ": TcpListener-RUN Exception");
                    role.listenerDied(port);
                }
            } catch (IOException ioe) {
                System.out.println(node.name + ": TcpListener-RUN Exception");
                role.listenerDied(port);
            }
        }

    }

    public void stopListening() {
        listening = false;
    }

    public void restartListening() {
        listening = true;
    }

    public void setPort(int port) { this.port = port; }

}
