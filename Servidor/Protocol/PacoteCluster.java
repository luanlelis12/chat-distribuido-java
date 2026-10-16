/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 13/10/2026
* Ultima alteracao.: 15/10/2026
* Nome.............: PacoteCluster.java
* Funcao...........: Protocolo de mensagens internas entre os servidores de redundancia
*******************************************************************/
package Protocol;

import java.io.Serializable;

public class PacoteCluster implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum Tipo {
    SONDAGEM,          // Novo no verifica quem esta ativo na rede para auto-atribuir ID
    RESPOSTA_SONDAGEM, // Nos ativos informam seu ID e se sao lider
    HEARTBEAT,         // Lider envia periodicamente
    SYNC_ESTADO,       // Lider envia snapshot da memoria para os standbys
    SOLICITAR_ESTADO   // No Standby solicita snapshot atualizado da memoria ao Lider
  }

  private final Tipo tipo;
  private final int idRemetente;
  private final boolean lider;
  private final SyncPacote dadosSync;

  public PacoteCluster(Tipo tipo, int idRemetente, boolean lider, SyncPacote dadosSync) {
    this.tipo = tipo;
    this.idRemetente = idRemetente;
    this.lider = lider;
    this.dadosSync = dadosSync;
  }

  public Tipo getTipo() {
    return tipo;
  }

  public int getIdRemetente() {
    return idRemetente;
  }

  public boolean isLider() {
    return lider;
  }

  public SyncPacote getDadosSync() {
    return dadosSync;
  }

  @Override
  public String toString() {
    return "PacoteCluster{" +
        "tipo=" + tipo +
        ", idRemetente=" + idRemetente +
        ", lider=" + lider +
        '}';
  }
}

