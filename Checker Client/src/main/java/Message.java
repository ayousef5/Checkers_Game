import java.io.Serializable;

public class Message implements Serializable {
    static final long serialVersionUID = 42L;

    public enum MessageType {
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

    public MessageType type;
    public Object data;

    // wire message
    public Message(MessageType type, Object data) {
        this.type = type;
        this.data = data;
    }
}
