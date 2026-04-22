import java.io.Serializable; // needed for network transfer

public class Message implements Serializable { // sent over sockets
    static final long serialVersionUID = 42L; // required for serialization

    public enum MessageType { // all message types
        register,        // client register String[] user pass
        login,           // client login String[] user pass
        auth_ok,         // success Integer rating
        auth_fail,       // String reason
        join_queue,      // enter matchmaking
        list_games,      // request lobby game list
        games_list,      // java.util.ArrayList<GameListEntry>
        spectator_join,  // Integer sessionId
        spectator_ok,    // same payload shape as game start for spectator
        spectator_fail,  // String reason
        timer_sync,      // int[] {redSec,blackSec}
        rating_update,   // Integer newRating
        username,        // legacy unused
        username_ok,
        username_taken,
        waiting,
        game_start,
        move,
        invalid_move,
        game_over,
        chat,
        play_again,
        play_again_ack,
        draw_offer,
        draw_accept,
        draw_decline,
        resign
    }

    public MessageType type; // message type
    public Object data;      // payload (String, Move, Board, etc.)

    public Message(MessageType type, Object data) { // constructor
        this.type = type; // set type
        this.data = data; // set payload
    }
}