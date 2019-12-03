import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class ClusterNode extends Thread {
    private int port;
    private int connectionPort;
    private Socket socket;
    protected Node headNode;
    private int id;
    protected BufferedReader in;
    protected PrintWriter out;
    private ClusterNodeListener listener;
    private boolean listening = true;

    private final int TIMEOUT = 100;
    private String timestamp;
    private String hash;


    public ClusterNode(int port, int portToConnectTo, Node headNode) throws ConnectException {
        this.headNode = headNode;
        this.port = port;
        setId(portToConnectTo);
        connect(portToConnectTo);
        setupInOutput();
        System.out.println("\nOh, seems like I wanna connect to: " + portToConnectTo);
    }

    public ClusterNode(int port, Socket socket, Node headNode) {
        this.port = port;
        this.socket = socket;
        this.headNode = headNode;
        this.id = socket.getPort();
        System.out.println("\nOh, there is a new connection: " + socket);
        setupInOutput();
    }

    public Socket connect(int port) throws ConnectException {
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
        return socket;
    }


    public void run() {
        String answer = handleHandshake("exit");
        if (!answer.isEmpty()) startTightRelationship();
    }

    public String handleHandshake(String breakString) {
        String inputLine;
        while (true) {
            try {
                // System.out.println("Trying to readLine in handleHandshake");
                // TODO: add timeout here?
                inputLine = in.readLine();
                // System.out.println("read line in readLine in handleHandshake");
                if (inputLine == null) break;
                System.out.println(headNode.getPort() + " read: " + inputLine);
                if (inputLine.contains(breakString)) {
                    return inputLine;
                }
                String answer = headNode.getAnswer(inputLine, this);
                // System.out.println("Answe: " + answer);
                if (answer != null) {
                    System.out.println(headNode.getPort() + " answers: " + answer);
                    out.println(answer);
                    out.flush();
                    // System.out.println(answer);
                    if (answer.equals("ACCEPT")) return "ACCEPT";
                    if (answer.equals("THANKS")) return "THANKS";
                } // What is else = bullshit request?
            } catch (IOException e) {
                // e.printStackTrace();
                listening = false;
                System.out.println("Node: " + socket.getLocalPort() + " failed to read.");
            }

        }
        return "";
    }


    public void setupInOutput() {
        try {
            out = new PrintWriter(socket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.out.println("Failed to create in/out streams");
            System.exit(1);
        }
    }

    public void communicateJoin() {
        write("I (" + port + ") want to JOIN");
        String answer = handleHandshake("ACCEPT");
        if (!answer.isEmpty()) {
            // Accepted, start listening deamon
            startTightRelationship();
        }
    }

    private void startTightRelationship() {
        listener = new ClusterNodeListener(this, socket, in);
        listener.start();
    }

    public void write(String message) {
        out.println(message);
        out.flush();
        System.out.println("Written: " + message);
    }

    public void close() {
        try {
            if (listener != null) listener.close();
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("Failed to close");
            // System.exit(1);
        }
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getID() {
        return id;
    }


    public void listenerDied() {
        listener.close();
        listener = null;
        System.out.println("Seems like my listener, the bastard, killed himself, so there is no need for me to be in this imperfect world anymore. See you in hell.");
        headNode.clusterNodeDied(id);
    }

    public String requestTimestamp() {
        listener.stopListening();
        write("Hey coordinator, can you please tell me the time?");
        String answer = handleHandshake("THANKS");
        if (answer == "THANKS") System.out.println("Got the timestamp");
        System.out.println("The timestamp: " + timestamp);
        listener.restartListening();
        return timestamp;
    }

    public void receivedMessageToWrite(String message) {
        headNode.writeTextToFile(message);
        String lastLine = headNode.readLastLineOfFile();
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
        String answer = handleHandshake("THANKS");
        if (answer == "THANKS") System.out.println("Got the hash");
        System.out.println("The Hash: " + hash);
        if (hash.equals("null")) {
            System.out.println("Seems like the coordinator had no file, gonna delete mine too then");
            headNode.deleteMessagesFile(); //TODO: reelection
        } else {
            if (hash.equals(headNode.getFilesHash())) System.out.println("File is up to date");
            else {
                // Okay krise, Datei ist nicht gleich
                // TODO: ask coordinator for complete file
            }
        }
        listener.restartListening();
    }
}
