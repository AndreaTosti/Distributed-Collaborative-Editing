import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

public class ReceivePacketsTask implements Runnable
{
  private static int DEFAULT_BUFFER_SIZE = 4096;
  private byte[] buffer;
  private LinkedBlockingQueue<DatagramPacket> receivedPacketsQueue;
  private EditingRoom editingRoom;


  ReceivePacketsTask(LinkedBlockingQueue<DatagramPacket> receivedPacketsQueue,
                     EditingRoom editingRoom)
  {
    this.receivedPacketsQueue = receivedPacketsQueue;
    this.editingRoom = editingRoom;
  }

  @Override
  public void run()
  {
    MulticastSocket multicastSocket = editingRoom.getMulticastSocket();
    while(true)
    {
      try
      {
        editingRoom.canReceiveAnotherPacket();

        buffer = new byte[DEFAULT_BUFFER_SIZE];
        DatagramPacket packetToReceive = new DatagramPacket(buffer, buffer.length);
        assert multicastSocket != null;
        multicastSocket.receive(packetToReceive);
        receivedPacketsQueue.put(packetToReceive);
      }
      catch(SocketTimeoutException ignored){}
      catch(IOException | InterruptedException e2)
      {
        e2.printStackTrace();
        System.exit(1);
      }
    }
  }
}
