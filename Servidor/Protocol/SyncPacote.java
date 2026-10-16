/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 13/10/2026
* Ultima alteracao.: 15/10/2026
* Nome.............: SyncPacote.java
* Funcao...........: Encapsular snapshot de estado da memoria para replicacao entre servidores
*******************************************************************/
package Protocol;

import Model.Usuario;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SyncPacote implements Serializable {
  private static final long serialVersionUID = 1L;

  private final int idOrigem;
  private final Map<String, Usuario> usuariosOnline;
  private final Map<String, ArrayList<Usuario>> grupos;

  public SyncPacote(int idOrigem, Map<String, Usuario> usuariosOnline, Map<String, ArrayList<Usuario>> grupos) {
    this.idOrigem = idOrigem;
    // Copia defensiva para isolamento e integridade durante a serializacao
    this.usuariosOnline = new ConcurrentHashMap<>(usuariosOnline);
    this.grupos = new ConcurrentHashMap<>();
    for (Map.Entry<String, ArrayList<Usuario>> entry : grupos.entrySet()) {
      this.grupos.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
  }

  public int getIdOrigem() {
    return idOrigem;
  }

  public Map<String, Usuario> getUsuariosOnline() {
    return usuariosOnline;
  }

  public Map<String, ArrayList<Usuario>> getGrupos() {
    return grupos;
  }
}

