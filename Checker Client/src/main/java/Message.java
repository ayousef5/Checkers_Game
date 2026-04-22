import java.io.Serializable; // needed for network transfer

public class Message implements Serializable { // sent over sockets
    static final long serialVersionUID = 42L; // required for serialization

    public enum MessageType { // all message types
        register,
        login,
        auth_ok,
        auth_fail,
        join_queue,
        list_games,
        games_list,
        spectator_join,
        spectator_ok,
        spectator_fail,
        timer_sync,
        rating_update,
        username,
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
        resign,
        play_vs_bot,
        friend_add,
        friend_accept,
        friend_decline,
        friend_list_online,
        friend_pending_list,
        friend_incoming,
        friend_error,
        friend_notice,
        online_status
    }

    public MessageType type; // message type
    public Object data;      // payload (String, Move, Board, etc.)

    public Message(MessageType type, Object data) { // constructor
        this.type = type; // set type
        this.data = data; // set payload
    }
}