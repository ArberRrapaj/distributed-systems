import java.io.*;
import java.net.Socket;
import java.nio.Buffer;

public class TCPSocketCommunicator extends Thread implements Communicator {
    private Node node;
    private Socket socket;
    protected String threadName = "default";
    private Thread worker;
    protected BufferedReader in;
    protected PrintWriter out;
    private String breakString;

    public TCPSocketCommunicator(Node node, Socket socket) {
        this.node = node;
        this.socket = socket;
        setupInOutput();
    }

    public void write(String message) {
        out.println(message);
        out.flush();
        System.out.println("Written: " + message);
    }

    public String read() throws IOException {
        return in.readLine();
    }

    private void setupInOutput() {
        try {
            InputStream input = socket.getInputStream();
            out = new PrintWriter(socket.getOutputStream());
            InputStreamReader streamReader = new InputStreamReader(input);
            in = new BufferedReader(streamReader);
        } catch (IOException e) {
            System.out.println("Failed to create in/out streams");
            System.exit(1);
        }

    }

    public int getPort() {
        return socket.getLocalPort();
    }

    public void setBreakString(String breakString) {
        this.breakString = breakString;
    }


    public String continuouslyRead() throws IOException {
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            if (inputLine.contains(breakString)){
                return inputLine;
            }
            System.out.println(socket.getPort() + "read: " + inputLine);
            String answer = node.getAnswer(inputLine, this);
            if(answer != null) {
                System.out.println(socket.getPort() + " answers: " + answer);
                out.println(answer);
                out.flush();
            }
        }

        return "";
    }

    public void run() {
        System.out.println("New thread");
        try {
            continuouslyRead();
        } catch(IOException e) {
            System.out.println("Node: " + socket.getLocalPort() + " failed to read.");
        }
        close();
    }

    public void close() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            System.out.println("Failed to close");
            System.exit(1);
        }
    }


}
