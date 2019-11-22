import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Node extends Thread {
    private InetAddress address;
    private int port;

    private TCPServer server;
    private Status status;
    private boolean listening = false;
    private Map<Integer, TCPSocketCommunicator> cluster;

    private List<Thread> serverThreads;

    private static int LOWER_PORT = 5050;
    private static int UPPER_PORT = 5100;

    public Node(int port) {
        System.out.println("Creating node on port " + port + "...");
        this.port = port;
        server = new TCPServer(port);
        serverThreads = new ArrayList<>();
    }


    public void searchCluster() {

        status = Status.SEARCHING;

        for(int port=LOWER_PORT; port <= UPPER_PORT; port++) {
            System.out.println("Port " + this.port + " checking: " + port);
            if(port != this.port) {
                TCPSocketCommunicator communicator;
                try {
                    communicator = establishCommunication(port);
                } catch(ConnectException e) {
                    continue;
                }

                communicator.write("Hello I am: " + this.port
                        + "; are you a coordinator?");

                String answer = waitForAnswer("STATUS", communicator);

                if(!answer.isEmpty()) {
                    System.out.println("Answer: " + answer);

                    if (answer.contains(Status.COORDINATOR.toString())) {
                        System.out.println("Found coordinator: " + port);
                        joinCluster(answer, port, communicator);
                        return;

                    } else if (answer.equals("I am searching.")) {
                        status = Status.WAITING;
                        try {
                            sleep(5000);
                        } catch (InterruptedException e) {
                            communicator.close();
                            System.exit(1);
                        }

                        searchCluster();
                        return;
                    }
                }

            }

        }

        // no coordinator found ...
        createCluster();

    }

    private TCPSocketCommunicator establishCommunication(int port) throws ConnectException {
        TCPClient client = new TCPClient();
        Socket clientSocket;
        try {
            clientSocket = client.connect(port);
        } catch (IOException e) {
            throw new ConnectException("Failed to connect to: " + port + e);
        }

        System.out.println("Connection established: " + clientSocket);

        return new TCPSocketCommunicator(this, clientSocket);

    }

    private void createCluster() {
        System.out.println("Port " + this.port + " creating new cluster...");

        status = Status.COORDINATOR;
        cluster = new HashMap<>();

        start();

    }

    private void communicateJoin(TCPSocketCommunicator communicator) {
        communicator.write("I (" + port + ") want to JOIN");
        String answer = waitForAnswer("ACCEPT", communicator);
    }

    private String waitForAnswer(String message, TCPSocketCommunicator communicator) {
        communicator.setBreakString(message);

        String answer = "";
        try {
            answer = communicator.continuouslyRead();
        } catch (IOException e) {
            System.out.println("Failed to read from established connection: " + e);
        }

        return answer;
    }


    private void joinCluster(String answer, int port, TCPSocketCommunicator communicator) {
        status = Status.NO_COORDINATOR;

        System.out.println("Port " + this.port + " joining cluster of: " + port);

        cluster = new HashMap<>();
        communicateJoin(communicator);
        cluster.put(port, communicator);

        // Yes I am!!1! This my gang: [1023, 1025, ...]
        Pattern p = Pattern.compile(Status.COORDINATOR.toString() + " This is my gang: \\[((\\d+(, )?)*)\\]");
        Matcher m = p.matcher(answer);


        if(m.find()) {
            System.out.println("Pattern matched!");
            String clusterList = m.group(1);
            System.out.println("Cluster List: " + clusterList);

            if(!clusterList.isEmpty()) {
                String[] members = clusterList.split(", ");
                System.out.println("Size of " + members.length);
                for(String memberPortString : members) {
                    Integer memberPort = Integer.valueOf(memberPortString);
                    cluster.put(memberPort, null);
                }

                for(Integer memberPort : cluster.keySet()) {
                    try {
                        TCPSocketCommunicator memberCommunicator = establishCommunication(port);
                        communicateJoin(memberCommunicator);
                        cluster.put(memberPort, memberCommunicator);
                    } catch(ConnectException e) {
                        System.out.println("Failed to connect to: " + port);
                        continue;
                    }

                }

            }

        }

        start();
    }

    public void run() {
       listen();
    }

    private void listen() {
        listening = true;

        while(listening) {
            Socket socket = server.connect();
            TCPSocketCommunicator communicator = new TCPSocketCommunicator(this, socket);
            communicator.setBreakString("exit");

            Thread t = new Thread(communicator, port + "-server");
            serverThreads.add(t);
            t.start();

        }
    }


    public String getAnswer(String message, TCPSocketCommunicator communicator) {
        Pattern p = Pattern.compile("I am: (\\d+); are you a coordinator\\?");
        Matcher m = p.matcher(message);
        if(m.find()) {

            if(status == Status.COORDINATOR) {
                return Status.COORDINATOR.toString() + " This is my gang: " + cluster.keySet().toString();
            } else if(status == Status.SEARCHING) {
                return Status.SEARCHING.toString();
            } else {
                return Status.NO_COORDINATOR.toString();
            }

        }

        Pattern joinPattern = Pattern.compile("I \\((\\d+)\\) want to JOIN");
        Matcher joinMatcher = joinPattern.matcher(message);
        if(joinMatcher.find()) {
            int joiningPort = Integer.valueOf(joinMatcher.group(1));
            cluster.put(joiningPort, communicator);
            return "ACCEPT";
        }

        return null;

    }

    public Set<Integer> getCluster() {
        return cluster.keySet();
    }

    public int getPort() {
        return port;
    }

    public void close() {
        System.out.println("Closing...");
        listening = false;
        server.close();
        for(int port : cluster.keySet()) {
            cluster.get(port).close();
        }
    }
}
