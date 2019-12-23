import java.io.IOException;
import java.net.*;

public class Multicaster extends Thread {

    private Node node;

    private boolean running;
    private int port;
    private InetAddress mcGroup;
    private MulticastSocket mcSocket;

    public Multicaster(Node node, String ip, int port) throws ConnectException {
        this.port = port;

        // join Multicast group
        try {
            mcGroup = InetAddress.getByName(ip);
            mcSocket = new MulticastSocket(0); // dynmamically allocate a free port anywhere
            mcSocket.joinGroup(mcGroup);
        } catch(UnknownHostException e) {
            e.printStackTrace();
            System.exit(1);
        } catch(IOException e) {
            throw new ConnectException("Socket in use or failed to join Multicast group.");
        }
    }

    public void run() {
        running = true;
        listen();
    }

    private void listen() {
       while(true) {
           try {
               Message received = receive();
               node.actionOnMessage(received);
           } catch(IOException e) {
               e.printStackTrace();
               break;
           }
       }
    }

    public void send(String message) throws IOException {
        DatagramPacket packet = new DatagramPacket(message.getBytes(),
                message.length(), mcGroup, port);
        mcSocket.send(packet);
    }

    public Message receive() throws IOException {
        byte[] buf = new byte[1000];
        DatagramPacket recv = new DatagramPacket(buf, buf.length);
        mcSocket.receive(recv);

        String received = new String(recv.getData(), 0, recv.getLength());

        return new Message(recv.getPort(), received);
    }
}
