import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RegUtenteInterface extends Remote
{
  //Registra un nuovo utente
  public Op registerUser(String username, String password) throws RemoteException;

}
