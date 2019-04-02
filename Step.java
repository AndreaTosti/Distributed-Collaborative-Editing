public enum Step
{
  WaitingForMessageSize,     // Attesa ricezione dimensione del messaggio
  WaitingForMessage,         // Attesa ricezione messaggio
  SendingNotificationOpSize, // Invio dimensione della Op di notifica
  SendingNotificationOp,     // Invio della Op di notifica
  SendingNotificationSize,   // Invio dimensione della notifica
  SendingNotification,       // Invio della notifica
  SendingOutcome,            // Invio dell'esito
  SendingOutcomeSize,        // Invio dimensione dell'esito
  SendingMulticastAddress,   // Invio indirizzo multicast
  SendingNumberOfSections,   // Invio del numero delle sezioni
  SendingSection,            // Invio di una sezione
  SendingSectionSize,        // Invio dimensione di una sezione
  SendingSectionNumber,      // Invio numero identificativo di sezione
  SendingSectionStatus,      // Invio un numero che è 1 se la sezione in quel
                             // momento viene modificata, 0 altrimenti
  SendingListSize,           // Invio dimensione della lista
  SendingList,               // Invio della lista
  GettingSectionSize,        // Ricezione della dimensione di una sezione
  GettingSection,            // Ricezione di una sezione
}