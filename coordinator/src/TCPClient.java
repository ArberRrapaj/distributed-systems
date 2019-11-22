import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

public class TCPClient {

    private Socket socket;
    private String threadName = "TCPClient-default";

    private final int TIMEOUT = 100;

    public TCPClient() {
        socket = new Socket();
    }


    public Socket connect(int port) throws IOException {
        SocketAddress address = new InetSocketAddress(port);
        socket.connect(address, TIMEOUT);
        System.out.println("TCPClient connected socket: " + socket);

        return socket;
    }




}
