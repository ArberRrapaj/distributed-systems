import java.io.*;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.Date;


public class TcpListener extends Thread {

    public boolean receivingFile = false;
    protected BufferedReader in;
    private Role role;
    private Node node;
    private int port;
    Socket socket;
    private volatile boolean listening = true;
    volatile boolean informationExchanged = false;

    public int FILE_SIZE;

    public TcpListener(Role role, Node node, Socket socket, int port) throws IOException {
        this.role = role;
        this.node = node;
        this.socket = socket;
        this.port = port;
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // System.out.println(socket);
    }

    public void close() {

        if(listening) {
            listening = false;
            System.out.println("Seems like my true love, port " + socket.getPort() + " has just died. That means, me, the TcpListener of " + node.getName() + " will die now too, see you in hell.");
            try {
                if (in != null) in.close(); // TODO: blocks
                socket.close();
            } catch (IOException e) {
                // e.printStackTrace();
                System.out.println("Couldn't close input-stream or socket ¯\\_(ツ)_/¯");
            }
        }
        System.out.println(node.name + ": TcpListener closed");
    }

    // TODO: call handleDeathOf as coordinator when fails
    public void run() {
        // informationExchanged = true;
        System.out.println("Me, a TcpListener will listen to (external) " + socket.getPort() + " on port (internal): " + socket.getLocalPort());

        /*
            Wait for initial node information, message to wait for depends on role
            TODO: not really necessary to do it like this, only need the duringInformationExchange flag, this is only for debugging(prints)
         */

        while (listening && !informationExchanged) {
            try {
                String nextLine = in.readLine();

                if (nextLine != null) {
                    System.out.println("TcpListener: " + node.getPort() + " : " + nextLine);
                    Message message = new Message(port, nextLine);
                    role.actionOnMessage(message, true);
                } else {
                    // listener died
                    System.out.println(node.name + ": TcpListener died during information exchange");
                    role.listenerDied(port);
                }
            } catch (IOException ioe) {
                System.out.println(node.name + ": TcpListener Exception during information exchange");
                role.listenerDied(port);
            }
        }

        if (receivingFile) {
            System.out.println(node.name + ": ReceivingFile is listening, filesize: " + FILE_SIZE);

            int bytesRead;
            int current = 0;

            try {

                DataInputStream dis = new DataInputStream(socket.getInputStream());
                FileOutputStream fos = new FileOutputStream(node.name + ".txt");
                byte[] buffer = new byte[4096];

                int read = 0;
                int totalRead = 0;
                int remaining = FILE_SIZE;
                while((read = dis.read(buffer, 0, Math.min(buffer.length, remaining))) > 0) {
                    totalRead += read;
                    remaining -= read;
                    System.out.println("read " + totalRead + " bytes.");
                    fos.write(buffer, 0, read);
                }

                fos.flush();
                fos.close();
                dis.close();


                /*
                byte [] mybytearray  = new byte [FILE_SIZE];

                fileInputStream = socket.getInputStream();
                fos = new FileOutputStream(node.name + ".txt");
                bos = new BufferedOutputStream(fos);

                bytesRead = fileInputStream.read(mybytearray, 0, mybytearray.length);
                current = bytesRead;

                do {
                    System.out.println(node.name + ": hi");
                    bytesRead = fileInputStream.read(mybytearray, current, (mybytearray.length-current));
                    if(bytesRead >= 0) current += bytesRead;
                } while(bytesRead > -1);

                bos.write(mybytearray, 0 , current);
                bos.flush();
                 */
                System.out.println("File downloaded (" + current + " bytes read)");
                node.initializeWriteIndex();
            } catch (IOException e) {
                // e.printStackTrace();
                System.out.println(node.name + ": TcpListener Exception during file base exchange");
                role.listenerDied(port);
            }

        }

        System.out.println(node.name + ": information was exchanged, I'll listen the right way now");
        node.messageQueue.sendQueuedMessages();

        while (listening) {
            try {
                String nextLine = in.readLine();
                System.out.println(node.name + ": nextLine from readLine() = " + nextLine);

                if (nextLine != null) {
                    System.out.println("TcpListener: " + node.getPort() + " : " + nextLine);
                    Message message = new Message(port, nextLine);
                    role.actionOnMessage(message, false);
                } else {
                    // listener died
                    System.out.println(node.name + ": TcpListener-RUN Exception");
                    role.listenerDied(port);
                }
            } catch (IOException ioe) {
                System.out.println(node.name + ": TcpListener-RUN Exception");
                role.listenerDied(port);
            }
        }

    }

    public void stopListening() {
        listening = false;
    }

    public void restartListening() {
        listening = true;
    }

    public void setPort(int port) { this.port = port; }

}
