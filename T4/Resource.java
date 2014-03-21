
/**
 * A resource with an associated lock that can be held by only one transaction at a time.
 */
class Resource
{
    static final int NOT_LOCKED = -1;

    /**
     * The transaction currently holding the lock to this resource
     */
    private int lockOwner;

    /**
     * Creates a new resource.
     */

    private ServerImpl server;
    Resource(ServerImpl server)
    {
        this.server = server;
        lockOwner = NOT_LOCKED;
    }

    /**
     * Gives the lock of this resource to the requesting transaction. Blocks
     * the caller until the lock could be acquired.
     *
     * @param transactionId The ID of the transaction that wants the lock.
     * @return Whether or not the lock could be acquired.
     */
    synchronized boolean lock(int transactionId)
    {
        if(lockOwner == transactionId) {
            System.err.println("Error: Transaction " + transactionId + " tried to lock a locked resource by itself");
            return false;
        }

        if(Globals.PROBING_ENABLED) {
            while(lockOwner != NOT_LOCKED) {
                Probe probe = new Probe(server, transactionId);
                probe.start();

                if(lockOwner != NOT_LOCKED) return false;
            }
        } else {
            while(lockOwner != NOT_LOCKED) {
                try {
                    wait((long)(Globals.TIMEOUT_INTERVAL * Math.random()*1000));
                } catch (InterruptedException ie) { break; }

                if(lockOwner == NOT_LOCKED) {
                    lockOwner = transactionId;
                    return true;
                } else {
                    return false;
                }
            }
        }

        // will never happen, just to satisfy java
        // i expect a it's happening.gif sometime in the future because of this
        return false;
    }

    /**
     * Releases the lock of this resource.
     *
     * @param transactionId The ID of the transaction that wants to release lock.
     *                      If this transaction doesn't currently own the lock an
     *                      error message is displayed.
     * @return Whether or not the lock could be released.
     */
    synchronized boolean unlock(int transactionId)
    {
        if (lockOwner == NOT_LOCKED || lockOwner != transactionId) {
            System.err.println("Error: Transaction " + transactionId + " tried to unlock a resource without owning the lock!");
            return false;
        }

        lockOwner = NOT_LOCKED;
        // Notify a waiting thread that it can acquire the lock
        notifyAll();
        return true;
    }

    /**
     * Gets the current owner of this resource's lock.
     *
     * @return An Integer containing the ID of the transaction currently
     * holding the lock, or NOT_LOCKED if the resource is unlocked.
     */
    synchronized int getLockOwner()
    {
        return lockOwner;
    }

    /**
     * Unconditionally releases the lock of this resource.
     */
    synchronized void forceUnlock()
    {
        lockOwner = NOT_LOCKED;
        // Notify a waiting thread that it can acquire the lock
        notifyAll();
    }

    /**
     * Checks if this resource's lock is held by a transaction running on the specified server.
     *
     * @param serverId The ID of the server.
     * @return Whether or not the current lock owner is running on that server.
     */
    synchronized boolean isLockedByServer(int serverId)
    {
        return lockOwner != NOT_LOCKED && ServerImpl.getTransactionOwner(lockOwner) == serverId;
    }

    public synchronized void abortCurrentTransaction(Transaction trans) {
        notifyAll();
    }
}
