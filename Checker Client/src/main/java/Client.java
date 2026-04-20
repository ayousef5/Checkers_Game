import java.io.IOException; // needed for socket errors
import java.io.ObjectInputStream; // reads objects from socket
import java.io.ObjectOutputStream; // writes objects to socket
import java.net.Socket; // client socket
import java.util.function.Consumer; // callback to GUI

public class Client extends Thread { // runs on its own thread

	Socket socketClient; // connection to server
	ObjectOutputStream out; // sends messages to server
	ObjectInputStream in; // receives messages from server
	private Consumer<Message> callback; // sends received messages to GUI

	Client(Consumer<Message> call) { // constructor
		callback = call; // set callback
	}

	public void run() { // connection and read loop
		try {
			socketClient = new Socket("127.0.0.1", 5555); // connect to server
			out = new ObjectOutputStream(socketClient.getOutputStream()); // open output
			in = new ObjectInputStream(socketClient.getInputStream()); // open input
			socketClient.setTcpNoDelay(true); // disable Nagle's algorithm
		} catch (Exception e) {
			System.out.println("Could not connect to server"); // log error
		}

		while (true) { // keep reading messages
			try {
				Message msg = (Message) in.readObject(); // read message from server
				callback.accept(msg); // send to GUI
			} catch (Exception e) { // if connection drops
				System.out.println("Disconnected from server"); // log
				break; // exit loop
			}
		}
	}

	public void send(Message msg) { // send a message to server
		try {
			out.reset(); // clear cache so updated objects are re-serialized
			out.writeObject(msg); // write to stream
		} catch (IOException e) {
			System.out.println("Failed to send message"); // log error
		}
	}
}