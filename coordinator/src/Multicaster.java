import java.io.IOException;
import java.net.*;

public class Multicaster extends Thread {

    private Node node;

    private volatile boolean listening;
    private int mcPort;
    private InetAddress mcGroup;
    private MulticastSocket mcSocket;
    private MessageQueue messageQueue;


    public Multicaster(Node node, MessageQueue messageQueue, String ip, int port, int timeout) throws ConnectException {
        setName("Multicaster-"+node.getName());
        // join Multicast group
        this.node = node;
        this.messageQueue = messageQueue;
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
        listen();
    }

    private void listen() {
        listening = true;

        try{
            mcSocket.setSoTimeout(0);
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
        while(listening) {
            if(node.getStatus() == Status.DEAD) {
                close();
                break;
            }
            try {
               Message received = receive();
               if (received != null && !received.getText().isEmpty()) {
                   if (received.startsWith(Status.SEARCHING.toString())) {
                       node.answerSearchRequest(received);
                   } else if (received.startsWith(Status.DEAD.toString())) {
                       node.handleDeathOf(received.getSender());
                   } else if (received.startsWith(Status.ELECTION.toString())) {
                       if(node.getStatus().isInElection()) {
                           node.documentCandidate(received);
                       } else {
                           new Thread(() -> {
                               node.reElection();
                               node.documentCandidate(received);
                           }, "reElection-mc-"+getName()).start();
                       }
                   } else if (received.startsWith(Status.ELECTED.toString())) {
                       node.documentElected(received);
                   } else if(received.getIndex() != null) {
                       messageQueue.handleNewMessage(received);
                   } else if (received.startsWith(StandardMessages.REQUEST_MESSAGE.toString())) {
                       System.out.println(node.getName() + ": Received a message request: " + received.toString());
                       // Get message with that index
                       int index = Integer.parseInt(received.withoutStandardPart(StandardMessages.REQUEST_MESSAGE));
                       String requestedMessage = node.messageQueue.getMessage(index);
                       System.out.println(node.name + ": I would send this to you: " + requestedMessage);
                       if (requestedMessage != null) send(requestedMessage);
                   } else if (received.startsWith(StandardMessages.REQUEST_MESSAGE_ANSWER.toString())) {
                      messageQueue.receivedRequestAnswer(received.sanitizeMessage(StandardMessages.REQUEST_MESSAGE_ANSWER));
                   } else {
                       //System.err.println("Received unattended Multicast message: " + received);
                   }
               }
           } catch(SocketTimeoutException e) {
               // nothing received, repeat
           } catch(IOException e) {
               System.out.println(node.getName() + ": Socket closed");
               // e.printStackTrace();
               node.suicide();
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
        listening = false;
        try {
            mcSocket.leaveGroup(mcGroup);
            mcSocket.close();
        } catch (SocketException e) {
            mcSocket.close();
        } catch (IOException e) {
            // failed to leave. ignore.
        }
        System.out.println(node.getName() + ": Multicaster closed");
    }
}

