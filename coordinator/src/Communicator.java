import java.io.IOException;

public interface Communicator {
    // TODO interface: Abstraktionslevel Applikation – Kommunikation (connect, read, write, close) -> Austauschbarkeit von z.B. Sockets

    String read() throws IOException;
    void write(String message);
    void close();
}
