import java.io.*;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class TcpWriter extends Thread {
    private int id;
    private Socket socket;
    private Node node;
    private Role role;
    protected PrintWriter out;

    private final int TIMEOUT = 100;


    public TcpWriter(Role role, Node node) throws ConnectException {
        this.node = node;
        this.role = role;
    }

    public TcpWriter(Socket socket, Role role, Node node) {
        this.socket = socket;
        this.role = role;
        this.node = node;
        // System.out.println("\nOh, there is a new connection: " + socket);
        setupOutput();
    }

    public Socket connect(int port) throws ConnectException {
        // System.out.println("\nOh, seems like I - " + node.getPort() + " wanna connect to: " + port);
        socket = new Socket();
        SocketAddress address = new InetSocketAddress(port);
        try {
            socket.connect(address, TIMEOUT);
        } catch (IOException e) {
            close();
            throw new ConnectException("Failed to connect to: " + port + e);
        }
        // System.out.println("TCPClient connected socket: " + socket);
        setupOutput();
        return socket;
    }


    private void setupOutput() {
        try {
            out = new PrintWriter(socket.getOutputStream());
        } catch (IOException e) {
            // System.out.println("Failed to create in/out streams");
            System.exit(1);
        }
    }


    public void write(String message) {
        out.println(message);
        out.flush();
        // System.out.println("Written: " + message);
    }

    public void sendMessageFile() {
        FileInputStream fileInputStream;
        BufferedInputStream bis = null;
        OutputStream fileOutputStream = null;
        File tempFile = null;
        try {
            tempFile = ((Coordinator) role).getTempFileOf(id);
            // System.out.println(tempFile);

            if (tempFile.exists()) {

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                FileInputStream fis = new FileInputStream(tempFile);
                byte[] buffer = new byte[4096];

                int read;
                while ((read=fis.read(buffer)) > 0) {
                    dos.write(buffer,0,read);
                }
                fis.close();
                dos.flush();
                // dos.close();

            } // else; // System.out.println(node.name + ": No file, so not sending any file");

            // System.out.println("Done.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (tempFile != null) tempFile.delete();
                if (bis != null) bis.close();
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void close() {
        try {
            if (out != null) out.close();
            if (socket != null) socket.close();
            // System.out.println(node.name + ": TcpWriter closed");
        } catch (IOException e) {
            // System.out.println("Failed to close");
            // System.exit(1);
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public void setId(int id) {
        this.id = id;
    }
}
