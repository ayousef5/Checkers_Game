import java.io.Serializable; // needed for network transfer

public class Message implements Serializable { // sent over sockets
    static final long serialVersionUID = 42L; // required for serialization

    public enum MessageType { // all message types
        username,        // client sends username
        username_ok,     // server accepts username
        username_taken,  // username already in use
        waiting,         // client is in queue
        game_start,      // game is starting
        move,            // a move was made
        invalid_move,    // move was illegal
        game_over,       // game has ended
        chat,            // chat message
        play_again,      // request to replay
        play_again_ack,  // replay acknowledged
        draw_offer,      // draw offered
        draw_accept,     // draw accepted
        draw_decline,    // draw declined
        resign           // player resigned
    }

    public MessageType type; // message type
    public Object data;      // payload (String, Move, Board, etc.)

    public Message(MessageType type, Object data) { // constructor
        this.type = type; // set type
        this.data = data; // set payload
    }
}