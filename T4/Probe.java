import java.rmi.*;
import java.util.*;

class Probe extends Thread {
    private ArrayList<Integer> visited;
    private ServerImpl server;
    private int id;

    public Probe(ServerImpl server, int id) {
        visited = new ArrayList<Integer>();
        this.server = server;
        this.id = id;
    }

    @Override
    public void run() {
        try {
            server.recieveProbe(visited, id);
        } catch(Exception e) { e.printStackTrace(); }
    }
}
