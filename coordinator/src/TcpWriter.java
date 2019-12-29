import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class TcpWriter extends Thread {
    private int port;
    private int connectionPort;
    private Socket socket;
    protected Node node;
    protected Role role;
    private int id;
    protected BufferedReader in;
    protected PrintWriter out;
    private TcpListener listener;
    private boolean listening = true;

    private final int TIMEOUT = 100;
    private String timestamp;
    private String hash;


    public TcpWriter(int port, int portToConnectTo, Role role, Node node) throws ConnectException {
        this.node = node;
        this.role = role;
        this.port = port;
        id = portToConnectTo;
        connect(portToConnectTo);
        setupInOutput();
        System.out.println("\nOh, seems like I - " + node.getPort() + " wanna connect to: " + portToConnectTo);
    }

    public TcpWriter(int port, Socket socket, Node node) {
        this.port = port;
        this.socket = socket;
        this.node = node;
        this.id = socket.getPort();
        System.out.println("\nOh, there is a new connection: " + socket);
        setupInOutput();
    }

    private void connect(int port) throws ConnectException {
        socket = new Socket();
        SocketAddress address = new InetSocketAddress(port);
        try {
            socket.connect(address, TIMEOUT);
        } catch (IOException e) {
            close();
            throw new ConnectException("Failed to connect to: " + port + e);
        }
        System.out.println("TCPClient connected socket: " + socket);
        connectionPort = socket.getLocalPort();
    }


    private void setupInOutput() {
        try {
            out = new PrintWriter(socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.out.println("Failed to create in/out streams");
            System.exit(1);
        }
    }


    public void write(String message) {
        out.println(message);
        out.flush();
        System.out.println("Written: " + message);
    }

    public void close() {
        try {
            if (listener != null) listener.close();
            // if (in != null) in.close(); // TODO: blocks
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("Failed to close");
            // System.exit(1);
        }
    }

    public Socket getSocket() {
        return socket;
    }

    /*

    public void setId(int id) {
        this.id = id;
    }

    public Integer getID() {
        return id;
    }

    public String requestTimestamp() {
        listener.stopListening();
        write("Hey coordinator, can you please tell me the time?");
        // TODO: String answer = handleHandshake("THANKS");
        // TODO: if (answer == "THANKS") System.out.println("Got the timestamp");
        System.out.println("The timestamp: " + timestamp);
        listener.restartListening();
        return timestamp;
    }

    public void receivedMessageToWrite(String message) {
        node.writeTextToFile(message);
        String lastLine = node.readLastLineOfFile();
        System.out.println(lastLine);
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getHash() {
        return hash;
    }

    public int getPort() {
        return port;
    }

    public void handleMessagesFile() {
        listener.stopListening();
        write(StandardMessages.SEND_FILE_HASH.toString());
        // TODO: String answer = handleHandshake("THANKS");
        // TODO: if (answer == "THANKS") System.out.println("Got the hash");
        System.out.println("The Hash: " + hash);
        if (hash.equals("null")) {
            System.out.println("Seems like the coordinator had no file, gonna delete mine too then");
            node.deleteMessagesFile(); //TODO: reelection
        } else {
            if (hash.equals(node.getFilesHash())) System.out.println("File is up to date");
            else {
                // Okay krise, Datei ist nicht gleich
                // TODO: ask coordinator for complete file
            }
        }
        listener.restartListening();
    }
    */
}
