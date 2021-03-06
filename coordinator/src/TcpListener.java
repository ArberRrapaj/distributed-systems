import java.io.*;
import java.net.Socket;

public class TcpListener extends Thread {

    public boolean receivingFile = false;
    protected BufferedReader in;
    private Role role;
    private Node node;
    private int port;
    private Socket socket;
    private volatile boolean listening = true;
    volatile boolean informationExchanged = false;

    public int FILE_SIZE;

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
            // System.out.println("Seems like my true love, port " + socket.getPort() + " has just died. That means, me, the TcpListener of " + node.getName() + " will die now too, see you in hell.");
            try {
                if (in != null) in.close(); // TODO: blocks
                socket.close();
            } catch (IOException e) {
                // e.printStackTrace();
                // System.out.println("Couldn't close input-stream or socket ¯\\_(ツ)_/¯");
            }
        }
        // System.out.println(node.name + ": TcpListener closed");
    }

    public void run() {
        // informationExchanged = true;
        // System.out.println("Me, a TcpListener will listen to (external) " + socket.getPort() + " on port (internal): " + socket.getLocalPort());

        while (listening && !informationExchanged) {
            try {
                String nextLine = in.readLine();

                if (nextLine != null) {
                    // System.out.println("TcpListener: " + node.getPort() + " : " + nextLine);
                    Message message = new Message(port, nextLine);
                    role.actionOnMessage(message, true);
                } else {
                    // listener died
                    // System.out.println(node.name + ": TcpListener died during information exchange");
                    role.listenerDied(port);
                }
            } catch (IOException ioe) {
                // System.out.println(node.name + ": TcpListener Exception during information exchange");
                role.listenerDied(port);
            }
        }

        if (receivingFile) {
            // System.out.println(node.name + ": ReceivingFile is listening, filesize: " + FILE_SIZE);

            int bytesRead;
            int current = 0;

            try {

                DataInputStream dis = new DataInputStream(socket.getInputStream());
                FileOutputStream fos = new FileOutputStream(node.name + ".txt");
                byte[] buffer = new byte[4096];

                int read = 0;
                int totalRead = 0;
                int remaining = FILE_SIZE;
                while((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                    totalRead += read;
                    remaining -= read;
                    // System.out.println("read " + totalRead + " bytes.");
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                fos.close();
                // dis.close();

                node.initializeWriteIndex();
            } catch (IOException e) {
                // e.printStackTrace();
                // System.out.println(node.name + ": TcpListener Exception during file base exchange");
                role.listenerDied(port);
            }

        }

        // System.out.println(node.name + ": information was exchanged, I'll listen the right way now");
        node.messageQueue.sendQueuedMessages();

        while (listening) {
            try {
                String nextLine = in.readLine();
                // System.out.println(node.name + ": nextLine from readLine() = " + nextLine);

                if (nextLine != null) {
                    // System.out.println("TcpListener: " + node.getPort() + " : " + nextLine);
                    Message message = new Message(port, nextLine);
                    role.actionOnMessage(message, false);
                } else {
                    // listener died
                    // System.out.println(node.name + ": TcpListener-RUN Exception#1");
                    role.listenerDied(port);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
                // System.out.println(node.name + ": TcpListener-RUN Exception#2");
                role.listenerDied(port);
            }
        }

    }

    public void setPort(int port) { this.port = port; }

}
