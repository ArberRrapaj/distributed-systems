import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MessageQueue {

    Node node;
    Map<Integer, Message> toWrite = new HashMap<>();
    Queue<String> toSend = new LinkedList<>();
    Map<Integer, Message> messages = new HashMap<>();

    public MessageQueue(Node node) {
        this.node = node;
    }



    public void queueMessage(String message) {
        toSend.add(message);
    }

    public void sendQueuedMessages() {
        for (String message: toSend) {
            System.out.println(node.name + ": calling sendqueuedmessages");
            node.role.sendMessage(message);
        }
    }

    public void handleNewMessage(Message received) {
        // System.out.println(node.name + ": That's a new message, Index: " + received.getIndex() + "; My WriteIndex: " + node.getCurrentWriteIndex());

        System.out.println("handleNewMessage-Participant");
        if ( node.getNextWriteIndex() == received.getIndex() ){
            System.out.println(node.name + ": It's the next message according to the write index, so let's write it down; wi:" + node.getCurrentWriteIndex() + "; mi:" + received.getIndex());
            node.writeToFile(received);
            node.getNewWriteIndex();
            checkForAvailableMessagesToWrite();
        } else if (node.getNextWriteIndex() < received.getIndex()){
            receivedHigherMessage(received);
        }
    }

    public void receivedHigherMessage(Message message) {
        System.out.println(node.name + ": a higher message, according to write index, ask for oldies; wi:" + node.getCurrentWriteIndex() + "; mi:" + message.getIndex());
        // System.out.println(node.name + ": receivedHigherMessage");

        toWrite.put(message.getIndex(), message);
        Set<Integer> keys = toWrite.keySet();

        int maxIndex = -1;
        for(int key : keys) if (maxIndex < key) maxIndex = key;

        // TODO: Optimize, many potential redundant messages
        for (int i = node.writeIndex + 1; i <= maxIndex; i++) {
            if (toWrite.get(i) == null) requestMessage(i);
        }
    }

    public void requestMessage(int i) {
        System.out.println(node.name + ": requesting message: " + i);

        try {
            node.multicaster.send(StandardMessages.REQUEST_MESSAGE.toString() + " " + i);
        } catch (IOException e) {
            e.printStackTrace();
            // TODO: Retry?
        }
    }

    public void requestMissingMessages(int indexFromCoordinator) {
        System.out.println(node.name + ": looking for missing messages to request wi:" + node.getCurrentWriteIndex() + "; ci:" + indexFromCoordinator);

        int currentWriteIndex = node.getCurrentWriteIndex();
        if (indexFromCoordinator > currentWriteIndex) {
            for (int i = currentWriteIndex + 1; i < indexFromCoordinator + 1; i++) {
                if (toWrite.get(i) == null) requestMessage(i);
            }
        }
    }

    public void checkForAvailableMessagesToWrite() {
        System.out.println(node.name + ": Check for available messages to write");

        // Iterator<Integer> it = toWrite.keySet().iterator();

        List<Integer> iterationList = toWrite.keySet().stream().sorted().collect(Collectors.toList());

        for (Integer index : iterationList) {
            Message message = toWrite.get(index);
            if (node.getNextWriteIndex() == message.getIndex()) {
                toWrite.remove(index);
                node.writeToFile(message);
                node.getNewWriteIndex();
            } else if (node.getNextWriteIndex() > message.getIndex()) {
                toWrite.remove(index);
            } else return;
        }

        /*
        // Iterate over all the elements
        while (it.hasNext()) {
            Integer index = it.next();
            Message message = toWrite.get(index);
            it.remove();
            System.out.println(node.name + ": I'm iterating, baby; " + index);
            node.writeToFile(message);
        }

         */

    }

    public String getMessage(int index) {
        if (index > node.getCurrentWriteIndex()) return null;
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

    public void sendMessage(String message) {
        if (node.role.informationExchanged()) node.role.sendMessage(message);
        else queueMessage(message);
    }

    public static
    <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
        List<T> list = new ArrayList<T>(c);
        java.util.Collections.sort(list);
        return list;
    }
}
