public enum Player {
    O, X;

    public char toChar() {
        return this.toString().toCharArray()[0];
    }

    public boolean isStartingPlayer() {
        return this == Player.O;
    }
}
