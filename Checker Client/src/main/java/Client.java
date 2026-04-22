import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class Client extends Thread {

    Socket socketClient;
    ObjectOutputStream out;
    ObjectInputStream in;
    private Consumer<Message> callback;

    // gui callback
    Client(Consumer<Message> call) {
        callback = call;
    }

    // read loop
    public void run() {
        try {
            socketClient = new Socket("127.0.0.1", 5555);
            out = new ObjectOutputStream(socketClient.getOutputStream());
            in = new ObjectInputStream(socketClient.getInputStream());
            socketClient.setTcpNoDelay(true);
        } catch (Exception e) {
            System.out.println("Could not connect to server");
        }

        if (in == null) {
            return;
        }

        while (true) {
            try {
                Message msg = (Message) in.readObject();
                callback.accept(msg);
            } catch (Exception e) {
                System.out.println("Disconnected from server");
                break;
            }
        }
    }

    // send to server
    public void send(Message msg) {
        if (out == null) {
            return;
        }
        try {
            out.reset();
            out.writeObject(msg);
        } catch (IOException e) {
            System.out.println("Failed to send message");
        }
    }
}
