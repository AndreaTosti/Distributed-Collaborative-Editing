import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import java.util.Date;

public class Client
{
  private static String DEFAULT_HOST = "localhost"; //Host di default
  private static int DEFAULT_PORT = 51811; //Porta di default
  private static int DEFAULT_RMI_PORT = 51812; //Porta RMI di default
  private static String DEFAULT_DELIMITER = "#";
  private static String DEFAULT_INTERIOR_DELIMITER = ":";
  private static String DEFAULT_PARENT_FOLDER = "518111_ClientDirs";

  private static Pattern validPattern = Pattern.compile("[A-Za-z0-9_]+");

  private static boolean isNotAValidString(String string)
  {
    return !(validPattern.matcher(string).matches());
  }

  private static void println(Object o)
  {
    System.out.println("[Client] " + o);
  }

  private static void printErr(Object o)
  {
    System.err.println("[Client-Error] " + o);
  }

  private static int read(SocketChannel client, ByteBuffer buffer,
                          int dimBuffer) throws IOException
  {
    int res = 0;

    do
    {
      res += client.read(buffer);
      //println("Read " + res + "/" + dimBuffer + " bytes");
    }while(res != dimBuffer && res != -1);

    return res;
  }

  private static Op sendRequest(String joinedString, SocketChannel client)
  {
    byte[] operation = joinedString.getBytes(StandardCharsets.ISO_8859_1);

    ByteBuffer bufferDimensione = ByteBuffer.allocate(Long.BYTES);
    bufferDimensione.putLong(operation.length);
    bufferDimensione.flip();

    ByteBuffer buffer1 = ByteBuffer.wrap(operation);
    try
    {
      while(bufferDimensione.hasRemaining())
        client.write(bufferDimensione);

      while(buffer1.hasRemaining())
        client.write(buffer1);

      return Op.SuccessfullySent;
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }
  }

  private static Op receiveOutcome(SocketChannel client)
  {
    ByteBuffer bufferDimensione = ByteBuffer.allocate(Long.BYTES);
    int resD, resN, resD2;

    try
    {

      resD = read(client, bufferDimensione, bufferDimensione.capacity());

      if(resD < 0)
        return Op.ClosedConnection;

      bufferDimensione.flip();
      int dimensione = new Long(bufferDimensione.getLong()).intValue();

      ByteBuffer buffer = ByteBuffer.allocate(dimensione);
      int res;
      res = read(client, buffer, buffer.capacity());
      if(res < 0)
      {
        return Op.ClosedConnection;
      }
      else
      {
        buffer.flip();
        String result = new String(buffer.array(), 0,
                res, StandardCharsets.ISO_8859_1);

        //Se ho una notifica, provvedo a riceverla
        if(Op.valueOf(result) == Op.newNotification)
        {
          ByteBuffer bufferDimensioneNotifica = ByteBuffer.allocate(Long.BYTES);
          resN =read(client, bufferDimensioneNotifica,
                  bufferDimensioneNotifica.capacity());
          if(resN < 0)
            return Op.ClosedConnection;

          bufferDimensioneNotifica.flip();
          int dimensioneNotifica =
                  new Long(bufferDimensioneNotifica.getLong()).intValue();

          ByteBuffer bufferNotifica = ByteBuffer.allocate(dimensioneNotifica);
          res = read(client, bufferNotifica, bufferNotifica.capacity());
          if(res < 0)
            return Op.ClosedConnection;

          bufferNotifica.flip();
          String notifica = new String(bufferNotifica.array(), 0,
                  dimensioneNotifica, StandardCharsets.ISO_8859_1);
          println(notifica);

          //Leggo il risultato dell'operazione richiesta
          ByteBuffer bufferDimensioneRisultato = ByteBuffer.allocate(Long.BYTES);
          resD2 = read(client, bufferDimensioneRisultato,
                  bufferDimensioneRisultato.capacity());
          if(resD2 < 0)
            return Op.ClosedConnection;

          bufferDimensioneRisultato.flip();
          int dimensioneRisultato =
                  new Long(bufferDimensioneRisultato.getLong()).intValue();

          ByteBuffer bufferRisultato = ByteBuffer.allocate(dimensioneRisultato);
          res = read(client, bufferRisultato, bufferRisultato.capacity());
          if(res < 0)
            return Op.ClosedConnection;

          bufferRisultato.flip();
          result = new String(bufferRisultato.array(), 0,
                  res, StandardCharsets.ISO_8859_1);

        }
        return Op.valueOf(result);
      }
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }
  }

