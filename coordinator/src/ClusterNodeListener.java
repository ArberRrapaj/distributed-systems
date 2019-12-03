import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;


public class ClusterNodeListener extends Thread {


    protected BufferedReader in;
    private ClusterNode clusterNode;
    private Socket socket;
    private volatile boolean running = true;
    private volatile boolean listening = true;

    public ClusterNodeListener(ClusterNode clusterNode, Socket socket, BufferedReader in) {
        this.clusterNode = clusterNode;
        this.socket = socket;
        System.out.println(socket);
        this.in = in;
    }

    public void close() {
        running = false;
        System.out.println("Seems like my true love, port " + socket.getPort() + " has just died. That means, me, the ClusterNodeListener will die now too, see you in hell.");

        try {
            if (in != null) in.close();
        } catch (IOException e) {
            // e.printStackTrace();
            System.out.println("Couldn't close input-stream ¯\\_(ツ)_/¯");
        }
    }

    public void run() {
        System.out.println("Me, a ClusterNodeListener will listen to (external) " + socket.getPort() + " on port (internal): " + socket.getLocalPort());
        while (running) {

            if (listening) {
                try {
                    // TODO: sort message into file
                    String nextLine = in.readLine();

                    if (nextLine != null) {
                        System.out.println(nextLine);
                        if (nextLine.equals(StandardMessages.SEND_FILE_HASH.toString())) {
                            clusterNode.write(StandardMessages.ANSWER_TIME.toString());
                            clusterNode.write("The file's hash is: " + clusterNode.headNode.getFilesHash());
                            clusterNode.handleHandshake("THANKS");
                            continue;
                        }
                        if (nextLine.equals(StandardMessages.ANSWER_TIME.toString())) continue;
                        if (nextLine.equals(StandardMessages.REQUEST_TIME.toString())) {
                            clusterNode.write(StandardMessages.ANSWER_TIME.toString());
                            clusterNode.write("The current time is: " + new Timestamp(new Date().getTime()));
                            clusterNode.handleHandshake("THANKS");
                            continue;
                        }
                        clusterNode.receivedMessageToWrite(nextLine);


                    }
                } catch (IOException ioe) {
                    System.out.println(ioe);
                    clusterNode.listenerDied();
                }
            }
        }

    }

    public void stopListening() {
        listening = false;
    }

    public void restartListening() {
        listening = true;
    }

}
