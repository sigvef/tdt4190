import java.net.MalformedURLException;
import java.rmi.*;
import java.rmi.server.*;
import java.util.*;
import java.io.*;

/**
 * Implementation of a server in a distributed transaction system.
 * <p/>
 * The transactions to be performed can be chosen randomly or they can
 * be specified by an input file. The operations of the system are logged
 * to an output file. It's important to keep this log detailed, as it will
 * be used as "proof" of the system's performance (whether or not it is able
 * to detect and resolve deadlocks).
 * <p/>
 * The input file is specified as an optional second command line parameter
 * (the first parameter specifies the registry address). See example_input_file
 * for an example of what such an input file may look like. If no input file is
 * provided, transactions are performed randomly.
 * <p/>
 * The output file is written to whenever the print() or println() methods of
 * this class are called. These methods can be used to provide feedback to the
 * user. Information sent to these methods is displayed in the GUI and written
 * to the log. The name of the output (log) file is specified in the Globals class,
 * but may be changed by the input file.
 * <p/>
 * The log file is opened in the append mode, so if the log file already exists,
 * new log entries are added to the end of the log.
 */
public class ServerImpl extends UnicastRemoteObject implements Server
{
  /**
   * The unique ID of this server
   */
  private int serverId;
  /**
   * A hash table of all known servers, hashed on their ID
   */
  private final Map<Integer, Server> servers;
  /**
   * A list of all the local resources on this server
   */
  private final List<Resource> resources;
  /**
   * The number of transactions performed so far by this server
   */
  private int transactionCounter;
  /**
   * The number of aborted transactions so far on this server
   */
  private int nofAborts;
  /**
   * The transaction that is currently being executed by this server
   */
  private Transaction activeTransaction;
  /**
   * A reference to the GUI
   */
  private final Gui gui;
  /**
   * A registry proxy object used to bind non-local objects to the registry
   */
  private RegistryProxy registryProxy;
  /**
   * The writer writing to the output file
   */
  private PrintWriter output;
  /**
   * The reader reading input from the input file
   */
  private TransactionFileReader input;
  /**
   * The name of the test case being executed
   */
  private String testCase;
  /**
   * The time of startup
   */
  private long startupTime;

  /**
   * Creates a new server.
   *
   * @param ip The address of the RMI registry to use.
   */
  public ServerImpl(String ip, String inputfile) throws RemoteException
  {
    servers = new HashMap<Integer, Server>();
    transactionCounter = 0;
    nofAborts = 0;
    activeTransaction = null;
    readGlobalParameters(inputfile);
    resources = new ArrayList<Resource>();
    for (int i = 0; i < Globals.NOF_RESOURCES_PER_SERVER; i++)
      resources.add(new Resource());

    try {
      // Find the registry proxy object used to bind non-local objects to the registry:
      registryProxy = (RegistryProxy)Naming.lookup("rmi://" + ip + "/RegistryProxy");
    } catch (NotBoundException nbe) {
      System.out.println("Couldn't find RegistryProxy object in registry. Be sure to use the StartRegistry class to start up your registry.");
      System.exit(1);
    } catch (MalformedURLException mue) {
      System.err.println("Error: Malformed URL; " + mue.getMessage());
      System.exit(1);
    } catch (ConnectException ce) {
      System.err.println("Couldn't connect to the RMI registry. Make sure that a RMI registry is running on the address you specified.");
      System.exit(1);
    }
    // Meet the other servers
    println("Connecting to rmi registry at " + ip);
    // Get the list of all servers registered at the registry
    String[] serverNames = registryProxy.list();
    int maxId = 0;
    // Look them up
    for (String serverName : serverNames) {
      maxId = Math.max(maxId, lookUpServer(serverName));
    }
    // Bind as the next available id
    bindToRegistry(maxId + 1);
    // Notify other servers of our arrival
    for (Server server : servers.values())
      server.newServerConnected(this);
    // Add ourselves to our server list
    servers.put(serverId, this);
    // Open the output file using the append mode
    try {
      startupTime = System.currentTimeMillis();
      output = new PrintWriter(new FileWriter(Globals.OUTPUT_FILE_PREFIX + "_server_" + serverId + ".txt", true));
      output.println("==================================================================");
    } catch (IOException ioe) {
    }
    if (testCase != null)
      println("Test case " + testCase + " started.");
    // Start up the GUI
    gui = new Gui("Server " + serverId, resources, servers, this, (serverId - 1) % 4);
    println("Server " + serverId + " started.");
    if (input != null)
      runTransactions(input);
    else
      runTransactions();
  }

