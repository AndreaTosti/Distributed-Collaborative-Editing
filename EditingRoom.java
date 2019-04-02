import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.LinkedList;
import java.util.StringJoiner;

class EditingRoom
{
  private boolean isEditing;
  private static String DEFAULT_DELIMITER = "#";

  private LinkedList<String> requestQueue;

  private LinkedList<String> joinedAddresses;

  private static int soTimeout = 1000; //1 secondo

  private MulticastSocket multicastSocket;

  EditingRoom(boolean isEditing, int port)
  {
    this.isEditing = isEditing;
    try
    {
      this.multicastSocket = new MulticastSocket(port);
      this.multicastSocket.setSoTimeout(soTimeout);
    }
    catch(IOException e)
    {
      e.printStackTrace();
    }
    requestQueue = new LinkedList<>();
    joinedAddresses = new LinkedList<>();
  }

  synchronized void joinGroup(String multicastAddress) throws InterruptedException
  {
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add("join").add(multicastAddress);
    requestQueue.add(joiner.toString());

    //Notifico il threadUDP che c'è una richiesta da elaborare
    this.notifyAll();

    //Il main deve aspettare che il thread UDP abbia elaborato la richiesta
    while(requestQueue.size() != 0)
      wait();
  }

  synchronized void leaveGroup() throws InterruptedException
  {
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add("leave");
    requestQueue.add(joiner.toString());

    //Notifico il threadUDP che c'è una richiesta da elaborare
    //In realtà non è indispensabile metterlo perchè sicuramente
    //joinedAddresses > 0
    //e quindi in canReceiveAnotherPacket() non verrà mai fatta la wait()
    this.notifyAll();

    //Il main deve aspettare che il thread UDP abbia elaborato la richiesta
    while(requestQueue.size() != 0)
      wait();
  }

  private synchronized boolean computeRequests()
  {
    String request;
    try
    {
      while(( request = requestQueue.poll() ) != null)
      {
        String[] splitted = request.split(DEFAULT_DELIMITER);
        assert ( splitted.length == 2 );
        switch(splitted[0])
        {
          case "join":
            multicastSocket.joinGroup(InetAddress.getByName(splitted[1]));
            System.out.println("[ThreadReceiver] La chat e' stata instaurata su " +
                    "indirizzo multicast " + splitted[1]);
            assert(joinedAddresses.size() == 0);
            joinedAddresses.addLast(splitted[1]);
            break;
          case "leave":
            assert(joinedAddresses.size() == 1);
            String address = joinedAddresses.remove();
            multicastSocket.leaveGroup(InetAddress.getByName(address));
            System.out.println("[ThreadReceiver] La chat e' stata abbandonata su " +
                    "indirizzo multicast " + address);
            break;
          default:
            break;
        }
      }
    }
    catch(IOException e)
    {
      e.printStackTrace();
    }
    this.notifyAll();

    return joinedAddresses.size() != 0;
  }

  synchronized void canReceiveAnotherPacket() throws InterruptedException
  {
    while(!computeRequests())
    {
      wait();
    }
  }

  synchronized String getMulticastAddress()
  {
    assert(joinedAddresses.size() == 1);
    return joinedAddresses.getFirst();
  }

  synchronized MulticastSocket getMulticastSocket()
  {
    assert(joinedAddresses.size() == 1);
    return multicastSocket;
  }

  boolean isEditing()
  {
    return isEditing;
  }

  void setEditing(boolean editing)
  {
    isEditing = editing;
  }

}
