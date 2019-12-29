import java.io.IOException;
import java.util.*;

public class MessageQueue {

    Node node;
    Map<Integer, Message> toWrite = new HashMap<>();
    Queue<String> toSend = new LinkedList<>();
    Map<Integer, Message> messages = new HashMap<>();

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

    public void handleNewMessage(Message received) {
        System.out.println("That's a new message, Index: " + received.getIndex() + "; My WriteIndex: " + node.getCurrentWriteIndex());

        System.out.println("handleNewMessage-Participant");
        if ( node.getNextWriteIndex() == received.getIndex() ){
            System.out.println("It's the next message according to the write index, so let's write it down");
            node.writeToFile(received);
            node.getNewWriteIndex();
            checkForAvailableMessagesToWrite();
        } else if (node.getNextWriteIndex() < received.getIndex()){
            receivedHigherMessage(received);
        }
    }

    public void receivedHigherMessage(Message message) {
        System.out.println(node.name + ": receivedHigherMessage");

        toWrite.put(message.getIndex(), message);
        Set<Integer> keys = toWrite.keySet();

        int maxIndex = -1;
        for(int key : keys) if (maxIndex < key) maxIndex = key;

        // TODO: Rewrite, many potential redundant messages
        for (int i = node.writeIndex + 1; i <= maxIndex; i++) {
            if (toWrite.get(i) == null) requestMessage(i);
        }
    }

    public void requestMessage(int i) {
        try {
            node.multicaster.send(StandardMessages.REQUEST_MESSAGE.toString() + " " + i);
        } catch (IOException e) {
            e.printStackTrace();
            // TODO: Retry?
        }
    }

    public void checkForAvailableMessagesToWrite() {
        for(int element : toWrite.keySet()) {
            node.writeToFile(toWrite.get(element));
        }
    }

    public String getMessage(int index) {
        if (index >= node.getCurrentWriteIndex()) return null;
        if (messages.size() > 0) {
            Message messageToReturn = messages.get(index);
            if (messageToReturn == null) return null;
            else return messageToReturn.asRequestedMessage();
        } else return node.lookForIndexInFile(index);
    }

    public void putIntoMessages(int index, String line) {

    }

    public void receivedRequestAnswer(Message message) {
        if (message.getIndex() <= node.getCurrentWriteIndex()) return;
        handleNewMessage(message);
    }
}