  /**
   * Read the global parameters specified in the beginning
   * of the input file.
   *
   * @param inputfile The name of the inputfile, or null if no input file is used.
   */
  private void readGlobalParameters(String inputfile)
  {
    if (inputfile != null) {
      input = new TransactionFileReader(inputfile);
      String line = input.readLine();
      if (line == null || !line.startsWith("TEST CASE: ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      testCase = line.substring(11);
      line = input.readLine();
      if (line == null || !line.startsWith("OUTPUT_FILE_PREFIX: ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      Globals.OUTPUT_FILE_PREFIX = line.substring(20);
      line = input.readLine();
      if (line == null || !line.startsWith("NOF_RESOURCES_PER_SERVER: ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      Globals.NOF_RESOURCES_PER_SERVER = Integer.parseInt(line.substring(26));
      line = input.readLine();
      if (line == null || !line.startsWith("TIMEOUT_INTERVAL: ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      Globals.TIMEOUT_INTERVAL = Long.parseLong(line.substring(18));
      line = input.readLine();
      if (line == null || !line.startsWith("PROBING_ENABLED: ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      Globals.PROBING_ENABLED = line.substring(17).equals("true");
    }
  }

  /**
   * Outputs a text string to the user and logs it.
   *
   * @param s The text to display.
   */
  private void print(String s)
  {
    if (gui != null && gui.isShowing())
      gui.print(s);
    else
      System.out.print(s);
    if (output != null) {
      long time = System.currentTimeMillis() - startupTime;
      output.print(time + ": " + s);
      output.flush();
    }
  }

  /**
   * Outputs a text string to the user, adding a newline at the end, and logs it.
   *
   * @param s The text to display.
   */
  private void println(String s)
  {
    if (gui != null && gui.isShowing())
      gui.println(s);
    else
      System.out.println(s);
    if (output != null) {
      long time = System.currentTimeMillis() - startupTime;
      output.println(time + ": " + s);
      output.flush();
    }
  }

  /**
   * Outputs a text string relevant to a specific transaction to the user, and logs it.
   *
   * @param s   The information to display.
   * @param tId The ID of the transaction relevant to this information.
   */
  private void print(String s, int tId)
  {
    if (gui != null && gui.isShowing())
      gui.print(s, tId);
    else
      System.out.print(s);
    if (output != null) {
      long time = System.currentTimeMillis() - startupTime;
      output.print(time + ": TRANS " + tId + ": " + s);
      output.flush();
    }
  }

  /**
   * Outputs a text string relevant to a specific transaction to the user, formated
   * with a newline at the end, and logs it.
   *
   * @param s   The information to display.
   * @param tId The ID of the transaction relevant to this information.
   */
  void println(String s, int tId)
  {
    if (gui != null && gui.isShowing())
      gui.println(s, tId);
    else
      System.out.println(s);
    if (output != null) {
      long time = System.currentTimeMillis() - startupTime;
      output.println(time + ": TRANS " + tId + ": " + s);
      output.flush();
    }
  }

  /**
   * Tries to look up a server specified by the given name.
   *
   * @param name The name specifying the object to look up in the registry.
   * @return The ID of the server that was looked up, or -1 if the server could not be found.
   */
  private int lookUpServer(String name)
  {
    print("Looking up server " + name + "...");
    try {
      Server s = (Server)registryProxy.lookup(name);
      int id = s.getServerId();
      println("Server found.");
      servers.put(id, s);
      return id;
    } catch (NotBoundException nbe) {
      println("Server recently disconnected.");
      return -1;
    } catch (RemoteException re) {
      println("Server unavailable. Server removed from registry.");
      try {
        registryProxy.unbind(name);
      } catch (Exception e) {
      }
      return -1;
    }
  }

  /**
   * Assigns a unique ID to this server and binds the server to the registry.
   *
   * @param asId The ID to attempt to claim. If other servers are trying to bind to the registry
   *             at the same time, a larger ID may be used.
   */
  private void bindToRegistry(int asId) throws RemoteException
  {
    serverId = asId;
    boolean bound = false;
    while (!bound) {
      print("Attempting to bind to registry as server" + serverId + "...");
      try {
        registryProxy.bind("server" + serverId, this);
        bound = true;
      } catch (AlreadyBoundException abe) {
        println("ID recently taken.");
        lookUpServer("server" + serverId);
        serverId++;
      }
    }
    println("Succeeded.");
  }

  /**
   * Get the unique ID of this server.
   *
   * @return The ID of this server.
   */
  public int getServerId() throws RemoteException
  {
    return serverId;
  }

  /**
   * Called to notify this server that a new server has connected to the system.
   * Adds the new server to this server's hash table of known servers.
   *
   * @param s The server that arrived.
   */
  public synchronized void newServerConnected(Server s) throws RemoteException
  {
    int id = s.getServerId();
    println("Server " + serverId + " was notified of the arrival of server " + id);
    servers.put(id, s);
    if (gui != null)
      gui.updateServerList(servers);
  }

  /**
   * Called to notify this server that a server has disconnected from the system.
   * Removes the disconnected server from this server's hash table of known servers.
   *
   * @param disconnectedId The ID of the server that left.
   * @param messengerId    The ID of the server that first discovered that that server had left.
   */
  public void serverDisconnected(int disconnectedId, int messengerId) throws RemoteException
  {
    println("Server " + serverId + " was notified of the departure of server " + disconnectedId + " by server " + messengerId + '.');
    serverDisconnected(disconnectedId);
  }

  /**
   * Removes the specified server from this server's hash table of known servers,
   * and unlocks any local resources locked by transactions from that server.
   *
   * @param disconnectedId The ID of the server that has disconnected.
   */
  private synchronized void serverDisconnected(int disconnectedId)
  {
    servers.remove(disconnectedId);
    gui.updateServerList(servers);
    // Check if the active transaction needs to abort
    if (activeTransaction != null)
      activeTransaction.serverDisconnected(disconnectedId);
    // Unlock any resources locked by transactions from the server that disconnected
    for (Object resource : resources) {
      Resource r = (Resource)resource;
      if (r.isLockedByServer(disconnectedId))
        r.forceUnlock();
    }
  }

  /**
   * Gives the lock of the specified local resource to the specified transaction.
   * This method blocks until the lock has been acquired.
   *
   * @param lockerId   The ID of the transaction that wants the lock.
   * @param resourceID The ID of the resource whose lock the transaction wants.
   * @return Whether or not the lock was acquired.
   */
  public boolean lockResource(int transactionId, int resourceId) throws RemoteException
  {
    Resource r = resources.get(resourceId);
    boolean result = r.lock(transactionId);
    if (gui != null)
      gui.updateResourceTable(resources);
    return result;
  }

  /**
   * Release the lock of the specified local resource, which is currently locked by the specified transaction.
   *
   * @param lockerId   The ID of the transaction that owns the lock and wants to release it.
   * @param resourceID The ID of the resource whose lock the transaction wants to release.
   * @return Whether or not the lock could be released.
   */
  public boolean releaseLock(int transactionId, int resourceId) throws RemoteException
  {
    Resource r = resources.get(resourceId);
    boolean result = r.unlock(transactionId);
    if (gui != null)
      gui.updateResourceTable(resources);
    return result;
  }

  /**
   * Called by another server to ask this server to start its transactions.
   */
  public void startTransactions() throws RemoteException
  {
    if (gui != null) {
      println("Server " + serverId + " started its transactions.");
      gui.pressStartButton();
    }
  }

  /**
   * Called whenever this server loses contact with another server.
   * Notifies all servers that the given server is down.
   *
   * @param id The ID of the server with which we lost contact.
   */
  void lostContactWithServer(int id)
  {
    println("Contact with server " + id + " was lost.");
    // Notify all other known servers that the server with the given id is down.
    for (Integer key : servers.keySet()) {
      if (key != serverId) {
        try {
          servers.get(key).serverDisconnected(id, serverId);
        } catch (RemoteException re) {
        }
      }
    }
    // Then "notify" ourselves
    serverDisconnected(id);
    // Remove the server from the registry if possible
    try {
      registryProxy.unbind("server" + id);
    } catch (Exception ex) {
    }
  }

  /**
   * Gets the ID of the server running the specified transaction.
   * The server ID can be deduced by the transaction ID.
   *
   * @param transactionId The ID of the transaction.
   * @return The ID of the server running the transaction.
   */
  static int getTransactionOwner(int transactionId)
  {
    return transactionId / Globals.MAX_NOF_TRANSACTIONS_PER_SERVER;
  }

  /**
   * Called by the GUI when the Start button was pressed.
   * Ask the other known servers to start as well.
   */
  void start()
  {
    for (Server s : servers.values()) {
      try {
        s.startTransactions();
      } catch (RemoteException re) {
      }
    }
  }

  /**
   * Loop executing transactions on this server. Random transactions
   * are executed one at a time, until the total number of transactions executed reaches
   * the maximum limit.
   */
  private void runTransactions()
  {
    while (transactionCounter < Globals.MAX_NOF_TRANSACTIONS_PER_SERVER) {
      if (!gui.getPauseStatus()) {
        int newId = serverId * Globals.MAX_NOF_TRANSACTIONS_PER_SERVER + transactionCounter;
        gui.newTransactionTab(newId);
        println("Transaction " + newId + " arrived.");
        activeTransaction = new Transaction(newId, this, null);
        if (!activeTransaction.runTransaction())
          nofAborts++;
        activeTransaction = null;
        transactionCounter++;
        println("Transaction " + newId + " ended.");
        gui.updateTransactionCounters(nofAborts, transactionCounter);
        Globals.randomSleep(Globals.MIN_ARRIVAL_WAIT, Globals.MAX_ARRIVAL_WAIT);
      }
      else {
        // Wait a little while to prevent hogging the CPU
        Globals.randomSleep(100, 100);
      }
    }
    println("Max # of transactions reached, no more transactions will be started.");
  }

  /**
   * Loop executing transactions on this server. The transactions are described by the input
   * file.
   */
  private void runTransactions(TransactionFileReader input)
  {
    String line = input.readLine();
    while (line != null && transactionCounter < Globals.MAX_NOF_TRANSACTIONS_PER_SERVER) {
      if (!line.equals("START TRANSACTION")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      if (!gui.getPauseStatus()) {
        int newId = serverId * Globals.MAX_NOF_TRANSACTIONS_PER_SERVER + transactionCounter;
        gui.newTransactionTab(newId);
        println("Transaction " + newId + " arrived.");
        activeTransaction = new Transaction(newId, this, input);
        if (!activeTransaction.runTransaction())
          nofAborts++;
        activeTransaction = null;
        transactionCounter++;
        println("Transaction " + newId + " ended.");
        gui.updateTransactionCounters(nofAborts, transactionCounter);
        // Read a line describing the waiting time until the next transaction arrives.
        // The line should be on the format WAIT 50-200 (signifying a wait between 50 and 200 ms).
        line = input.readLine();
        if (line == null || !line.startsWith("WAIT ")) {
          System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
          System.exit(1);
        }
        StringTokenizer st = new StringTokenizer(line.substring(5), "-");
        long min = Long.parseLong(st.nextToken());
        if (!st.hasMoreTokens()) {
          System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
          System.exit(1);
        }
        long max = Long.parseLong(st.nextToken());
        Globals.randomSleep(min, max);
        // Read the next input line
        line = input.readLine();
        if (line.equals("END OF FILE"))
          line = null;
      }
      else {
        // Wait a little while to prevent hogging the CPU
        Globals.randomSleep(100, 100);
      }
    }
    println("End of input file reached, no more transactions will be started.");
    input.close();
  }

  /**
   * Picks a random resource (from the entire distributed system) that is not already in
   * the given list of resources.
   *
   * @param resourceAccesses A list of resources not to pick.
   * @return A random resource not already present in the resourceAccesses list.
   */
  synchronized ResourceAccess getRandomResource(List<ResourceAccess> resourceAccesses)
  {
    ResourceAccess result;
    Integer keys[] = new Integer[servers.size()];
    keys = servers.keySet().toArray(keys);
    do {
      int randomPos = Globals.random(0, servers.size() - 1);
      int resourceId = Globals.random(0, Globals.NOF_RESOURCES_PER_SERVER - 1);
      result = new ResourceAccess(servers.get(keys[randomPos]), keys[randomPos], resourceId);
    } while (resourceAccesses.contains(result));
    return result;
  }

  /**
   * Returns the Server reference to the server with the given id.
   */
  synchronized Server getServer(int id)
  {
    return servers.get(id);
  }

  /**
   * Starts up a new server.
   *
   * @param args Command line parameters, the first parameter specifies the address of the RMI registry,
   *             and the second specifies the name of the input file.
   *             If no parameters are supplied, the default address of "localhost:1111" is used, and
   *             transactions are performed randomly.
   */
  public static void main(String[] args)
  {
    String registryAddress = "localhost:1111";
    String inputfile = null;
    if (args.length > 0)
      registryAddress = args[0];
    if (args.length > 1)
      inputfile = args[1];
    try {
      new ServerImpl(registryAddress, inputfile);
    } catch (RemoteException re) {
      re.printStackTrace();
    }
  }
}