  private static Op handleTuring(String[] splitted)
  {
    //Controllo il numero di parametri
    if(splitted.length != 2)
    {
      printErr("Usage: turing --help");
      return Op.UsageError;
    }

    if(!splitted[1].equals("--help"))
    {
      printErr("Usage: turing --help");
      return Op.UsageError;
    }

    println("commands:");
    println("\tregister <username> <password> registra l'utente");
    println("\tlogin <username> <password>    effettua il login");
    println("\tlogout                         effettua il logout");
    println("\tcreate <doc> <numsezioni>      crea un documento");
    println("\tshare <doc> <username>         condivide il documento");
    println("\tshow <doc> <sec>               mostra una sezione del documento");
    println("\tshow <doc>                     mostra l'intero documento");
    println("\tlist                           mostra la lista dei documenti");
    println("\tedit <doc> <sec>               modifica una sezione del documento");
    println("\tend-edit <doc> <sec>           fine modifica della sezione del doc.");
    println("\tsend <msg>                     invia un msg sulla chat");
    println("\treceive                        visualizza i msg ricevuti sulla chat");
    println("\tediting                        stampa la sezione che si sta editando");

    return Op.SuccessfullyShownHelp;
  }

  private static Op handleRegister(String[] splitted, String host, int rmiPort)
  {
    //Controllo il numero di parametri
    if(splitted.length != 3)
    {
      printErr("Usage: register <username> <password>");
      return Op.UsageError;
    }

    String username = splitted[1];
    String password = splitted[2];

    if(isNotAValidString(username) || isNotAValidString(password))
    {
      printErr("Usage: register <username> <password>");
      return Op.UsageError;
    }

    //REGISTRAZIONE RMI
    try
    {
      Registry registry = LocateRegistry.getRegistry(host, rmiPort);
      RegUtenteInterface stub = (RegUtenteInterface) registry.lookup("RegUtente");
      return stub.registerUser(username, password);
    }
    catch(RemoteException e1)
    {
      e1.printStackTrace();
      return Op.ClosedConnection;
    }
    catch(NotBoundException e)
    {
      e.printStackTrace();
      return Op.Error;
    }
  }

