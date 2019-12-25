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

        while(true) {
           try {
               Message received = receive();
               if (received.startsWith(StandardMessages.CLUSTER_SEARCH.toString())) {
                   node.answerSearchRequest(received);
               } else {
                   System.err.println("Received unexpected Multicast message: " + received);
               }
           } catch(SocketTimeoutException e) {
               // nothing received, repeat
           } catch(IOException e) {
               e.printStackTrace();
               System.exit(1);
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
        if(sender == node.getPort()) {
            //TODO: handle differently
            return new Message(0, ""); // ignore messages sent by yourself
        }
        System.out.println(node.getPort() + " [Multicast UDP message received] >> " + receivedSplit[1]);
        return new Message(sender, receivedSplit[1]);
    }

    public void close() {
        try {
            mcSocket.leaveGroup(mcGroup);
            mcSocket.close();
        } catch (IOException e) {
            // failed to leave. ignore.
        }
    }
}

