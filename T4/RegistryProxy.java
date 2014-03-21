import java.rmi.*;

/**
 * This interface specifies the functionality implemented by the RegistryProxyImpl class.
 *
 * @see RegistryProxyImpl
 */
public interface RegistryProxy extends Remote
{
  void bind(String name, Remote object) throws RemoteException, AlreadyBoundException;

  void unbind(String name) throws RemoteException, NotBoundException;

  Remote lookup(String name) throws RemoteException, NotBoundException;

  void rebind(String name, Remote object) throws RemoteException;

  String[] list() throws RemoteException;
}
