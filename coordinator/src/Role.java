import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public abstract class Role {
    protected ServerSocket newConnectionsSocket;

    protected Node node;
    protected NodeWriter nodeWriter;

    public int getPort() {
        return node.getPort();
    }

    protected Map<Integer, String> cluster;
    public Map<Integer, String> getCluster() {
        return cluster;
    }

    public Role(Node node) {
        this.node = node;

        cluster = new HashMap<>();

        nodeWriter = new NodeWriter(this);


        // Setup the socket server
        try {
            newConnectionsSocket = new ServerSocket(node.getPort());
            System.out.println("\nI'll be listening for new connections on port: " + node.getPort());
        } catch (IOException e) {
            System.out.println("There was an error setting up the newConnectionsSocket");
            e.printStackTrace();
            close();
            System.exit(1);
        }
    }

    public abstract void sendMessage(String message);

    public abstract void actionOnMessage(Message message);

    public int printCurrentlyConnected() {
        int connectedUsers = cluster.keySet().size();
        System.out.println("Cluster status:");
        switch (connectedUsers) {
            case 0:
                System.out.println("  --> I have no gang");
                break;
            case 1:
                System.out.println("  --> There is one person in my gang: " + connectedUsers);
                break;
            default:
                System.out.println("  --> My gang consists of " + connectedUsers);
                break;
        }
        return connectedUsers;
    }


    protected void close() {
        try {
            newConnectionsSocket.close();
        } catch (IOException e) {
            // failed to close. ignore.
        }
    }
}
