import java.rmi.*;
import java.util.*;

/**
 * A transaction in the distributed transaction system.
 * A transaction consists of a series of accesses to resources in the distributed
 * system. Two-phase locking is used. Which and how many resources to access is determined
 * randomly or by the data in the input file, if such a file is specified.
 */
class Transaction
{
  /**
   * The ID of this transaction
   */
  private final int transactionId;
  /**
   * The server executing this transaction
   */
  private final ServerImpl owner;
  /**
   * The resources currently locked by this transaction
   */
  private final List<ResourceAccess> lockedResources;
  /**
   * Signalling variable used to exchange messages between different threads operating on this transaction
   */
  private boolean abortTransaction;
  /**
   * The resource whose lock this transaction is currently waiting for
   */
  private ResourceAccess waitingForResource;
  /**
   * The transaction file reader whose input specifies the contents of this transaction
   */
  private final TransactionFileReader input;

  /**
   * Creates a new transaction object.
   *
   * @param transactionId The ID of the transaction.
   * @param owner         The server executing this transaction.
   * @param input         The input file reader specifying the contents of this transaction.
   *                      If this parameter is null, the transaction is performed randomly.
   */
  Transaction(int transactionId, ServerImpl owner, TransactionFileReader input)
  {
    this.transactionId = transactionId;
    this.owner = owner;
    this.input = input;
    waitingForResource = null;
    lockedResources = new ArrayList<ResourceAccess>();
  }

