import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Server
{
  private static String DEFAULT_HOST = "localhost"; //Host di default
  private static int DEFAULT_PORT = 51811; //Porta di default
  private static int DEFAULT_RMI_PORT = 51812; //Porta RMI di default
  private static String DEFAULT_DELIMITER = "#";
  private static String DEFAULT_INTERIOR_DELIMITER = ":";
  private static String DEFAULT_PARENT_FOLDER = "518111_ServerDirs";

  private static final ConcurrentMap<String, Utente> users = new ConcurrentHashMap<>();

  private static final Map<SocketChannel, Sessione> sessions = new HashMap<>();

  private static final Map<String, Documento> documents = new HashMap<>();


  private static void println(Object o)
  {
    System.out.println("[Server] " + o);
  }

  private static void printErr(Object o)
  {
    System.err.println("[Server-Error] " + o);
  }


  private static Op handleClosedConnection(SocketChannel channel)
  {
    Op returnValue;

    Sessione sessione;

    if((sessione = sessions.remove(channel)) == null)
    {
      returnValue = Op.UnknownSession;
    }
    else
    {
      returnValue = Op.SuccessfullyRemovedSession;

      String nomeDocumentoEdit = sessione.getNomeDocumentoEdit();
      if(nomeDocumentoEdit != null)
      {
        Documento documento = documents.get(nomeDocumentoEdit);
        int numSezioneEdit = sessione.getNumSezioneEdit();
        documento.getSezioni()[numSezioneEdit].endEdit();
      }
    }
    try
    {
      channel.close();
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }

    return returnValue;
  }

  private static Op handleLogin(String[] splitted, SocketChannel channel)
  {
    String nickname = splitted[1];
    String password = splitted[2];

    Sessione sessione = sessions.get(channel);

    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Logged ||
       sessione.getStato() == Sessione.Stato.Editing)
      return Op.AlreadyLoggedIn;

    Utente utente = users.get(nickname);
    if(utente == null)
      return Op.UserDoesNotExists;

    if(utente.getPassword().compareTo(password) != 0)
      return Op.WrongPassword;

    sessione.setUtente(utente);
    sessione.setStato(Sessione.Stato.Logged);

    return Op.SuccessfullyLoggedIn;
  }

  private static Op handleLogout(SocketChannel channel)
  {
    Sessione sessione = sessions.get(channel);
    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Started ||
       sessione.getStato() == Sessione.Stato.Editing)
      return Op.CannotLogout;

    sessione.setStato(Sessione.Stato.Started);

    return Op.SuccessfullyLoggedOut;
  }

  private static Op handleCreate(String[] splitted, SocketChannel channel)
  {
    String nomeDocumento = splitted[1];

    if(nomeDocumento == null)
      return Op.UsageError;

    int numSezioni;
    try
    {
      numSezioni = Integer.parseInt(splitted[2]);
    }
    catch(NumberFormatException e)
    {
      return Op.UsageError;
    }

    Sessione sessione = sessions.get(channel);

    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Started ||
       sessione.getStato() == Sessione.Stato.Editing)
      return Op.MustBeInLoggedState;

    Utente utente = sessione.getUtente();

    if(utente == null)
      return Op.UserDoesNotExists;

    Sezione[] newSezioni = new Sezione[numSezioni];
    for(int i = 0; i < numSezioni; i++)
    {
      newSezioni[i] = new Sezione(nomeDocumento + "_" + i, nomeDocumento, i);
    }
    Documento documento = new Documento(nomeDocumento, utente, newSezioni);

    //Creo una cartella avente come nome il nome del documento
    Path directoryPath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator + nomeDocumento);
    //Evito di sovrascrivere dei files involontariamente (lato Server)
    if(Files.exists(directoryPath))
      return Op.DirectoryAlreadyExists;

    if(documents.putIfAbsent(nomeDocumento, documento) != null)
      return Op.DocumentAlreadyExists;

    try
    {
      Files.createDirectories(directoryPath);
      Sezione[] sezioni = documento.getSezioni();
      for(Sezione sezione: sezioni)
      {
        Path filePath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator +
                nomeDocumento + File.separator + sezione.getNomeSezione() + ".txt");
        Files.createFile(filePath);
      }
    }
    catch(IOException e)
    {
      e.printStackTrace();
      return Op.Error;
    }

    return Op.SuccessfullyCreated;
  }

  private static Op handleShare(String[] splitted, SocketChannel channel)
  {
    String nomeDocumento = splitted[1];
    String nickname = splitted[2];

    Sessione sessione = sessions.get(channel);

    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Started ||
       sessione.getStato() == Sessione.Stato.Editing)
      return Op.MustBeInLoggedState;

    Utente utente = sessione.getUtente();

    if(utente == null)
      return Op.UserDoesNotExists;

    Documento documento = documents.get(nomeDocumento);
    if(documento == null)
      return Op.DocumentDoesNotExists;

    if(!documento.getCreatore().equals(utente))
      return Op.NotDocumentCreator;

    Utente utenteCollaboratore = users.get(nickname);

    if(utenteCollaboratore == null)
      return Op.UserDoesNotExists;

    if(documento.getCreatore().equals(utenteCollaboratore))
      return Op.CreatorCannotBeCollaborator;

    if(documento.isCollaboratore(utenteCollaboratore))
      return Op.AlreadyCollaborates;

    documento.addCollaboratore(utenteCollaboratore);
    if(utenteCollaboratore.getNotifica() == null)
    {
      utenteCollaboratore.setNotifica("[Notification] You can now edit the" +
              " following documents: " + nomeDocumento);
    }
    else
    {
      utenteCollaboratore.setNotifica(utenteCollaboratore.getNotifica() + ", " +
              nomeDocumento);
    }

    return Op.SuccessfullyShared;
  }

  private static Op handleShow(String[] splitted, SocketChannel channel)
  {
    String nomeDocumento = splitted[1];

    int numSezione = -1;
    if(splitted.length == 3)
    {
      try
      {
        numSezione = Integer.parseInt(splitted[2]);
      }
      catch(NumberFormatException e)
      {
        return Op.UsageError;
      }
    }

    Sessione sessione = sessions.get(channel);

    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Started ||
       sessione.getStato() == Sessione.Stato.Editing)
      return Op.MustBeInLoggedState;

    Utente utente = sessione.getUtente();

    if(utente == null)
      return Op.UserDoesNotExists;

    Documento documento = documents.get(nomeDocumento);
    if(documento == null)
      return Op.DocumentDoesNotExists;

    if(!documento.getCreatore().equals(utente) &&
       !documento.isCollaboratore(utente))
      return Op.NotDocumentCreatorNorCollaborator;

    if(numSezione != -1)
    {
      if(documento.getSezioni().length <= numSezione)
        return Op.SectionDoesNotExists;
    }

    return Op.SuccessfullyShown;
  }

  private static Op handleList(SocketChannel channel)
  {
    Sessione sessione = sessions.get(channel);
    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Started ||
       sessione.getStato() == Sessione.Stato.Editing)
      return Op.MustBeInLoggedState;

    Utente utente = sessione.getUtente();

    if(utente == null)
      return Op.UserDoesNotExists;

    return Op.SuccessfullyListed;
  }

  private static Op handleEdit(String[] splitted, SocketChannel channel)
  {
    String nomeDocumento = splitted[1];
    int numSezione;
    try
    {
      numSezione = Integer.parseInt(splitted[2]);
    }
    catch(NumberFormatException e)
    {
      return Op.UsageError;
    }

    Sessione sessione = sessions.get(channel);

    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Started ||
       sessione.getStato() == Sessione.Stato.Editing)
      return Op.MustBeInLoggedState;

    Utente utente = sessione.getUtente();

    if(utente == null)
      return Op.UserDoesNotExists;

    Documento documento = documents.get(nomeDocumento);
    if(documento == null)
      return Op.DocumentDoesNotExists;

    if(!documento.getCreatore().equals(utente) &&
       !documento.isCollaboratore((utente)))
      return Op.NotDocumentCreatorNorCollaborator;

    if(documento.getSezioni().length <= numSezione)
      return Op.SectionDoesNotExists;

    Sezione sezione = documento.getSezioni()[numSezione];
    if(sezione.getUserEditing() != null)
      return Op.SectionUnderModification;

    sezione.edit(utente);

    sessione.setNomeDocumentoEdit(nomeDocumento);
    sessione.setNumSezioneEdit(numSezione);
    sessione.setStato(Sessione.Stato.Editing);

    return Op.SuccessfullyStartedEditing;
  }

  private static Op handleEndEdit(String[] splitted, SocketChannel channel)
  {
    String nomeDocumento = splitted[1];
    int numSezione;
    try
    {
      numSezione = Integer.parseInt(splitted[2]);
    }
    catch(NumberFormatException e)
    {
      return Op.Error;
    }

    Sessione sessione = sessions.get(channel);

    if(sessione == null)
      return Op.UnknownSession;

    if(sessione.getStato() == Sessione.Stato.Started ||
       sessione.getStato() == Sessione.Stato.Logged)
      return Op.MustBeInEditingState;

    Utente utente = sessione.getUtente();

    if(utente == null)
      return Op.UserDoesNotExists;

    Documento documento = documents.get(nomeDocumento);
    if(documento == null)
      return Op.DocumentDoesNotExists;

    if(!documento.getCreatore().equals(utente) &&
            !documento.isCollaboratore((utente)))
      return Op.NotDocumentCreatorNorCollaborator;

    if(documento.getSezioni().length <= numSezione)
      return Op.SectionDoesNotExists;

    Sezione sezione = documento.getSezioni()[numSezione];

    if(sezione.getUserEditing() == null)
      return Op.NotEditingThisSection;

    if(!sezione.getUserEditing().equals(utente))
      return Op.NotEditingThisSection;

    if(sessione.getNomeDocumentoEdit().compareTo(nomeDocumento) != 0)
      return Op.NotEditingThisSection;

    if(sessione.getNumSezioneEdit() != numSezione)
      return Op.NotEditingThisSection;

    sezione.endEdit();

    //Se in quel documento nessuno sta più editando alcuna sezione
    //allora ricicla l'indirizzo IP Multicast
    Sezione[] sezioni = documento.getSezioni();
    boolean recycleIP = true;
    for(Sezione sez : sezioni)
    {
      if(sez.getUserEditing() != null)
        recycleIP = false;
    }
    if(recycleIP)
      documento.setMulticastAddress(null);

    sessione.setNomeDocumentoEdit(null);
    sessione.setNumSezioneEdit(-1);
    sessione.setStato(Sessione.Stato.Logged);

    return Op.SuccessfullyEndedEditing;
  }

  public static void main(String[] args)
  {
    String host;
    int serverPort;
    int rmiPort;

    try
    {
      if(args.length == 0)
      {
        //Non ho passato argomenti
        host = DEFAULT_HOST;
        serverPort = DEFAULT_PORT;
        rmiPort = DEFAULT_RMI_PORT;
        println("No arguments specified, using default host " + host + ", " +
                "default port number " + DEFAULT_PORT +
                " and default RMI port number " + DEFAULT_RMI_PORT);
      }
      else if(args.length == 1)
      {
        //Ho passato solo l'host
        host = args[0];
        serverPort = DEFAULT_PORT;
        rmiPort = DEFAULT_RMI_PORT;
        println("Listening to chosen host " + host + ", default port number " +
                serverPort + " and " + "default RMI port number " + DEFAULT_RMI_PORT);
      } else if(args.length == 2)
      {
        //Ho passato l'host e il numero di porta
        host = args[0];
        serverPort = Integer.parseInt(args[1]);
        rmiPort = DEFAULT_RMI_PORT;
        println("Listening to chosen host " + host + ", chosen port number " +
                serverPort + " and default " + "RMI port number " + rmiPort);
      }
      else if(args.length == 3)
      {
        //Ho passato l'host, il numero di porta e il numero di porta RMI
        host = args[0];
        serverPort = Integer.parseInt(args[1]);
        rmiPort = Integer.parseInt(args[2]);
        println("Listening to chosen host " + host + ", chosen port number " +
                serverPort + " and chosen RMI port number " + rmiPort);
      }
      else
      {
        printErr("Usage: java Server [port_number] [RMI_port_number]");
        return;
      }
    }
    catch(NumberFormatException e)
    {
      printErr("Usage: java Server [port_number] [RMI_port_number]");
      return;
    }

    //Registrazione RMI
    RegUtenteImplementation object = new RegUtenteImplementation(users);
    RegUtenteInterface stub = null;
    Registry registry = null;
    try
    {
      System.setProperty("java.rmi.server.hostname", host);
      stub = (RegUtenteInterface) UnicastRemoteObject.exportObject(object, rmiPort);
      registry = LocateRegistry.createRegistry(rmiPort);
      registry.bind("RegUtente", stub);
    }
    catch(ExportException e1)
    {
      printErr(e1.toString());
      printErr("Errore con la Bind, probabilmente la porta RMI e' (ancora) in " +
              " uso da un'altra applicazione");
      printErr("e' consigliato cambiare porta RMI: " +
              "java Server [port_number] [RMI_port_number]");
      System.exit(1);
    }
    catch(Exception e2)
    {
      printErr("exception: " + e2.toString());
      e2.printStackTrace();
      System.exit(1);
    }

    ServerSocketChannel serverChannel;
    Selector selector = null;
    try
    {
      serverChannel = ServerSocketChannel.open();
      ServerSocket ss = serverChannel.socket();
      InetSocketAddress address = new InetSocketAddress(serverPort);
      ss.bind(address);
      serverChannel.configureBlocking(false);
      selector = Selector.open();
      serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }
    catch(IOException e)
    {
      e.printStackTrace();
      printErr("Errore con la Bind, probabilmente la porta e' (ancora) in " +
              " uso da un'altra applicazione");
      printErr("e' consigliato cambiare porta: " +
              "java Server [port_number] [RMI_port_number]");
      System.exit(1);
    }

    Op OP = null;

    while(true)
    {
      try
      {
        assert selector != null;
        selector.select();
      }
      catch(IOException | NullPointerException e)
      {
        e.printStackTrace();
        break;
      }
      Set<SelectionKey> readyKeys = selector.selectedKeys();
      Iterator<SelectionKey> iterator = readyKeys.iterator();
      while(iterator.hasNext())
      {
        SelectionKey key = iterator.next();
        iterator.remove();
        try
        {
          if(key.isValid() && key.isAcceptable())
          {
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel client = server.accept();
            println("New connection from Client IP " + client.getRemoteAddress());
            client.configureBlocking(false);
            client.setOption(StandardSocketOptions.SO_KEEPALIVE, true);

            Attachment attachments = new Attachment(
                    Long.BYTES,
                    ByteBuffer.allocate(Long.BYTES),
                    Step.WaitingForMessageSize,
                    null,
                    null,
                    null
            );

            client.register(selector, SelectionKey.OP_READ, attachments);

            //Crea una nuova sessione per il client
            sessions.put(client, new Sessione());
          }
          if(key.isValid() && key.isReadable())
          {
            SocketChannel channel = (SocketChannel)key.channel();

            Attachment attachments = (Attachment) key.attachment();
            int remainingBytes     =  attachments.getRemainingBytes();
            ByteBuffer buffer      =  attachments.getBuffer();
            Step step              =  attachments.getStep();
            String[] parameters    =  attachments.getParameters();
            String list;

            int res;

            switch(step)
            {
              case WaitingForMessageSize :
                res = channel.read(buffer);
                println("WaitingForMessageSize Read " + res + " bytes");

                if(res < 0)
                {
                  String clientRemoteAddress = channel.getRemoteAddress().toString();
                  Op result = handleClosedConnection(channel);
                  if(result == Op.SuccessfullyRemovedSession)
                    println("Lost connection from Client IP " + clientRemoteAddress);
                  else
                    Op.print(result);
                }
                else
                {
                  if(res < remainingBytes)
                  {
                    //Non abbiamo ancora tutta la dimensione del messaggio
                    remainingBytes -= res;
                    attachments.setRemainingBytes(remainingBytes);
                  }
                  else
                  {
                    //Abbiamo la dimensione del messaggio nel buffer
                    //Ora bisogna scaricare il messaggio
                    buffer.flip();
                    int messageSize = new Long(buffer.getLong()).intValue();
                    attachments.setRemainingBytes(messageSize);
                    attachments.setBuffer(ByteBuffer.allocate(messageSize));
                    attachments.setStep(Step.WaitingForMessage);
                    attachments.setParameters(null);
                    attachments.setList(null);
                    channel.register(selector, SelectionKey.OP_READ, attachments);
                  }
                }
                break;

              case WaitingForMessage :
                try
                {
                  res = channel.read(buffer);
                  println("WaitingForMessage Read " + res + " bytes");

                  if(res < 0)
                  {
                    String clientRemoteAddress = channel.getRemoteAddress().toString();
                    Op result = handleClosedConnection(channel);
                    if(result == Op.SuccessfullyRemovedSession)
                      println("Lost connection from Client IP " + clientRemoteAddress);
                    else
                      Op.print(result);
                  }
                  else
                  {
                    if(res < remainingBytes)
                    {
                      //Non abbiamo ancora ricevuto il messaggio per intero
                      remainingBytes -= res;
                      attachments.setRemainingBytes(remainingBytes);
                    }
                    else
                    {
                      //Abbiamo il messaggio
                      //Bisogna elaborare l'operazione richiesta
                      buffer.flip();
                      String toSplit = new String(buffer.array(), 0,
                              buffer.array().length, StandardCharsets.ISO_8859_1);
                      String[] splitted = toSplit.split(DEFAULT_DELIMITER);
                      Op requestedOperation = Op.valueOf(splitted[0]);
                      println("Requested Operation : " +
                              requestedOperation.toString() +
                              " from Client IP : " +
                              channel.socket().getInetAddress().getHostAddress() +
                              " port : " + channel.socket().getPort());
                      Op result;
                      println(toSplit);
                      switch(requestedOperation)
                      {
                        case Login :
                          result = handleLogin(splitted, channel);
                          break;

                        case Logout :
                          result = handleLogout(channel);
                          break;

                        case Create :
                          result = handleCreate(splitted, channel);
                          break;

                        case Share :
                          result = handleShare(splitted, channel);
                          break;

                        case Show :
                          result = handleShow(splitted, channel);
                          break;

                        case List :
                          result = handleList(channel);
                          break;

                        case Edit :
                          result = handleEdit(splitted, channel);
                          break;

                        case EndEdit :
                          result = handleEndEdit(splitted, channel);
                          break;

                        default:
                          result = Op.UsageError;
                          break;
                      }

                      //Aggiungo due slot ai parametri
                      String[] newSplitted = new String[splitted.length + 2];
                      System.arraycopy(splitted, 0, newSplitted,
                              0, splitted.length);
                      newSplitted[splitted.length] = result.toString();
                      newSplitted[splitted.length + 1] = null;

                      Sessione sessione = sessions.get(channel);
                      Utente utente = sessione.getUtente();

                      boolean gotNotification = false;
                      if(utente != null)
                      {
                        if(utente.getNotifica() != null)
                          gotNotification = true;
                      }

                      //Se ho una notifica, prima mando quella e poi mando
                      //L'esito dell'operazione richiesta
                      if(gotNotification)
                      {
                        String notifica = utente.getNotifica();
                        newSplitted[splitted.length + 1] = notifica;
                        utente.setNotifica(null);

                        result = Op.newNotification;

                        byte[] resultBytes = result.toString().getBytes(StandardCharsets.ISO_8859_1);
                        buffer = ByteBuffer.allocate(Long.BYTES);
                        buffer.putLong(resultBytes.length);
                        buffer.flip();
                        attachments.setRemainingBytes(buffer.array().length);
                        attachments.setBuffer(buffer);
                        attachments.setStep(Step.SendingNotificationOpSize);
                        attachments.setParameters(newSplitted);
                      }
                      else
                      {
                        byte[] resultBytes = result.toString().getBytes(StandardCharsets.ISO_8859_1);
                        buffer = ByteBuffer.allocate(Long.BYTES);
                        buffer.putLong(resultBytes.length);
                        buffer.flip();
                        attachments.setRemainingBytes(buffer.array().length);
                        attachments.setBuffer(buffer);
                        attachments.setStep(Step.SendingOutcomeSize);
                        attachments.setParameters(newSplitted);
                      }
                      channel.register(selector, SelectionKey.OP_WRITE, attachments);
                    }
                  }
                }
                catch(IOException e)
                {
                  e.printStackTrace();
                }

                break;

              case GettingSectionSize :
                res = channel.read(buffer);
                println("GettingSectionSize Read " + res + " bytes");

                if(res < 0)
                {
                  String clientRemoteAddress = channel.getRemoteAddress().toString();
                  Op result = handleClosedConnection(channel);
                  if(result == Op.SuccessfullyRemovedSession)
                    println("Lost connection from Client IP " + clientRemoteAddress);
                  else
                    Op.print(result);
                }
                else
                {
                  if(res < remainingBytes)
                  {
                    //Non abbiamo ancora tutta la dimensione della sezione
                    remainingBytes -= res;
                    attachments.setRemainingBytes(remainingBytes);
                  }
                  else
                  {
                    //Abbiamo la dimensione della sezione nel buffer
                    //Ora bisogna scaricare la sezione
                    buffer.flip();
                    int sectionSize = new Long(buffer.getLong()).intValue();
                    attachments.setRemainingBytes(sectionSize);
                    attachments.setBuffer(ByteBuffer.allocate(sectionSize));
                    attachments.setStep(Step.GettingSection);
                    attachments.setParameters(parameters); //Propagare i parametri
                    attachments.setList(null);
                    channel.register(selector, SelectionKey.OP_READ, attachments);
                  }
                }
                break;

              case GettingSection :
                try
                {
                  res = channel.read(buffer);
                  println("GettingSection Read " + res + " bytes");

                  if(res < 0)
                  {
                    String clientRemoteAddress = channel.getRemoteAddress().toString();
                    Op result = handleClosedConnection(channel);
                    if(result == Op.SuccessfullyRemovedSession)
                      println("Lost connection from Client IP " + clientRemoteAddress);
                    else
                      Op.print(result);
                  }
                  else
                  {
                    if(res < remainingBytes)
                    {
                      //Non abbiamo ancora ricevuto la sezione per intero
                      remainingBytes -= res;
                      attachments.setRemainingBytes(remainingBytes);
                    }
                    else
                    {
                      //Abbiamo la sezione
                      buffer.flip();
                      String nomeDocumento = parameters[1];
                      Documento documento = documents.get(nomeDocumento);
                      int numSezione = Integer.parseInt(parameters[2]);
                      Sezione sezione = documento.getSezioni()[numSezione];

                      Path filePath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator +
                              sezione.getNomeDocumento() + File.separator +
                              sezione.getNomeSezione() + ".txt");

                      Path directoryPath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator +
                              sezione.getNomeDocumento());

                      if (!Files.exists(directoryPath))
                        Files.createDirectories(directoryPath);

                      FileChannel fileChannel = FileChannel.open(filePath, EnumSet.of(
                              StandardOpenOption.CREATE,
                              StandardOpenOption.TRUNCATE_EXISTING,
                              StandardOpenOption.WRITE));

                      while(buffer.hasRemaining())
                      {
                        println("Written " + fileChannel.write(buffer) + " bytes to file");
                      }

                      //Abbiamo memorizzato la sezione
                      //torno nello stato WaitingForMessageSize
                      attachments.setRemainingBytes(Long.BYTES);
                      attachments.setBuffer(ByteBuffer.allocate(Long.BYTES));
                      attachments.setStep(Step.WaitingForMessageSize);
                      attachments.setParameters(null);
                      attachments.setSections(null);
                      attachments.setList(null);

                      channel.register(selector, SelectionKey.OP_READ, attachments);
                    }
                  }
                }
                catch(IOException e)
                {
                  e.printStackTrace();
                }

                break;

              default :
                println("nessuna delle precedenti (read)");
                break;
            }
          }
          if(key.isValid() && key.isWritable())
          {
            SocketChannel channel = (SocketChannel)key.channel();

            Attachment attachments = (Attachment) key.attachment();
            int remainingBytes     =  attachments.getRemainingBytes();
            ByteBuffer buffer      =  attachments.getBuffer();
            Step step              =  attachments.getStep();
            String list;

            int res;
            LinkedList<Sezione> sezioni;

            switch(step)
            {
              case SendingNotificationOpSize :
                res = channel.write(buffer);

                println("SendingNotificationOpSize Written " + res + " bytes");
                if(res < remainingBytes)
                {
                  //Non abbiamo finito di inviare la dimensione dell'Op di notifica
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato la dimensione della notifica
                  //Invio l'operazione di notifica
                  String[] parameters = attachments.getParameters();
                  Op result = Op.newNotification;

                  buffer = ByteBuffer.wrap(result.toString().getBytes());
                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingNotificationOp);
                  attachments.setParameters(parameters);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }
                break;


              case SendingNotificationOp :
                res = channel.write(buffer);

                println("SendingNotificationOp Written " + res + " bytes");
                if(res < remainingBytes)
                {
                  //Non abbiamo finito di inviare l'Op di notifica
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato l'Op di notifica
                  //Invio la dimensione della notifica
                  String[] parameters = attachments.getParameters();
                  String notifica = parameters[parameters.length - 1];

                  byte[] notifyBytes = notifica.getBytes(StandardCharsets.ISO_8859_1);
                  buffer = ByteBuffer.allocate(Long.BYTES);
                  buffer.putLong(notifyBytes.length);
                  buffer.flip();
                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingNotificationSize);
                  attachments.setParameters(parameters);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }
                break;

              case SendingNotificationSize :
                res = channel.write(buffer);

                println("SendingNotificationSize Written " + res + " bytes");
                if(res < remainingBytes)
                {
                  //Non abbiamo finito di inviare la dimensione di notifica
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato la dimensione della notifica
                  //Invio la notifica
                  String[] parameters = attachments.getParameters();
                  String notifica = parameters[parameters.length - 1];

                  buffer = ByteBuffer.wrap(notifica.getBytes());
                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingNotification);
                  attachments.setParameters(parameters);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }
                break;

              case SendingNotification :
                res = channel.write(buffer);

                println("SendingNotification Written " + res + " bytes");
                if(res < remainingBytes)
                {
                  //Non abbiamo finito di inviare la notifica
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato la notifica
                  //Invio l'esito dell'operazione
                  String[] parameters = attachments.getParameters();
                  Op result = Op.valueOf(parameters[parameters.length - 2]);

                  byte[] resultBytes = result.toString().getBytes(StandardCharsets.ISO_8859_1);
                  buffer = ByteBuffer.allocate(Long.BYTES);
                  buffer.putLong(resultBytes.length);
                  buffer.flip();
                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingOutcomeSize);
                  attachments.setParameters(parameters);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }
                break;

              case SendingOutcomeSize :
                res = channel.write(buffer);

                println("SendingOutcomeSize Written " + res + " bytes");
                if(res < remainingBytes)
                {
                  //Non abbiamo finito di inviare la dimensione dell'esito
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato la dimensione dell'esito
                  //Invio l'esito
                  String[] parameters = attachments.getParameters();
                  Op result = Op.valueOf(parameters[parameters.length - 2]);

                  buffer = ByteBuffer.wrap(result.toString().getBytes());
                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingOutcome);
                  attachments.setParameters(parameters);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }
                break;

              case SendingOutcome :
                res = channel.write(buffer);

                println("SendingOutcome Written " + res + " bytes");
                if(res < remainingBytes)
                {
                  //Non abbiamo finito ad inviare l'esito
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato l'esito
                  String[] parameters = attachments.getParameters();

                  Op requestedOperation = Op.valueOf(parameters[0]);
                  Op result = Op.valueOf(parameters[parameters.length - 2]);

                  if(requestedOperation == Op.Show && result == Op.SuccessfullyShown)
                  {
                    //Devo inviare il numero di sezioni
                    String nomeDocumento = parameters[1];
                    Documento documento = documents.get(nomeDocumento);
                    int numSezioni;

                    if(parameters.length == 3 + 2)
                    {
                      //singola sezione
                      int numSezione = Integer.parseInt(parameters[2]);
                      Sezione sezione = documento.getSezioni()[numSezione];
                      LinkedList<Sezione> sezioni_ = new LinkedList<>();
                      sezioni_.add(sezione);
                      attachments.setSections(sezioni_);
                      numSezioni = 1;
                    }
                    else
                    {
                      //tutte le sezioni
                      Sezione[] arraySezioni = documento.getSezioni();
                      LinkedList<Sezione> sezioni_ =
                              new LinkedList<>(Arrays.asList(arraySezioni));
                      numSezioni = sezioni_.size();
                      attachments.setSections(sezioni_);
                    }

                    buffer = ByteBuffer.allocate(Long.BYTES);
                    buffer.putLong(new Long(numSezioni).intValue());
                    buffer.flip();
                    attachments.setRemainingBytes(buffer.array().length);
                    attachments.setBuffer(buffer);
                    attachments.setStep(Step.SendingNumberOfSections);
                    attachments.setParameters(null);
                    attachments.setList(null);

                    channel.register(selector, SelectionKey.OP_WRITE, attachments);
                  }
                  else if(requestedOperation == Op.List && result == Op.SuccessfullyListed)
                  {
                    //Devo inviare la lista dei documenti in cui collabora e
                    //quelli creati da esso

                    //LIST TCP
                    //documentoX:creatoreX:collaboratore1_X: ... : collaboratoreN_X#
                    //documentoY:creatoreY:collaboratore1_Y: ... : collaboratoreM_Y#
                    // ...
                    //documentoZ:creatoreZ:collaboratore1_Z: ... : collaboratoreK_Z

                    Sessione sessione = sessions.get(channel);
                    Utente utente = sessione.getUtente();

                    StringJoiner joiner = new StringJoiner(DEFAULT_DELIMITER);

                    for(Documento documento : documents.values())
                    {
                      if(documento.getCreatore().equals(utente))
                      {
                        //È creatore di quel documento
                        StringJoiner interiorJoiner = new StringJoiner(DEFAULT_INTERIOR_DELIMITER);
                        interiorJoiner.add(documento.getNome()).add(utente.getNickname());
                        for(Utente collaboratore : documento.getCollaborators().values())
                        {
                          interiorJoiner.add(collaboratore.getNickname());
                        }
                        joiner.add(interiorJoiner.toString());
                      }
                      else
                      {
                        for(Utente collaboratore : documento.getCollaborators().values())
                        {
                          if(collaboratore.equals(utente))
                          {
                            //è collaboratore di quel documento
                            StringJoiner interiorJoiner = new StringJoiner(DEFAULT_INTERIOR_DELIMITER);
                            interiorJoiner.add(documento.getNome()).add(documento.getCreatore().getNickname());
                            for(Utente collaboratore_ : documento.getCollaborators().values())
                            {
                              interiorJoiner.add(collaboratore_.getNickname());
                            }
                            joiner.add(interiorJoiner.toString());
                          }
                        }
                      }
                    }

                    byte[] listBytes = joiner.toString().getBytes(StandardCharsets.ISO_8859_1);

                    buffer = ByteBuffer.allocate(Long.BYTES);
                    buffer.putLong(listBytes.length);
                    buffer.flip();

                    attachments.setRemainingBytes(buffer.array().length);
                    attachments.setBuffer(buffer);
                    attachments.setStep(Step.SendingListSize);
                    attachments.setParameters(null);
                    attachments.setList(joiner.toString());

                    channel.register(selector, SelectionKey.OP_WRITE, attachments);
                  }
                  else if(requestedOperation == Op.Edit && result == Op.SuccessfullyStartedEditing)
                  {
                    //Devo inviare una sezione, quindi sia la dimensione che la sezione stessa
                    String nomeDocumento = parameters[1];
                    Documento documento = documents.get(nomeDocumento);

                    if(documento.getMulticastAddress() == null)
                    {
                      //Indirizzo per reti private (Local-Scope Organization)
                      //239.0.0.0/8
                      boolean generateAnotherIP;
                      Long generatedIP;
                      do
                      {
                        int[] ipAddressInArray = new Random().ints(3,
                                0, 255).toArray();
                        //Trasforma IP a Long https://stackoverflow.com/a/53105157
                        long longIP = 0;
                        longIP += 239 * Math.pow(256, 3);
                        for (int i = 1; i <= 3; i++)
                        {
                          int power = 3 - i;
                          int ip = ipAddressInArray[i - 1];
                          longIP += ip * Math.pow(256, power);
                        }
                        generatedIP = longIP;
                        generateAnotherIP = false;
                        for(Documento doc : documents.values())
                        {
                          if(doc.getMulticastAddress() != null)
                          {
                            if(doc.getMulticastAddress().equals(generatedIP))
                              generateAnotherIP = true;
                          }
                        }
                      }while(generateAnotherIP);
                      documento.setMulticastAddress(generatedIP);
                    }

                    int numSezione = Integer.parseInt(parameters[2]);
                    Sezione sezione = documento.getSezioni()[numSezione];
                    LinkedList<Sezione> sezioni_ = new LinkedList<>();
                    sezioni_.add(sezione);
                    attachments.setSections(sezioni_);

                    int numSezioni = 1;

                    buffer = ByteBuffer.allocate(Long.BYTES);
                    buffer.putLong(numSezioni);
                    buffer.flip();

                    attachments.setRemainingBytes(buffer.array().length);
                    attachments.setBuffer(buffer);
                    attachments.setStep(Step.SendingNumberOfSections);
                    attachments.setParameters(null);
                    attachments.setList(null);

                    channel.register(selector, SelectionKey.OP_WRITE, attachments);
                  }
                  else if(requestedOperation == Op.EndEdit && result == Op.SuccessfullyEndedEditing)
                  {
                    //Devo ricevere una sezione, quindi sia la dimensione che
                    //la sezione stessa, quindi vado nello stato gettingSectionSize
                    attachments.setRemainingBytes(Long.BYTES);
                    attachments.setBuffer(ByteBuffer.allocate(Long.BYTES));
                    attachments.setStep(Step.GettingSectionSize);
                    attachments.setParameters(parameters); //PROPAGARE I PARAMETRI
                    attachments.setSections(null);
                    attachments.setList(null);

                    channel.register(selector, SelectionKey.OP_READ, attachments);
                  }
                  else
                  {
                    // torno nello stato WaitingForMessageSize
                    attachments.setRemainingBytes(Long.BYTES);
                    attachments.setBuffer(ByteBuffer.allocate(Long.BYTES));
                    attachments.setStep(Step.WaitingForMessageSize);
                    attachments.setParameters(null);
                    attachments.setSections(null);
                    attachments.setList(null);

                    channel.register(selector, SelectionKey.OP_READ, attachments);
                  }
                }
                break;

              case SendingNumberOfSections :
                res = channel.write(buffer);
                println("SendingNumberOfSections Written " + res + " bytes");
                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare il numero di sezioni
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato il numero di sezioni
                  //Prelevo una sezione dalla lista e
                  //invio la dimensione di questa
                  sezioni = attachments.getSections();
                  Sezione sezione = sezioni.element();
                  Path filePath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator +
                          sezione.getNomeDocumento() + File.separator +
                          sezione.getNomeSezione() + ".txt");
                  try
                  {
                    if(Files.notExists(filePath))
                    {
                      //la sezione non esiste nella directory
                      //creo un file vuoto e lo invio lo stesso
                      printErr("Filename " + sezione.getNomeSezione() + ".txt" +
                              " does not exists in the current working directory: " +
                              System.getProperty("user.dir"));
                      //Creo un file vuoto
                      Files.createFile(filePath);
                    }
                    FileChannel fileChannel = FileChannel.open(filePath);

                    long dimensioneFile = fileChannel.size();

                    buffer = ByteBuffer.allocate(Long.BYTES);
                    buffer.putLong(dimensioneFile);
                    buffer.flip();

                    attachments.setRemainingBytes(buffer.array().length);
                    attachments.setBuffer(buffer);
                    attachments.setStep(Step.SendingSectionSize);
                    attachments.setSections(sezioni); //Parametri

                    channel.register(selector, SelectionKey.OP_WRITE, attachments);
                    fileChannel.close();
                  }
                  catch(IOException e)
                  {
                    e.printStackTrace();
                  }
                }
                break;

              case SendingSectionSize :
                res = channel.write(buffer);
                println("SendingSectionSize Written " + res + " bytes");

                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare la dimensione della sezione
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato la dimensione della sezione
                  //Invio il numero identificativo della sezione
                  sezioni = attachments.getSections();

                  Sezione sezione = sezioni.element();
                  Path filePath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator +
                          sezione.getNomeDocumento() + File.separator +
                          sezione.getNomeSezione() + ".txt");
                  try
                  {
                    FileChannel fileChannel = FileChannel.open(filePath);
                    long numeroSezione = sezione.getNumeroSezione();

                    buffer = ByteBuffer.allocate(Long.BYTES);
                    buffer.putLong(numeroSezione);
                    buffer.flip();

                    attachments.setRemainingBytes(buffer.array().length);
                    attachments.setBuffer(buffer);
                    attachments.setStep(Step.SendingSectionNumber);
                    attachments.setSections(sezioni);

                    channel.register(selector, SelectionKey.OP_WRITE, attachments);
                    fileChannel.close();
                  }
                  catch(IOException e)
                  {
                    e.printStackTrace();
                  }
                }
                break;

              case SendingSectionNumber :
                res = channel.write(buffer);
                println("SendingSectionNumber Written " + res + " bytes");

                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare il numero identificativo di sezione
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato il numero identificativo di sezione
                  //Invia l'indirizzo Multicast

                  sezioni = attachments.getSections();
                  Sezione sezione = sezioni.element();
                  String nomeDocumento = sezione.getNomeDocumento();
                  Documento documento = documents.get(nomeDocumento);
                  Long multicastAddress = documento.getMulticastAddress();
                  buffer = ByteBuffer.allocate(Long.BYTES);
                  if(multicastAddress == null)
                    multicastAddress = 0L;
                  buffer.putLong(multicastAddress);
                  buffer.flip();

                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingMulticastAddress);
                  attachments.setSections(sezioni);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }

                break;

              case SendingMulticastAddress :
                res = channel.write(buffer);
                println("SendingMulticastAddress Written " + res + " bytes");

                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare l'indirizzo Multicast
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato l'indirizzo Multicast
                  //Invio lo stato di modifica sezione
                  sezioni = attachments.getSections();
                  Sezione sezione = sezioni.element();
                  long stato;
                  if(sezione.getUserEditing() == null)
                    stato = 0L;
                  else
                    stato = 1L;

                  buffer = ByteBuffer.allocate(Long.BYTES);
                  buffer.putLong(stato);
                  buffer.flip();
                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingSectionStatus);
                  attachments.setSections(sezioni);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }
                break;

              case SendingSectionStatus :
                res = channel.write(buffer);
                println("SendingSectionStatus Written " + res + " bytes");

                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare lo stato di modifica sezione
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato lo stato di modifica sezione
                  //Invio la sezione
                  sezioni = attachments.getSections();
                  Sezione sezione = sezioni.element();
                  Path filePath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator +
                          sezione.getNomeDocumento() + File.separator +
                          sezione.getNomeSezione() + ".txt");
                  try
                  {
                    FileChannel fileChannel = FileChannel.open(filePath);
                    long dimensioneFile = fileChannel.size();
                    //Decido di bufferizzare l'intero file
                    buffer = ByteBuffer.allocate(Math.toIntExact(dimensioneFile));

                    while(buffer.hasRemaining())
                    {
                      println("Read " + fileChannel.read(buffer) + " bytes from file");
                    }
                    buffer.flip();

                    //Il buffer è sufficientemente grande per contenere l'intero file
                    attachments.setRemainingBytes(buffer.array().length);
                    attachments.setBuffer(buffer);
                    attachments.setStep(Step.SendingSection);
                    attachments.setSections(sezioni);

                    channel.register(selector, SelectionKey.OP_WRITE, attachments);
                    fileChannel.close();
                  }
                  catch(IOException e)
                  {
                    e.printStackTrace();
                  }
                }
                break;

              case SendingSection :
                res = channel.write(buffer);
                println("SendingSection Written " + res + " bytes");

                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare la sezione
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato l'intera sezione
                  //Vedo se bisogna o meno inviare un'altra sezione
                  sezioni = attachments.getSections();
                  //Rimuovo la sezione perchè è stata appena inviata
                  sezioni.remove();
                  if(sezioni.size() > 0)
                  {
                    //Bisogna inviare un'altra sezione
                    Sezione sezione = sezioni.element();
                    //Prelevo una sezione dalla lista e
                    //invio la dimensione di questa
                    Path filePath = Paths.get(DEFAULT_PARENT_FOLDER + File.separator +
                            sezione.getNomeDocumento() + File.separator +
                            sezione.getNomeSezione() + ".txt");
                    try
                    {
                      if(Files.notExists(filePath))
                      {
                        //la sezione non esiste nella directory
                        //creo un file vuoto e lo invio lo stesso
                        printErr("Filename " + sezione.getNomeSezione() + ".txt" +
                                " does not exists in the current working directory: " +
                                System.getProperty("user.dir"));
                        //Creo un file vuoto
                        Files.createFile(filePath);
                      }
                      FileChannel fileChannel = FileChannel.open(filePath);
                      long dimensioneFile = fileChannel.size();

                      buffer = ByteBuffer.allocate(Long.BYTES);
                      buffer.putLong(dimensioneFile);
                      buffer.flip();

                      attachments.setRemainingBytes(buffer.array().length);
                      attachments.setBuffer(buffer);
                      attachments.setStep(Step.SendingSectionSize);
                      attachments.setSections(sezioni);

                      channel.register(selector, SelectionKey.OP_WRITE, attachments);
                      fileChannel.close();
                    }
                    catch(IOException e)
                    {
                      e.printStackTrace();
                    }
                  }
                  else
                  {
                    // ho finito di inviare sezioni,
                    // torno nello stato WaitingForMessageSize
                    attachments.setRemainingBytes(Long.BYTES);
                    attachments.setBuffer(ByteBuffer.allocate(Long.BYTES));
                    attachments.setStep(Step.WaitingForMessageSize);
                    attachments.setSections(null);
                    attachments.setList(null);

                    channel.register(selector, SelectionKey.OP_READ, attachments);
                  }
                }
                break;

              case SendingListSize :
                res = channel.write(buffer);
                println("SendingListSize Written " + res + " bytes");

                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare la dimensione della lista
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato la dimensione della lista
                  //Invio la lista
                  list = attachments.getList();
                  byte[] listBytes = list.getBytes(StandardCharsets.ISO_8859_1);
                  buffer = ByteBuffer.wrap(listBytes);

                  attachments.setRemainingBytes(buffer.array().length);
                  attachments.setBuffer(buffer);
                  attachments.setStep(Step.SendingList);
                  attachments.setSections(null);
                  attachments.setList(null);

                  channel.register(selector, SelectionKey.OP_WRITE, attachments);
                }
                break;


              case SendingList :
                res = channel.write(buffer);
                println("SendingList Written " + res + " bytes");

                if(res < remainingBytes)
                {
                  //Non abbiamo finito di mandare la lista
                  remainingBytes -= res;
                  attachments.setRemainingBytes(remainingBytes);
                }
                else
                {
                  //Abbiamo inviato la lista
                  //torno nello stato WaitingForMessageSize
                  attachments.setRemainingBytes(Long.BYTES);
                  attachments.setBuffer(ByteBuffer.allocate(Long.BYTES));
                  attachments.setStep(Step.WaitingForMessageSize);
                  attachments.setSections(null);
                  attachments.setList(null);

                  channel.register(selector, SelectionKey.OP_READ, attachments);
                }
                break;

              default :
                println("nessuna delle precedenti(write)");
                break;
            }

          }
        }
        catch(IOException e)
        {
          //Connessione chiusa dal client
          SocketChannel channel = (SocketChannel)key.channel();
          String clientRemoteAddress = null;
          try
          {
            clientRemoteAddress = channel.getRemoteAddress().toString();
          }
          catch(IOException e1)
          {
            e1.printStackTrace();
          }
          Op result = handleClosedConnection(channel);
          if(result == Op.SuccessfullyRemovedSession)
            println("Lost connection from Client IP " + clientRemoteAddress);
          else
            Op.print(result);

          key.cancel();
        }
      }
    }
  }
}
