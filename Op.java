public enum Op
{
  SuccessfullyRegistered,     // Registrazione avvenuta con successo
  SuccessfullyLoggedIn,       // Login avvenuto con successo
  SuccessfullyLoggedOut,      // Logout avvenuto con successo
  SuccessfullyCreated,        // Creazione documento avvenuta con successo
  SuccessfullyShared,         // Condivisione documento avvenuta con successo
  SuccessfullyShown,          // Visualizzazione avvenuta con successo
  SuccessfullyRemovedSession, // Sessione rimossa con successo
  SuccessfullyReceivedSections,// Tutte le sezioni richieste sono state ricevute
                               // con successo
  SuccessfullyReceivedList,   // Lista ricevuta con successo
  SuccessfullyListed,         // Lista visualizzata con successo
  SuccessfullyStartedEditing, // Inizio editing sezione avvenuto con successo
  SuccessfullyEndedEditing,   // Fine editing sezione avvenuto con successo
  SuccessfullySentSection,    // Invio sezione avvenuta con successo
  SuccessfullySentMessage,    // Invio messaggio avvenuto con successo
  SuccessfullyReceivedMessage,// Ricezione messaggio avvenuta con successo
  SuccessfullyShownHelp,      // Messaggio di Help visualizzato con successo
  SuccessfullyPrintedEditing,   // Messaggio di sezione modificata attualmente
                              // visualizzato con successo
  WrongPassword,              // Password non corretta
  UserDoesNotExists,          // L'utente non esiste
  NicknameAlreadyExists,      // Esiste già l'utente
  DocumentAlreadyExists,      // Esiste già il documento
  DirectoryAlreadyExists,     // Esiste già la cartella
  NotDocumentCreator,         // L'utente non è il creatore del documento
  NotDocumentCreatorNorCollaborator, // L'utente non è né il creatore del
                                     // documento, né un collaboratore
  SectionUnderModification,   // La sezione è attualmente sotto modifica
  Login,                      // Operazione di Login TCP
  Logout,                     // Operazione di Logout TCP
  Create,                     // Operazione di creazione di un documento
  Share,                      // Operazione di condivisione di un documento
  Show,                       // Operazione di visualizzazione di una sezione
                              // o dell'intero documento
  List,                       // Operazione di visualizzazione lista documenti
                              // con relativi creatori e collaboratori
  Edit,                       // Operazione di richiesta modifica di una sezione
  EndEdit,                    // Operazione di richiesta fine modifica di una
                              // sezione
  Error,                      // Errore non ben specificato
  UsageError,                 // Passaggio parametri sbagliato
  ClosedConnection,           // Il server non È più raggiungibile
  UnknownSession,             // Sessione inesistente
  AlreadyLoggedIn,            // L'utente è già loggato
  CannotLogout,               // L'utente è nello stato Started o Editing
  SuccessfullySent,           // Richiesta inviata con successo
  MustBeInStartedState,       // Bisogna essere nello stato Started per eseguire
                              // il comando
  MustBeInLoggedState,        // Bisogna essere nello stato Logged per eseguire
                              // il comando
  MustBeInEditingState,       // Bisogna essere nello stato Editing per eseguire
                              // il comando
  DocumentDoesNotExists,      // Il documento non esiste
  SectionDoesNotExists,       // La sezione non esiste
  AlreadyCollaborates,        // L'utente già collabora alla modifica del
                              // documento
  CreatorCannotBeCollaborator,// Il creatore del documento non può essere
                              // collaboratore
  NotEditingThisSection,      // Non si sta attualmente editando questa sezione
  newNotification;            // Nuova notifica

  static void printErr(Object o)
  {
    System.err.println("[Error] " + o);
  }

  static void print(Op op)
  {
    switch(op)
    {
      case WrongPassword :
        printErr("Password non corretta");
        break;
      case UserDoesNotExists :
        printErr("L'utente specificato non esiste");
        break;
      case NicknameAlreadyExists :
        printErr("Esiste gia' un utente con questo nickname");
        break;
      case DocumentAlreadyExists :
        printErr("Esiste gia' un documento con questo nome");
        break;
      case DirectoryAlreadyExists :
        printErr("Esiste gia' una directory con questo nome");
        break;
      case NotDocumentCreator :
        printErr("Privilegi insufficienti: non si e' creatori del documento");
        break;
      case NotDocumentCreatorNorCollaborator :
        printErr("Privilegi insufficienti: non si e' creatori ne' collaboratori" +
                " del documento");
        break;
      case SectionUnderModification :
        printErr("La sezione e' attualmente sotto modifica");
        break;
      case Error :
        printErr("Errore non ben specificato");
        break;
      case UsageError :
        printErr("Errore di utilizzo del comando");
        break;
      case ClosedConnection :
        printErr("host non piu' raggiungibile");
        break;
      case UnknownSession :
        printErr("Sessione inesistente");
        break;
      case AlreadyLoggedIn :
        printErr("Login gia' effettuato");
        break;
      case CannotLogout :
        printErr("Impossibile effettuare il logout (non si e' nello stato " +
                " Started o Editing)");
        break;
      case MustBeInStartedState :
        printErr("Bisogna essere nello stato Started per eseguire il comando");
        break;
      case MustBeInLoggedState :
        printErr("Bisogna essere nello stato Logged per eseguire il comando");
        break;
      case MustBeInEditingState :
        printErr("Bisogna essere nello stato Editing per eseguire il comando");
        break;
      case DocumentDoesNotExists :
        printErr("Il documento specificato non esiste");
        break;
      case SectionDoesNotExists :
        printErr("La sezione specificata non esiste ");
        break;
      case AlreadyCollaborates :
        printErr("L'utente specificato collabora gia' alla modifica del" +
                " documento");
        break;
      case CreatorCannotBeCollaborator :
        printErr("Un creatore non puo' essere anche collaboratore");
        break;
      case NotEditingThisSection :
        printErr("Non si sta attualmente modificando questa sezione");
        break;
      default:
        printErr("Errore non codificato");
        break;
    }
  }

}
