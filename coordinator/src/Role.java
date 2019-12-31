import java.util.HashMap;
import java.util.Map;

public abstract class Role {

    protected Node node;
    protected NodeWriter nodeWriter;
    protected Thread nodeWriterThread;

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
        nodeWriterThread = new Thread(nodeWriter, "NodeWriter-"+node.getName());
        nodeWriterThread.start();
    }

    public abstract void sendMessage(String message);

    public void requestMessage(Integer messageIndex) {
        sendMessage(StandardMessages.REQUEST_MESSAGE.toString() + " " + messageIndex);
    }

    public abstract void actionOnMessage(Message message, boolean duringInformationExchange);

    public abstract void listenerDied(int port);

    public abstract boolean informationExchanged();

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

        if(nodeWriterThread != null) {
            nodeWriterThread.interrupt();
            nodeWriterThread = null;
        }
        if(nodeWriter != null) {
            nodeWriter.close();
            nodeWriter = null;
        }
        System.out.println(node.name + ": Role closed");
    }

    public abstract Status getStatus();

    public void addToCluster(Integer port, String name) {
        String existentVal = clusterNames.getOrDefault(port, null);
        if(existentVal == null) {
            clusterNames.put(port, name);
        } else if(!existentVal.equals(name)) {
            System.err.println("Overriding name of port " + port);
            clusterNames.put(port, name);
        }
    }

    public abstract void handleDeathOf(Integer port);
}
