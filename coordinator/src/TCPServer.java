import com.sun.corba.se.spi.activation.Server;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class TCPServer {
    private ServerSocket serverSocket;
    private Map<Integer, Socket> sockets;



    public TCPServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
        } catch(IOException e) {
            System.out.println("Failed to create ServerSocket on port: " + port + "\n" + e);
            System.exit(1);
        }

        sockets = new HashMap<>();
    }


    public Socket connect() {
        Socket socket = null;
        try {
            socket = serverSocket.accept();
        } catch (IOException e) {
            System.out.println("Failed to accept client");
            System.exit(1);
        }

        if(socket != null) {
            System.out.println("TCPServer – New connection: " + socket);
            sockets.put(socket.getLocalPort(), socket);

        }


        return socket;
    }

    public void close() {
        try {
            serverSocket.close();
            for(Socket socket : sockets.values()) {
                socket.close();
            }
        } catch (IOException e) {
            System.out.println("Failed to close.");
        }
    }

}
