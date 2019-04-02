class Sezione
{
  private Utente editingUser;
  private String nomeSezione;
  private long  numeroSezione;
  private String nomeDocumento;

  Sezione(String nomeSezione, String nomeDocumento, long numeroSezione)
  {
    this.editingUser = null;
    this.nomeSezione = nomeSezione;
    this.nomeDocumento = nomeDocumento;
    this.numeroSezione = numeroSezione;
  }

  Utente getUserEditing()
  {
    return editingUser;
  }

  void edit(Utente utente)
  {
    editingUser = utente;
  }

  void endEdit()
  {
    editingUser = null;
  }

  String getNomeSezione()
  {
    return nomeSezione;
  }

  String getNomeDocumento()
  {
    return nomeDocumento;
  }

  long getNumeroSezione()
  {
    return numeroSezione;
  }
}
