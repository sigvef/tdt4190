
public class Game {
    
    static PeerConnection peer;

    public static void main(String[]args) {

        try {
            peer = new PeerConnection();
            peer.server();
        } catch(Exception _) {
            try {
                peer.client();
            } catch(Exception __) {
                System.out.println("Could not start the game at this time.");
            }
        }
    }
}
