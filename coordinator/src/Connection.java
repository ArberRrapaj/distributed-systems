import java.io.IOException;

public class Connection {

    TCPSocketCommunicator clientCommunicator;
    TCPSocketCommunicator serverCommunicator;

    public Connection(TCPSocketCommunicator clientCommunicator,
                      TCPSocketCommunicator serverCommunicator) {
        this.clientCommunicator = clientCommunicator;
        this.serverCommunicator = serverCommunicator;
    }

    public String read() throws IOException {
        return serverCommunicator.read();
    }

    public void close() {
        clientCommunicator.close();
        serverCommunicator.close();
    }
}
