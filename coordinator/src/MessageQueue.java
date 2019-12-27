import com.sun.istack.internal.NotNull;

import java.util.*;

public class MessageQueue {

    Node node;
    Map<Integer, Message> toWrite = new HashMap<>();
    Queue<String> toSend = new LinkedList<>();

    public MessageQueue(Node node) {
        this.node = node;
    }

    public void queueMessage(String message) {
        // timestamp?
        toSend.add(message);
    }

    public void sendQueuedMessages() {
        for (String message: toSend) {
            node.role.sendMessage(message);
        }
    }

    public void receivedMessage(int messageIndex) {
        toWrite.remove(messageIndex);
    }

    public void receivedHigherMessage(Message message) {
        toWrite.put(message.getIndex(), message);
        Set<Integer> keys = toWrite.keySet();

        int maxIndex = -1;
        for(int key : keys) if (maxIndex < key) maxIndex = key;

        // TODO: Rewrite, many potential redundant messages
        for (int i = node.writeIndex + 1; i <= maxIndex; i++) {
            if (toWrite.get(i) == null) node.role.requestMessage(i);
        }
    }

    public void checkForAvailableMessagesToWrite() {
        for(int element : toWrite.keySet()) {
            node.writeToFile(toWrite.get(element));
        }

    }
}