  /**
   * Executes this transaction. This method blocks the calling thread
   * until the transaction has completed, which may take a while.
   *
   * @return Whether or not the transaction was able to commit.
   */
  boolean runTransaction()
  {
    abortTransaction = false;
    lockedResources.clear();
    owner.println("Starting transaction " + transactionId + '.', transactionId);

    // Figure out how many resource accesses the transaction consists of.
    int nofAccesses = Globals.random(Globals.MIN_NOF_ACCESSES_PER_TRANSACTION, Globals.MAX_NOF_ACCESSES_PER_TRANSACTION);
    if (input != null) {
      // Read the number of accesses from the input file instead.
      // Expected format: NUMBER OF ACCESSES: 7
      String line = input.readLine();
      if (line == null || !line.startsWith("NUMBER OF ACCESSES: ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      nofAccesses = Integer.parseInt(line.substring(20));
    }

    // Perform the accesses
    for (int i = 0; i < nofAccesses; i++) {
      ResourceAccess nextResource = getNextResource();
      abortTransaction = !acquireLock(nextResource);
      if (abortTransaction) {
        // Transaction should abort due to a communication failure
        abort();
        return false;
      }
      else {
        owner.println("Lock claimed. Processing...", transactionId);
        processResource();
      }
    }
    commit();
    return true;
  }

  /**
   * Processes a resource that the transaction has acquired the lock to.
   * No actual processing is done, but some milliseconds are spent sleeping
   * to simulate the processing.
   */
  private void processResource()
  {
    // Figure out how long to "process" the resource:
    if (input == null)
      Globals.randomSleep(Globals.MIN_PROCESSING_TIME, Globals.MAX_PROCESSING_TIME);
    else {
      // Read the processing time from the input file.
      // The line should be on the format PROCESS 50-200 (signifying a processing time between 50 and 200 ms).
      String line = input.readLine();
      if (line == null || !line.startsWith("PROCESS ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      StringTokenizer st = new StringTokenizer(line.substring(8), "-");
      long min = Long.parseLong(st.nextToken());
      if (!st.hasMoreTokens()) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      long max = Long.parseLong(st.nextToken());
      Globals.randomSleep(min, max);
    }
  }

  /**
   * Figure out what resource the transaction should access next.
   *
   * @return The next resource that the transaction should try to access.
   */
  private ResourceAccess getNextResource()
  {
    if (input == null)
      return owner.getRandomResource(lockedResources);
    else {
      // Read the next resource to be accessed from the input file
      // Expected format: ACCESS SERVER 1 RESOURCE 5
      String line = input.readLine();
      if (line == null || !line.startsWith("ACCESS SERVER ")) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      line = line.substring(14);
      StringTokenizer st = new StringTokenizer(line, " RESOURCE ");
      int serverId = Integer.parseInt(st.nextToken());
      if (!st.hasMoreTokens()) {
        System.out.println("ERROR: Input file is incorrectly formatted. Program aborted.");
        System.exit(1);
      }
      int resourceId = Integer.parseInt(st.nextToken());
      return new ResourceAccess(owner.getServer(serverId), serverId, resourceId);
    }
  }

  /**
   * Attempts to acquire the lock of the specified resource. Blocks until the
   * lock has been acquired.
   *
   * @param resourceAccess Information about the resource we are trying to access.
   * @return  True if the lock was acquired, false otherwise.
   */
  private synchronized boolean acquireLock(ResourceAccess resourceAccess)
  {
    waitingForResource = resourceAccess;
    owner.println("Trying to claim lock of resource " + resourceAccess.resourceId + " at server " + resourceAccess.serverId, transactionId);
    try {
      if (resourceAccess.server.lockResource(transactionId, resourceAccess.resourceId)) {
        lockedResources.add(resourceAccess);
        waitingForResource = null;
        return true;
      }
    } catch (RemoteException re) {
      owner.lostContactWithServer(resourceAccess.serverId);
    }
    waitingForResource = null;
    System.err.println("We didn't get the lock we wanted! How can that happen?");
    return false;
  }

  /**
   * Aborts this transaction, releasing all the locks held by it.
   */
  private synchronized void abort()
  {
    owner.println("Aborting transaction " + transactionId + '.', transactionId);
    releaseLocks();
    owner.println("Transaction " + transactionId + " aborted.", transactionId);
  }

  /**
   * Commits this transaction, releasing all the locks held by it.
   */
  private synchronized void commit()
  {
    owner.println("Committing transaction " + transactionId + '.', transactionId);
    releaseLocks();
    owner.println("Transaction " + transactionId + " committed.", transactionId);
  }

  /**
   * Called by the server executing this transaction whenever a server in the distributed
   * system has disconnected. If the transaction is accessing any resources on the server
   * that disconnected, the transaction aborts.
   *
   * @param serverId The ID of the server that disconnected.
   */
  synchronized void serverDisconnected(int serverId)
  {
    for (ResourceAccess lockedResource : lockedResources) {
      if (lockedResource.serverId == serverId) {
        owner.println("Contact was lost with a server hosting a resource involved in transaction " + transactionId + ". Transaction must abort.", transactionId);
        abortTransaction = true;
        return;
      }
    }
  }

  /**
   * Releases all locks held by this transaction, in the reverse order of the order they were acquired in.
   */
  private synchronized void releaseLocks()
  {
    for (ResourceAccess lockedResource : lockedResources)
      releaseLock(lockedResource);
    lockedResources.clear();

    if (input != null) {
      // Scan to the end of this transaction in the input file
      String line = input.readLine();
      while (!line.equals("END OF TRANSACTION"))
        line = input.readLine();
    }
  }

  /**
   * Releases the lock to the specified resource being held by this transaction.
   *
   * @param resource The resource whose lock should be released.
   */
  private void releaseLock(ResourceAccess resource)
  {
    try {
      if (resource.server.releaseLock(transactionId, resource.resourceId)) {
        owner.println("Unlocked resource " + resource.resourceId + " at server " + resource.serverId, transactionId);
      }
      else {
        owner.println("Failed to unlock resource " + resource.resourceId + " at server " + resource.serverId, transactionId);
      }
    } catch (RemoteException re) {
      owner.println("Failed to unlock resource " + resource.resourceId + " at server " + resource.serverId + " due to communication failure.", transactionId);
    }
  }
}
