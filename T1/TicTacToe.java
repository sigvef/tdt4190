import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;


public class TicTacToe extends JFrame implements ListSelectionListener {

	private static final int BOARD_SIZE = 15;
	private final BoardModel boardModel;
	private final JTable board;
	private final JLabel statusLabel = new JLabel();
    private final Player player;
	private Connection connection;
	private boolean myTurn;
	private boolean isDone = false;

	public TicTacToe(Connection connection, Player player) {
        this.player = player;
		this.connection = connection;
        setTurn(player.isStartingPlayer());
		boardModel = new BoardModel(BOARD_SIZE);
		board = new JTable(boardModel);
		setup();
	}

	private void setup() {
		board.setFont(board.getFont().deriveFont(25.0f));
		board.setRowHeight(30);
		board.setCellSelectionEnabled(true);
		for (int i = 0; i < board.getColumnCount(); i++)
			board.getColumnModel().getColumn(i).setPreferredWidth(30);
		board.setGridColor(Color.BLACK);
		board.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		DefaultTableCellRenderer dtcl = new DefaultTableCellRenderer();
		dtcl.setHorizontalAlignment(SwingConstants.CENTER);
		board.setDefaultRenderer(Object.class, dtcl);
		board.getSelectionModel().addListSelectionListener(this);
		board.getColumnModel().getSelectionModel()
				.addListSelectionListener(this);

		statusLabel.setPreferredSize(new Dimension(statusLabel
				.getPreferredSize().width, 40));
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());
		contentPane.add(board, BorderLayout.CENTER);
		contentPane.add(statusLabel, BorderLayout.SOUTH);
		pack();

		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

		int centerX = (int) (Toolkit.getDefaultToolkit().getScreenSize()
				.getWidth() - getSize().width) / 2;
		int centerY = (int) (Toolkit.getDefaultToolkit().getScreenSize()
				.getHeight() - getSize().height) / 2;
		setLocation(centerX, centerY);
		setVisible(true);
	}

    public void notifyWinner(Player player) {
        statusLabel.setText("Player " + player + " wins!");
    }

	public void valueChanged(ListSelectionEvent e) {
		if (!myTurn || isDone || e.getValueIsAdjusting()) {
			return;
        }
		int x = board.getSelectedColumn();
		int y = board.getSelectedRow();
		if (x == -1 || y == -1 || !boardModel.isEmpty(x, y))
			return;

		try {
			if (boardModel.setCell(x, y, player) &&
                    connection.registerTurn(x, y, player)) {
                notifyWinner(player);
                connection.hasWon(player);
                gameOver();
                return;
			}
			connection.registerTurn(x, y, player);
			connection.nextPlayer();
			changePlayer();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	public void changePlayer() {
        setTurn(!myTurn);
	}

    public void setTurn(boolean turn) {
        myTurn = turn;
		statusLabel.setText("It is your " + (myTurn ? "" : "opponent's")
				+ " turn.");
    }

	public BoardModel getBoardModel() {
		return boardModel;
	}

	public void gameOver() {
		isDone = true;
	}
}
