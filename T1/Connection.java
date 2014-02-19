import java.rmi.Remote;

public interface Connection extends Remote {
	public void register(Connection player) throws Exception;
	public boolean registerTurn(int x, int y, Player player) throws Exception;
	public void hasWon(Player player) throws Exception;
	public void nextPlayer() throws Exception;
}
