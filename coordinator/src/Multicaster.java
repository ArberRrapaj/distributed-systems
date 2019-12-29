import java.io.IOException;
import java.net.*;

public class Multicaster extends Thread {

    private Node node;

    private boolean running;
    private int mcPort;
    private InetAddress mcGroup;
    private MulticastSocket mcSocket;


    public Multicaster(Node node, String ip, int port, int timeout) throws ConnectException {
        // join Multicast group
        this.node = node;
        mcPort = port;
        try {
            mcGroup = InetAddress.getByName(ip);
            mcSocket = new MulticastSocket(mcPort);
            mcSocket.joinGroup(mcGroup);
            mcSocket.setSoTimeout(timeout);
        } catch(UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        } catch(IOException e) {
            e.printStackTrace();
            throw new ConnectException("Socket in use or failed to join Multicast group: " );
        }
    }

    public void run() {
        running = true;
        listen();
    }

    private void listen() {
        try{
            mcSocket.setSoTimeout(0);
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }

        while(running) {
           try {
               Message received = receive();
               if (received == null); // System.out.println("I've received my own message from multicast");
               else if (received.startsWith(StandardMessages.CLUSTER_SEARCH.toString())) {
                   node.answerSearchRequest(received);
               } else if(received.getIndex() != null) {
                    node.messageQueue.handleNewMessage(received);
               } else if (received.startsWith(StandardMessages.REQUEST_MESSAGE.toString())) {
                   System.out.println(node.name + ": Received a message request: " + received.toString());
                    // Get message with that index
                   int index = Integer.parseInt(received.toString().substring(StandardMessages.REQUEST_MESSAGE.length() + 1));
                   String requestedMessage = node.messageQueue.getMessage(index);
                   System.out.println(node.name + ": I would send this to you: " + requestedMessage);
                   send(requestedMessage);
               } else if (received.startsWith(StandardMessages.REQUEST_MESSAGE_ANSWER.toString())) {
                   node.messageQueue.receivedRequestAnswer(received.sanitizeMessage(StandardMessages.REQUEST_MESSAGE_ANSWER));
               } else {
                   System.err.println("Received unexpected Multicast message: " + received);
               }
           } catch(SocketTimeoutException e) {
               // nothing received, repeat
           } catch(IOException e) {
               System.out.println(node.name + ": Socket closed");
               running = false;
               // e.printStackTrace();

               // System.exit(1);
           }
       }
    }

    public void send(String message) throws IOException {
        System.out.println(node.getPort() + " sending multicast message: " + message);
        String mcMessage = node.getPort() + "|" + message;
        byte[] buf = mcMessage.getBytes();

        DatagramPacket packet = new DatagramPacket(buf, buf.length, mcGroup, mcPort);
        mcSocket.send(packet);
    }

    public Message receive() throws IOException {
        byte[] buf = new byte[256];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);
        mcSocket.receive(recv);

        String mcReceived = new String(recv.getData(), recv.getOffset(), recv.getLength());
        String[] receivedSplit = mcReceived.split("\\|");

        int sender = Integer.parseInt(receivedSplit[0]);
        if (sender == node.getPort()) {
            // TODO: handle differently
            return null; // ignore messages sent by yourself
        }
        String content = receivedSplit[1];
        System.out.println(node.getPort() + " [Multicast UDP message received] >> " + content);
        if (content.startsWith(StandardMessages.NEW_MESSAGE.toString())) {
            content = content.substring(StandardMessages.NEW_MESSAGE.toString().length() + 1);
            String[] messageSplit = content.split("\\$", 4);
            // System.out.println(messageSplit[0]);
            int index = Integer.parseInt(messageSplit[0]);
            if (messageSplit[3].startsWith(StandardMessages.CLOSE.toString())) {
                node.close();
            }
            return new Message(sender, index, messageSplit[1], messageSplit[2], messageSplit[3]);
        } else return new Message(sender, content);
    }

    public void close() {
        try {
            running = false;
            mcSocket.leaveGroup(mcGroup);
            mcSocket.close();
        } catch (IOException e) {
            // failed to leave. ignore.
        }
        System.out.println(node.name + ": Multicaster closed");
    }
}

