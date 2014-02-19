import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.*;

public class PeerConnection extends UnicastRemoteObject implements Connection {

    public PeerConnection() throws Exception { }

    public static final int PORT_NUMBER = 3009 + 31 * 10;
    private Connection otherPlayer;
    private static Registry registry;
    private TicTacToe game;


    public void server() throws Exception {
        PeerConnection.registry = LocateRegistry.createRegistry(PORT_NUMBER);
        registry.rebind("PeerConnection", (Connection) this);
    }

    public void client() throws Exception {
        PeerConnection.registry = LocateRegistry.getRegistry("localhost", PORT_NUMBER);
        otherPlayer = (Connection)PeerConnection.registry.lookup("PeerConnection");
        otherPlayer.register((Connection) this);
        game = new TicTacToe(otherPlayer, Player.X);
    }

    public void register(Connection player) {
        otherPlayer = player;
        game = new TicTacToe(this.otherPlayer, Player.O);
    }

    public boolean registerTurn(int x, int y, Player player) throws Exception {
        return game.getBoardModel().setCell(x, y, player);
    }

    public void hasWon(Player player) throws Exception {
        game.gameOver();
        game.notifyWinner(player);
    }

    public void nextPlayer() throws Exception {
        game.changePlayer();
    }
}
