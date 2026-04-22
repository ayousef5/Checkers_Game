import java.io.ObjectInputStream; // reads objects from socket
import java.io.ObjectOutputStream; // writes objects to socket
import java.io.Serializable; // needed for callback
import java.net.ServerSocket; // listens for connections
import java.net.Socket; // individual client connection
import java.util.ArrayList; // needed for lists
import java.util.function.Consumer; // callback to GUI

public class Server { // main server class

	int count = 1; // tracks client count
	ArrayList<ClientThread> clients = new ArrayList<>(); // all connected clients
	ArrayList<ClientThread> waitingQueue = new ArrayList<>(); // players waiting for a match
	ArrayList<GameSession> activeSessions = new ArrayList<>(); // ongoing games
	TheServer server; // the listening thread
	private Consumer<Serializable> callback; // sends log messages to GUI

	Server(Consumer<Serializable> call) { // constructor
		callback = call; // set callback
		server = new TheServer(); // create server thread
		server.start(); // start listening
	}

	public void matchPlayers() { // pair two waiting players
		if (waitingQueue.size() >= 2) { // need at least 2 players
			ClientThread player1 = waitingQueue.remove(0); // first waiting player (red)
			ClientThread player2 = waitingQueue.remove(0); // second waiting player (black)
			GameSession session = new GameSession(player1, player2, count++); // create session
			activeSessions.add(session); // track session
			player1.currentGame = session; // assign session to player1
			player2.currentGame = session; // assign session to player2
			session.startGame(); // notify both players
			callback.accept("Game started between " + player1.username + " and " + player2.username); // log
		}
	}

	public void removeClient(ClientThread client) { // remove a disconnected client
		clients.remove(client); // remove from client list
		waitingQueue.remove(client); // remove from queue if waiting
		if (client.currentGame != null) { // if in a game
			client.currentGame.endGame(); // end the game
			activeSessions.remove(client.currentGame); // remove session
		}
		callback.accept(client.username + " disconnected"); // log
	}

	public class TheServer extends Thread { // listens for new connections

		public void run() { // server loop
			try (ServerSocket mysocket = new ServerSocket(5555)) { // open port 5555
				System.out.println("Server is waiting for a client!"); // log
				while (true) { // keep accepting clients
					ClientThread c = new ClientThread(mysocket.accept(), count); // accept connection
					callback.accept("Client #" + count + " connected"); // log
					clients.add(c); // track client
					c.start(); // start client thread
					count++; // increment count
				}
			} catch (Exception e) { // if server fails
				callback.accept("Server socket did not launch"); // log error
			}
		}
	}

	class ClientThread extends Thread { // handles one client connection

		Socket connection; // client socket
		int count; // client number
		ObjectInputStream in; // reads from client
		ObjectOutputStream out; // writes to client
		String username; // player's username
		GameSession currentGame; // the game this client is in

		ClientThread(Socket s, int count) { // constructor
			this.connection = s; // set socket
			this.count = count; // set count
		}

		public void sendMessage(Message msg) { // send a message to this client
			try {
				out.reset(); // clear cache so updated objects are re-serialized
				out.writeObject(msg); // write message to stream
			} catch (Exception e) { // if send fails
				callback.accept("Failed to send message to " + username); // log error
			}
		}

		public void run() { // client loop
			try {
				out = new ObjectOutputStream(connection.getOutputStream()); // open output stream
				in = new ObjectInputStream(connection.getInputStream()); // open input stream
				connection.setTcpNoDelay(true); // disable Nagle's algorithm
			} catch (Exception e) {
				System.out.println("Streams not open"); // log error
			}

			while (true) { // keep reading messages
				try {
					Message msg = (Message) in.readObject(); // read incoming message
					handleMessage(msg); // process the message
				} catch (Exception e) { // if client disconnects
					removeClient(this); // clean up
					break; // exit loop
				}
			}
		}

		private void handleMessage(Message msg) { // process a message from client
			switch (msg.type) { // check message type

				case username: // client is sending their username
					String name = (String) msg.data; // get username
					for (ClientThread c : clients) { // check for duplicates
						if (c != this && name.equals(c.username)) { // already taken
							sendMessage(new Message(Message.MessageType.username_taken, null)); // reject
							return;
						}
					}
					this.username = name; // save username
					sendMessage(new Message(Message.MessageType.username_ok, null)); // confirm
					waitingQueue.add(this); // add to queue
					sendMessage(new Message(Message.MessageType.waiting, null)); // tell client to wait
					callback.accept(username + " joined the queue"); // log
					matchPlayers(); // try to start a game
					break;

				case move: // client is making a move
					if (currentGame != null) { // if in a game
						currentGame.handleMove((Move) msg.data, this); // process move
					}
					break;

				case chat: // client sent a chat message
					if (currentGame != null) { // if in a game
						currentGame.broadcastToGame(new Message(Message.MessageType.chat, username + ": " +
								msg.data)); // forward chat
					}
					break;

				case resign: // client is resigning
					if (currentGame != null) { // if in a game
						String opponent = currentGame.player1 == this ? currentGame.player2.username :
								currentGame.player1.username; // get opponent
						currentGame.broadcastToGame(new Message(Message.MessageType.game_over, opponent)); // opponent wins
						currentGame.endGame(); // end session
						activeSessions.remove(currentGame); // remove session
					}
					break;

				case draw_offer: // client is offering a draw
					if (currentGame != null) { // if in a game
						ClientThread opponent = currentGame.player1 == this ? currentGame.player2 :
								currentGame.player1; // get opponent
						opponent.sendMessage(new Message(Message.MessageType.draw_offer, username)); // forward offer
					}
					break;

				case draw_accept: // client accepted a draw
					if (currentGame != null) { // if in a game
						currentGame.broadcastToGame(new Message(Message.MessageType.game_over, "draw")); // game is a draw
						currentGame.endGame(); // end session
						activeSessions.remove(currentGame); // remove session
					}
					break;

				case draw_decline: // client declined a draw
					if (currentGame != null) { // if in a game
						ClientThread opp = currentGame.player1 == this ? currentGame.player2 :
								currentGame.player1; // get opponent
						opp.sendMessage(new Message(Message.MessageType.draw_decline, null)); // notify opponent
					}
					break;

				case play_again: // client wants to play again
					waitingQueue.add(this); // re-add to waiting queue
					sendMessage(new Message(Message.MessageType.waiting, null)); // tell client to wait
					matchPlayers(); // try to find a match
					break;

				case play_again_ack: // client acknowledged play again
					waitingQueue.add(this); // re-add to queue
					sendMessage(new Message(Message.MessageType.waiting, null)); // tell client to wait
					matchPlayers(); // try to match again
					break;

				default: // unknown message type
					callback.accept("Unknown message from " + username); // log
					break;
			}
		}
	}
}