  private static Op handleLogin(String[] splitted, SocketChannel client)
  {
    //Controllo il numero di parametri
    if(splitted.length != 3)
    {
      printErr("Usage: login <username> <password>");
      return Op.UsageError;
    }

    String username = splitted[1];
    String password = splitted[2];

    //LOGIN TCP
    //login#username#password
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.Login.toString()).add(username).add(password);
    Op resultSend = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);
  }

  private static Op handleLogout(SocketChannel client)
  {
    //LOGOUT TCP
    //logout
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.Logout.toString());
    Op result = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);
  }

  private static Op handleCreate(String[] splitted, SocketChannel client)
  {
    //Controllo il numero di parametri
    if(splitted.length != 3)
    {
      printErr("Usage: create <doc> <numsezioni>");
      return Op.UsageError;
    }

    String nomeDocumento = splitted[1];

    if(isNotAValidString(nomeDocumento))
    {
      printErr("Usage: create <doc> <numsezioni>");
      return Op.UsageError;
    }

    int numSezioni;
    try
    {
      numSezioni = Integer.parseInt(splitted[2]);
    }
    catch(NumberFormatException e)
    {
      printErr("Usage: create <doc> <numsezioni>");
      return Op.UsageError;
    }

    if(numSezioni < 1)
    {
      printErr("Usage: create <doc> <numsezioni>");
      return Op.UsageError;
    }

    //CREAZIONE DOCUMENTO TCP
    //create#nomedocumento#numsezioni
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.Create.toString()).add(nomeDocumento).add(splitted[2]);

    Op result = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);

  }

  private static Op handleShare(String[] splitted, SocketChannel client)
  {
    //Controllo il numero di parametri
    if(splitted.length != 3)
    {
      printErr("Usage: share <doc> <username>");
      return Op.UsageError;
    }

    String nomeDocumento = splitted[1];
    String username = splitted[2];

    if(isNotAValidString(nomeDocumento) || isNotAValidString((username)))
    {
      printErr("Usage: share <doc> <username>");
      return Op.UsageError;
    }

    //CONDIVISIONE DOCUMENTO TCP
    //share#nomedocumento#username
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.Share.toString()).add(nomeDocumento).add(username);

    Op result = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);

  }

  private static Op handleShow(String[] splitted, SocketChannel client)
  {
    //Controllo il numero di parametri
    if(splitted.length != 2 && splitted.length != 3)
    {
      printErr("Usage: show <doc> [<sec>]");
      return Op.UsageError;
    }

    String nomeDocumento = splitted[1];

    if(isNotAValidString(nomeDocumento))
    {
      printErr("Usage: show <doc> [<sec>]");
      return Op.UsageError;
    }

    int numSezione;
    if(splitted.length == 3)
    {
      try
      {
        numSezione = Integer.parseInt(splitted[2]);
      }
      catch(NumberFormatException e)
      {
        printErr("Usage: show <doc> [<sec>]");
        return Op.UsageError;
      }
      if(numSezione < 0)
      {
        printErr("Usage: show <doc> [<sec>]");
        return Op.UsageError;
      }
    }

    //Visualizzazione di una sezione o dell'intero documento
    //show#nomedocumento[#numsezione]
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.Show.toString()).add(nomeDocumento);
    if(splitted.length == 3)
      joiner.add(splitted[2]);

    Op result = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);
  }

  private static Op receiveSections(String[] splitted, SocketChannel client,
                                    String loggedInNickname, EditingRoom editingRoom,
                                    StringBuilder receivedMulticastAddress)
  {
    ByteBuffer bufferNumSezioni = ByteBuffer.allocate(Long.BYTES);

    int resNi, resD, resNe, resI, resS, res, counter;
    try
    {
      resNi = read(client, bufferNumSezioni, bufferNumSezioni.capacity());
      if(resNi < 0)
        return Op.ClosedConnection;

      bufferNumSezioni.flip();
      int numeroSezioni = new Long(bufferNumSezioni.getLong()).intValue();

      for(int i = 0; i < numeroSezioni; i++)
      {
        ByteBuffer bufferDimensione = ByteBuffer.allocate(Long.BYTES);
        ByteBuffer bufferNumSezione = ByteBuffer.allocate(Long.BYTES);
        ByteBuffer bufferIndirizzoMulticast = ByteBuffer.allocate(Long.BYTES);
        ByteBuffer bufferStato      = ByteBuffer.allocate(Long.BYTES);

        resD = read(client, bufferDimensione, bufferDimensione.capacity());
        if(resD < 0)
          return Op.ClosedConnection;

        resNe = read(client, bufferNumSezione, bufferNumSezione.capacity());
        if(resNe < 0)
          return Op.ClosedConnection;

        resI = read(client, bufferIndirizzoMulticast,
                bufferIndirizzoMulticast.capacity());
        if(resI < 0)
          return Op.ClosedConnection;

        resS = read(client, bufferStato, bufferStato.capacity());
        if(resS < 0)
          return Op.ClosedConnection;

        bufferDimensione.flip();
        int dimensioneFile = new Long(bufferDimensione.getLong()).intValue();

        bufferNumSezione.flip();
        int numeroSezione = new Long(bufferNumSezione.getLong()).intValue();

        if(editingRoom.isEditing())
        {
          bufferIndirizzoMulticast.flip();
          long longIP = bufferIndirizzoMulticast.getLong();

          //Trasforma IP da Long https://stackoverflow.com/a/53105157
          StringBuilder sb = new StringBuilder(15);
          for (int j = 0; j < 4; j++)
          {
            sb.insert(0,(longIP & 0xff));
            if (j < 3) {
              sb.insert(0,'.');
            }
            longIP = longIP >> 8;
          }
          receivedMulticastAddress.append(sb.toString());
        }

        bufferStato.flip();
        long stato = bufferStato.getLong();

        ByteBuffer buffer = ByteBuffer.allocate(dimensioneFile);
        //Il nome della cartella conterrà anche
        //l'username per distinguerlo da altri client sullo stesso host

        //Si assume che il nomeSezione sia nomeDocumento_numSezione
        String nomeDocumento = splitted[1];
        Path filePath = Paths.get(System.getProperty("user.dir") +
                File.separator + DEFAULT_PARENT_FOLDER +
                File.separator + loggedInNickname +
                File.separator + nomeDocumento +
                File.separator + nomeDocumento +
                "_" + numeroSezione + ".txt");

        Path directoryPath = Paths.get(System.getProperty("user.dir") +
                File.separator + DEFAULT_PARENT_FOLDER +
                File.separator + loggedInNickname +
                File.separator + nomeDocumento);

        if (!Files.exists(directoryPath))
          Files.createDirectories(directoryPath);

        FileChannel fileChannel = FileChannel.open(filePath, EnumSet.of(
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE));

        counter = 0;

        do
        {
          buffer.clear();
          res = read(client, buffer, buffer.capacity());
          if(res < 0)
          {
            return Op.ClosedConnection;
          }
          else
          {
            buffer.flip();
            if(res > 0)
            {
              fileChannel.write(buffer);
              counter += res;
            }
            dimensioneFile -= res;
          }
        }while(dimensioneFile > 0);

        fileChannel.close();
        println("La sezione " + nomeDocumento + "_" + numeroSezione +
                " (" + counter + " bytes) " +
                (stato == 1 ?
                              "e' attualmente sotto modifiche" :
                              "non e' attualmente sotto modifiche"));
      }
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }
    return Op.SuccessfullyReceivedSections;
  }

  private static Op handleList(SocketChannel client)
  {
    //LIST TCP
    //list
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.List.toString());
    Op result = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);
  }

  private static Op receiveList(SocketChannel client)
  {
    ByteBuffer bufferDimensione = ByteBuffer.allocate(Long.BYTES);
    int resD, res;

    try
    {
      resD = read(client, bufferDimensione, bufferDimensione.capacity());
      if(resD < 0)
        return Op.ClosedConnection;

      bufferDimensione.flip();
      int dimensione = new Long(bufferDimensione.getLong()).intValue();

      ByteBuffer buffer = ByteBuffer.allocate(dimensione);
      res = read(client, buffer, buffer.capacity());
      if(res != dimensione)
        return Op.Error;

      buffer.flip();
      String toSplit = new String(buffer.array(), 0,
              dimensione, StandardCharsets.ISO_8859_1);
      String[] splittedDocs = toSplit.split(DEFAULT_DELIMITER);

      StringBuilder builder = new StringBuilder();
      for(String documento : splittedDocs)
      {
        if(documento.equals(""))
          continue;
        String[] splitted = documento.split(DEFAULT_INTERIOR_DELIMITER);
        builder.append("\n\t\t");
        builder.append(splitted[0]);
        builder.append(":");
        builder.append("\n\t\t\tCreatore: ");
        builder.append(splitted[1]);
        builder.append("\n\t\t\tCollaboratori: ");
        for(int i = 2; i < splitted.length; i++)
        {
          builder.append(splitted[i]);
          builder.append(" ");
        }
      }

      String result = builder.toString();
      if(result.equals(""))
        println("Non ci sono documenti di cui si e' creatori o di cui si collabora");
      else
        println(builder.toString());
      return Op.SuccessfullyReceivedList;
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }
  }

  private static Op handleEdit(String[] splitted, SocketChannel client)
  {
    //Controllo il numero di parametri
    if(splitted.length != 3)
    {
      printErr("Usage: edit <doc> <sec>");
      return Op.UsageError;
    }

    String nomeDocumento = splitted[1];

    if(isNotAValidString(nomeDocumento))
    {
      printErr("Usage: edit <doc> <sec>");
      return Op.UsageError;
    }

    int numSezione;
    try
    {
      numSezione = Integer.parseInt(splitted[2]);
    }
    catch(NumberFormatException e)
    {
      printErr("Usage: edit <doc> <sec>");
      return Op.UsageError;
    }
    if(numSezione < 0)
    {
      printErr("Usage: edit <doc> <sec>");
      return Op.UsageError;
    }

    //Richiesta modifica di una sezione
    //edit#nomedocumento#numsezione
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.Edit.toString()).add(nomeDocumento).add(splitted[2]);

    Op result = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);

  }

  private static Op handleEndEdit(String[] splitted, SocketChannel client)
  {
    //Controllo il numero di parametri
    if(splitted.length != 3)
    {
      printErr("Usage: end-edit <doc> <sec>");
      return Op.UsageError;
    }

    String nomeDocumento = splitted[1];

    if(isNotAValidString(nomeDocumento))
    {
      printErr("Usage: end-edit <doc> <sec>");
      return Op.UsageError;
    }

    int numSezione;
    try
    {
      numSezione = Integer.parseInt(splitted[2]);
    }
    catch(NumberFormatException e)
    {
      printErr("Usage: end-edit <doc> <sec>");
      return Op.UsageError;
    }
    if(numSezione < 0)
    {
      printErr("Usage: end-edit <doc> <sec>");
      return Op.UsageError;
    }

    //Richiesta fine modifica di una sezione
    //end-edit#nomedocumento#numsezione
    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);
    joiner.add(Op.EndEdit.toString()).add(nomeDocumento).add(splitted[2]);

    Op result = sendRequest(joiner.toString(), client);

    return receiveOutcome(client);
  }

  private static Op sendSection(String[] splitted, SocketChannel client,
                                String loggedInNickname)
  {
    String nomeDocumento = splitted[1];
    int numSezione = Integer.parseInt(splitted[2]);
    Path filePath = Paths.get(System.getProperty("user.dir") +
            File.separator + DEFAULT_PARENT_FOLDER +
            File.separator + loggedInNickname +
            File.separator + nomeDocumento +
            File.separator + nomeDocumento +
            "_" + numSezione + ".txt");
    try
    {
      if(Files.notExists(filePath))
      {
        //la sezione non esiste nella directory
        //creo un file vuoto e lo invio lo stesso
        printErr("Filename " + nomeDocumento +
                "_" + numSezione + ".txt" +
                " does not exists in the current working directory: " +
                System.getProperty("user.dir"));
        //Creo un file vuoto
        Files.createFile(filePath);
      }
      FileChannel fileChannel = FileChannel.open(filePath);

      long dimensioneFile = fileChannel.size();
      ByteBuffer bufferDimensione = ByteBuffer.allocate(Long.BYTES);
      bufferDimensione.putLong(dimensioneFile);
      bufferDimensione.flip();

      //Decido di bufferizzare l'intero file
      ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(dimensioneFile));
      int bytesReadFromFile = 0;

      do
      {
        bytesReadFromFile += fileChannel.read(buffer);
        println("Read " + bytesReadFromFile + "/" + dimensioneFile + " bytes " +
                "from file");
      }while(dimensioneFile > bytesReadFromFile);

      fileChannel.close();

      buffer.flip();

      while(bufferDimensione.hasRemaining())
        client.write(bufferDimensione);

      while(buffer.hasRemaining())
        client.write(buffer);

      return Op.SuccessfullySentSection;
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }
  }

  private static Op handleSend(String[] splitted, SocketChannel client,
                               String loggedInNickname, EditingRoom editingRoom,
                               int clientPort)
  {
    //Controllo il numero di parametri
    if(splitted.length != 2)
    {
      printErr("Usage: send <msg>");
      return Op.UsageError;
    }

    if(!editingRoom.isEditing())
      return Op.MustBeInEditingState;

    String messaggio = splitted[1];

    StringBuilder builder = new StringBuilder();

    //Metto il nickname e un delimitatore che verranno poi scartati (ridondanti)
    builder.append(loggedInNickname);
    builder.append(DEFAULT_DELIMITER);

    builder.append("[");
    builder.append(editingRoom.getMulticastAddress());
    builder.append("] ");
    builder.append(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss",
            Locale.ITALY).format(new Date()));
    builder.append(" ");
    builder.append(loggedInNickname);
    builder.append(": ");
    builder.append(messaggio);
    try
    {
      DatagramPacket packetToSend = new DatagramPacket(
            builder.toString().getBytes(StandardCharsets.UTF_8),
            builder.toString().getBytes(StandardCharsets.UTF_8).length,
            InetAddress.getByName(editingRoom.getMulticastAddress()), clientPort);

      editingRoom.getMulticastSocket().send(packetToSend);
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }

    return Op.SuccessfullySentMessage;
  }

  private static Op handleReceive(SocketChannel client, String loggedInNickname,
                                  EditingRoom editingRoom,
                                  LinkedBlockingQueue<DatagramPacket> receivedPacketsQueue)
  {
    if(!editingRoom.isEditing())
      return Op.MustBeInEditingState;

    DatagramPacket receivedPacket;
    while((receivedPacket = receivedPacketsQueue.poll()) != null)
    {
      String receivedMessage = new String(receivedPacket.getData(), 0,
              receivedPacket.getLength(), StandardCharsets.UTF_8);
      String[] splittedReceivedMessage = receivedMessage.split(DEFAULT_DELIMITER, 2);
      if(!splittedReceivedMessage[0].equals(loggedInNickname))
        println(splittedReceivedMessage[1]);
    }

    return Op.SuccessfullyReceivedMessage;
  }

  private static Op handlePrintEditing(String sectionBeingEdited)
  {
    println("Stai attualmente editando " + sectionBeingEdited);
    return Op.SuccessfullyPrintedEditing;
  }

  public static void main(String[] args)
  {
    String host;
    int clientPort;
    int rmiPort;
    try
    {
      if(args.length == 0)
      {
        //Non ho passato argomenti
        host = DEFAULT_HOST;
        clientPort = DEFAULT_PORT;
        rmiPort = DEFAULT_RMI_PORT;
        println("No arguments specified, using default host " + DEFAULT_HOST +
                ", default port number " + DEFAULT_PORT + " and default RMI " +
                "port number " + DEFAULT_RMI_PORT);
      }
      else if(args.length == 1)
      {
        //Ho passato solo il nome dell'host
        host = args[0];
        clientPort = DEFAULT_PORT;
        rmiPort = DEFAULT_RMI_PORT;
        println("Will connect to chosen host " + host + ", default port number " +
                DEFAULT_PORT + " and default RMI port number " + DEFAULT_RMI_PORT) ;
      }
      else if(args.length == 2)
      {
        //Ho passato il nome host e numero di porta
        host = args[0];
        clientPort = Integer.parseInt(args[1]);
        rmiPort = DEFAULT_RMI_PORT;
        println("Will connect to chosen host " + host + ", chosen port number " +
                clientPort + " and default RMI port number " + DEFAULT_RMI_PORT);
      }
      else if(args.length == 3)
      {
        host = args[0];
        clientPort = Integer.parseInt(args[1]);
        rmiPort = Integer.parseInt(args[2]);
        println("Will connect to chosen host " + host + ", chosen port number " +
                clientPort + " and chosen RMI port number " + rmiPort);
      }
      else
      {
        println("Usage: java Client [host] [port_number] [RMI_port_number]");
        return;
      }
    }
    catch(NumberFormatException e)
    {
      printErr("Usage: java Client [host] [port_number] [RMI_port_number]");
      return;
    }

    SocketAddress address = new InetSocketAddress(host, clientPort);
    SocketChannel client = null;
    try
    {
      client = SocketChannel.open(address);
      client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
    }
    catch(ConnectException | UnresolvedAddressException e1)
    {
      printErr("Connection refused on address " + address);
      printErr("Exiting...");
      return;
    }
    catch(IOException e2)
    {
      e2.printStackTrace();
    }

    println("Successfuly connected at " + address);
    println("Use turing --help to view all available commands");

    EditingRoom editingRoom = new EditingRoom(false, clientPort);

    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String stdin;

    String loggedInNickname = null;
    String sectionBeingEdited = "nessuna sezione";

    LinkedBlockingQueue<DatagramPacket> receivedPacketsQueue =
            new LinkedBlockingQueue<>();

    ReceivePacketsTask receivePacketsTask =
            new ReceivePacketsTask(receivedPacketsQueue, editingRoom);
    Thread thPacketListener = new Thread(receivePacketsTask);
    thPacketListener.start();

    try
    {
      //Input da linea di comando
      Op result = null;
      Op result_2;

      while(result != Op.ClosedConnection)
      {
        stdin = reader.readLine();
        if(stdin == null)
          continue;

        String[] splitted = stdin.split("\\s+");

        switch(splitted[0].toLowerCase())
        {
          case "turing" :
            result = handleTuring(splitted);
            break;

          case "register" :
            if(loggedInNickname != null)
            {
              Op.print(Op.MustBeInStartedState);
            }
            else
            {
              result = handleRegister(splitted, host, rmiPort);
              if(result == Op.SuccessfullyRegistered)
              {
                println("Registrazione eseguita con successo.");
              }
              else
              {
                Op.print(result);
              }
            }
            break;

          case "login" :
            result = handleLogin(splitted, client);
            if(result == Op.SuccessfullyLoggedIn)
            {
              loggedInNickname = splitted[1];
              println("Login eseguito con successo.");
            }
            else
            {
              Op.print(result);
            }
            break;

          case "logout" :
            result = handleLogout(client);
            if(result == Op.SuccessfullyLoggedOut)
            {
              loggedInNickname = null;
              println("Logout eseguito con successo.");
            }
            else
            {
              Op.print(result);
            }
            break;

          case "create" :
            result = handleCreate(splitted, client);
            if(result == Op.SuccessfullyCreated)
            {
              println("Documento " + splitted[1] + ", composto da " +
                      splitted[2] + " sezioni, creato con successo");
            }
            else
            {
              Op.print(result);
            }
            break;

          case "share" :
            result = handleShare(splitted, client);
            if(result == Op.SuccessfullyShared)
            {
              println("Documento " + splitted[1] + " condiviso con " +
                      splitted[2] + " con successo");
            }
            else
            {
              Op.print(result);
            }
            break;

          case "show" :
            result = handleShow(splitted, client);
            if(result == Op.SuccessfullyShown)
            {
              assert(client != null);
              StringBuilder receivedMulticastAddress = new StringBuilder();
              result_2 = receiveSections(splitted, client, loggedInNickname,
                      editingRoom, receivedMulticastAddress);

              if(result_2 == Op.SuccessfullyReceivedSections)
              {
                if(splitted.length == 3)
                {
                  println("Sezione " + splitted[2] + " scaricata con successo");
                }
                else
                {
                  println("L'intero documento " + splitted[1] +
                          " e' stato scaricato con successo");
                }
              }
              else
              {
                Op.print(result_2);
              }
            }
            else
            {
              Op.print(result);
            }
            break;

          case "list" :
            result = handleList(client);
            if(result == Op.SuccessfullyListed)
            {
              assert(client != null);
              result_2 = receiveList(client);
              if(result_2 != Op.SuccessfullyReceivedList)
              {
                Op.print(result_2);
              }
            }
            else
            {
              Op.print(result);
            }
            break;

          case "edit" :
            result = handleEdit(splitted, client);
            if(result == Op.SuccessfullyStartedEditing)
            {
              assert(client != null);
              editingRoom.setEditing(true);
              StringBuilder receivedMulticastAddress = new StringBuilder();
              result_2 = receiveSections(splitted, client, loggedInNickname,
                      editingRoom, receivedMulticastAddress);
              if(result_2 == Op.SuccessfullyReceivedSections)
              {
                editingRoom.joinGroup(receivedMulticastAddress.toString());

                sectionBeingEdited = "la sezione " + splitted[2] + " del documento " +
                        splitted[1];
                println("Sezione " + splitted[2] + " del documento " +
                        splitted[1] + " scaricata con successo");
              }
              else
              {
                Op.print(result_2);
              }
            }
            else
            {
              Op.print(result);
            }
            break;

          case "end-edit" :
            result = handleEndEdit(splitted, client);
            if(result == Op.SuccessfullyEndedEditing)
            {
              editingRoom.setEditing(false);

              assert(client != null);
              result_2 = sendSection(splitted, client, loggedInNickname);
              if(result_2 == Op.SuccessfullySentSection)
              {
                editingRoom.leaveGroup();
                sectionBeingEdited = "nessuna sezione";
                println("Sezione " + splitted[2] + " del documento " +
                        splitted[1] + " aggiornata con successo.");
              }
              else
              {
                Op.print(result_2);
              }
            }
            else
            {
              Op.print(result);
            }
            break;

          case "send" :
            splitted = stdin.split("\\s+", 2);
            result = handleSend(splitted, client, loggedInNickname,
                    editingRoom, clientPort);
            if(result == Op.SuccessfullySentMessage)
            {
              println("Messaggio inviato sulla chat.");
            }
            else
            {
              Op.print(result);
            }
            break;

          case "receive" :
            result = handleReceive(client, loggedInNickname,
                    editingRoom, receivedPacketsQueue);
            if(result == Op.SuccessfullyReceivedMessage)
            {
              println("Tutti i messaggi sono stati ricevuti");
            }
            else
            {
              Op.print(result);
            }
            break;

          case "editing" :
            result = handlePrintEditing(sectionBeingEdited);
            break;

          default:
            printErr("Comando non riconosciuto");
            break;
        }
      }
      reader.close();
    }
    catch(Exception e)
    {
      e.printStackTrace();
    }
  }
}
