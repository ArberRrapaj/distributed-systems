import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

public abstract class Role {

    protected Node node;
    protected NodeWriter nodeWriter;

    public int getPort() {
        return node.getPort();
    }

    public String getName()  {
        return node.getName();
    }

    protected Map<Integer, String> clusterNames;
    public Map<Integer, String> getClusterNames() {
        return clusterNames;
    }

    public Role(Node node) {
        this.node = node;

        clusterNames = new HashMap<>();

        nodeWriter = new NodeWriter(this);
        nodeWriter.start();

    }

    public abstract void sendMessage(String message);

    public abstract void actionOnMessage(Message message);

    public abstract void listenerDied(int port);

    protected int getWriteIndex() {
        return node.getWriteIndex();
    }
;

    public int printCurrentlyConnected() {
        int connectedUsers = clusterNames.keySet().size();
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
        if(nodeWriter != null) {
            nodeWriter.close();
        }
    }

    public abstract Status getStatus();

    public void addToCluster(Integer port, String name) {
        clusterNames.put(port, name);
    }

    public abstract void handleDeathOf(Integer port);
}
