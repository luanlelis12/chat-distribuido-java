/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 13/10/2026
* Ultima alteracao.: 16/10/2026
* Nome.............: GerenteRedundancia.java
* Funcao...........: Implementar algoritmo Bully (Hierarquia Fixa), Heartbeats e Replicacao de Estado
*******************************************************************/
package Model;

import Protocol.PacoteCluster;
import Protocol.SyncPacote;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GerenteRedundancia {
  private static final String IP_MULTICAST = "230.0.0.1";
  private static final int PORTA_CLUSTER = 9999;
  private static final int INTERVALO_HEARTBEAT_MS = 1200;
  private static final int TIMEOUT_LIDER_MS = 3500;
  private static final int JANELA_SONDAGEM_MS = 1000;

  private final Servidor servidor;
  private int id = -1;
  private MulticastSocket socketCluster;
  private InetAddress grupoMulticast;
  private volatile boolean executando = true;
  private volatile long ultimoHeartbeatRecebido = System.currentTimeMillis();
  private final Set<Integer> idsConhecidos = ConcurrentHashMap.newKeySet();
  private final ConcurrentHashMap<Integer, InetAddress> ipsLideresConjunto = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Integer, SyncPacote> pacotesSyncPendentes = new ConcurrentHashMap<>();
  private final Object lockEnvio = new Object();
  private final Object lockSincronizacao = new Object();
  private volatile boolean estadoConjuntoRecebido = false;
  private volatile boolean estadoGlobalRecebido = false;
  private volatile int idLiderAtual = -1;

  public GerenteRedundancia(Servidor servidor) {
    this.servidor = servidor;
  }

  public int getId() {
    return id;
  }

  /*
   * Metodo: obterIpLiderConjunto
   * Funcao: Retorna o ultimo InetAddress conhecido do Lider ativo do conjunto informado (1, 2 ou 3)
   */
  public InetAddress obterIpLiderConjunto(int conjuntoAlvo) {
    return ipsLideresConjunto.get(conjuntoAlvo);
  }

  /*
   * Metodo: mesmoConjunto
   * Funcao: Verifica se outro servidor pertence a mesma particao (conjunto 1, 2
   * ou 3) deste servidor
   */
  private boolean mesmoConjunto(int outroId) {
    if (this.id <= 0 || outroId <= 0) {
      return false;
    } // fim do if
    return Servidor.calcularConjuntoPorId(this.id) == Servidor.calcularConjuntoPorId(outroId);
  } // fim do metodo mesmoConjunto

  /*
   * Metodo: start
   * Funcao: Inicializa socket multicast, determina ID e dispara as threads de
   * controle
   */
  public void start() {
    try {
      socketCluster = new MulticastSocket(null);
      socketCluster.setReuseAddress(true);
      socketCluster.bind(new InetSocketAddress(PORTA_CLUSTER));
      grupoMulticast = InetAddress.getByName(IP_MULTICAST);
      socketCluster.joinGroup(grupoMulticast);

      // Inicia thread de escuta do cluster
      new Thread(this::escutarCluster, "Thread-Cluster-Escuta").start();

      // Fase de definicao do ID (Forcado vs Auto-Descoberta) e sincronizacao de
      // estado
      determinarId();

      // Inicia threads de envio periodico de Heartbeat e Watchdog
      new Thread(this::loopHeartbeat, "Thread-Cluster-Heartbeat").start();
      new Thread(this::loopWatchdog, "Thread-Cluster-Watchdog").start();

    } catch (Exception e) {
      System.err.println("[REDUNDANCIA-ERRO] Falha critica ao iniciar modulo de redundancia: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo start

  /*
   * Metodo: determinarId
   * Funcao: Define o ID fixado ou descobre o menor ID disponivel via sondagem na
   * rede, garantindo a obtencao previa do estado da memoria antes de assumir a
   * lideranca.
   */
  private void determinarId() {
    // Se o ID foi passado por parametro, ja configura imediatamente antes da sondagem
    // para que as respostas de SYNC_ESTADO durante a janela ja reconhecam o mesmo conjunto!
    if (servidor.getIdForcado() != null) {
      this.id = servidor.getIdForcado();
      servidor.setIdServidor(this.id);
      idsConhecidos.add(this.id);
      System.out.println("[REDUNDANCIA] ID fixado manualmente por parametro: " + this.id);
    }

    System.out.println("[REDUNDANCIA] Sondando cluster para mapear os outros nos ativos...");
    enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SONDAGEM, this.id, false, null));

    try {
      Thread.sleep(JANELA_SONDAGEM_MS);
    } catch (InterruptedException ignored) {
    } // fim do try-catch

    if (servidor.getIdForcado() == null) {
      // Encontra o menor ID positivo (1, 2, 3...) ainda nao utilizado no cluster
      int menorId = 1;
      while (idsConhecidos.contains(menorId)) {
        menorId++;
      } // fim do while
      this.id = menorId;
      servidor.setIdServidor(this.id);
      idsConhecidos.add(this.id);
      System.out.println("[REDUNDANCIA] Auto-atribuicao finalizada! ID atribuido: " + this.id);
    } // fim do if

    // Aplica eventuais snapshots recebidos durante a janela de sondagem (quando id ainda era -1)
    if (!pacotesSyncPendentes.isEmpty()) {
      for (SyncPacote pendente : pacotesSyncPendentes.values()) {
        if (pendente.getConjuntoOrigem() != servidor.getConjunto()) {
          servidor.aplicarSincronizacao(pendente);
          estadoGlobalRecebido = true;
        }
      }
      SyncPacote pendenteMesmoConj = pacotesSyncPendentes.get(servidor.getConjunto());
      if (pendenteMesmoConj != null) {
        servidor.aplicarSincronizacao(pendenteMesmoConj);
        estadoGlobalRecebido = true;
        estadoConjuntoRecebido = true;
      }
      pacotesSyncPendentes.clear();
    }

    // Avalia papel inicial no seu conjunto (Bully por Particao: se nao ha nenhum ID
    // menor do mesmo conjunto ativo, sou o lider!)
    boolean temMenorAtivoNoConjunto = false;
    boolean haOutrosNosNoMesmoConjunto = false;
    boolean haOutrosNosNaRede = false;
    for (int outroId : idsConhecidos) {
      if (outroId != this.id) {
        haOutrosNosNaRede = true;
        if (mesmoConjunto(outroId)) {
          haOutrosNosNoMesmoConjunto = true;
          if (outroId < this.id) {
            temMenorAtivoNoConjunto = true;
          } // fim do if
        } // fim do if
      } // fim do if
    } // fim do for

    if (!temMenorAtivoNoConjunto) {
      // Sou o servidor com maior autoridade (menor ID) dentro do meu Conjunto
      if (haOutrosNosNoMesmoConjunto) {
        // Ha outro servidor do MESMO conjunto ativo (ex: ID 6 era lider provisorio e ID 3 entrou).
        // Garantimos que recebemos especificamente o estado do nosso conjunto (grupos + usuarios) antes de assumir!
        if (!estadoConjuntoRecebido) {
          System.out.println("[REDUNDANCIA] Detectado outro servidor no Conjunto "
              + servidor.getConjunto() + " " + servidor.getFaixaLetras() + ".");
          System.out.println("[REDUNDANCIA] Solicitando transferencia de grupos e usuarios antes de assumir a lideranca...");
          pedirEstadoAoCluster();
          aguardarSincronizacao(2500, true);
        } // fim do if
      } else if (haOutrosNosNaRede) {
        // Primeiro servidor deste conjunto, mas ha outros conjuntos ativos na rede: sincroniza usuariosOnline
        if (!estadoGlobalRecebido) {
          System.out.println("[REDUNDANCIA] Detectados outros conjuntos ativos. Sincronizando lista global de usuarios...");
          pedirEstadoAoCluster();
          aguardarSincronizacao(1200, false);
        }
        estadoConjuntoRecebido = true;
      } else {
        // Primeiro servidor a subir em toda a rede: estado inicial limpo e legitimo
        estadoConjuntoRecebido = true;
        estadoGlobalRecebido = true;
      } // fim do if

      // Envia pulso de preempcao para qualquer lider provisorio do mesmo conjunto
      // liberar as portas e enviar seu snapshot final
      enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
      try {
        Thread.sleep(500); // Pausa graciosa para o lider anterior transferir estado e fechar os sockets
      } catch (InterruptedException ignored) {
      } // fim do try-catch
      servidor.promoverParaLider();
      enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
    } else {
      this.ultimoHeartbeatRecebido = System.currentTimeMillis();
      System.out.println("[REDUNDANCIA] Servidor (ID " + this.id + " | Conjunto "
          + servidor.getConjunto() + " " + servidor.getFaixaLetras() + ") operando em modo STANDBY.");
      if (!estadoConjuntoRecebido) {
        pedirEstadoAoCluster();
      } // fim do if
    } // fim do if
  } // fim do metodo determinarId

  /*
   * Metodo: pedirEstadoAoCluster
   * Funcao: Solicita snapshot completo da memoria do cluster ao Lider atual
   */
  public void pedirEstadoAoCluster() {
    System.out.println("[SYNC] Solicitando snapshot atualizado da memoria ao cluster...");
    enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SOLICITAR_ESTADO, this.id, false, null));
  } // fim do metodo pedirEstadoAoCluster

  /*
   * Metodo: aguardarSincronizacao
   * Funcao: Bloqueia ate receber o snapshot de estado (do conjunto ou global) ou ate atingir timeout
   */
  private void aguardarSincronizacao(long timeoutMs, boolean exigirMesmoConjunto) {
    synchronized (lockSincronizacao) {
      long limite = System.currentTimeMillis() + timeoutMs;
      while (!(exigirMesmoConjunto ? estadoConjuntoRecebido : estadoGlobalRecebido)
          && System.currentTimeMillis() < limite) {
        long restante = limite - System.currentTimeMillis();
        if (restante <= 0) {
          break;
        } // fim do if
        try {
          lockSincronizacao.wait(restante);
        } catch (InterruptedException ignored) {
        } // fim do try-catch
      } // fim do while
    } // fim do synchronized
    boolean recebido = exigirMesmoConjunto ? estadoConjuntoRecebido : estadoGlobalRecebido;
    if (recebido) {
      System.out.println("[SYNC] Estado inicial sincronizado com sucesso antes da promocao!");
    } else {
      System.out.println("[SYNC-AVISO] Timeout aguardando sincronizacao do cluster. Prosseguindo...");
    } // fim do if
  } // fim do metodo aguardarSincronizacao

  /*
   * Metodo: escutarCluster
   * Funcao: Recebe mensagens multicast de outros servidores e executa as acoes do
   * Bully
   */
  private void escutarCluster() {
    byte[] buffer = new byte[65507];
    while (executando) {
      try {
        DatagramPacket pacoteRecebido = new DatagramPacket(buffer, buffer.length);
        socketCluster.receive(pacoteRecebido);

        ByteArrayInputStream bais = new ByteArrayInputStream(pacoteRecebido.getData(), 0, pacoteRecebido.getLength());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Object obj = ois.readObject();

        if (obj instanceof PacoteCluster) {
          processarMensagemCluster((PacoteCluster) obj, pacoteRecebido.getAddress());
        } // fim do if
      } catch (Exception e) {
        if (!executando) {
          break;
        } // fim do if
      } // fim do try-catch
    } // fim do while
  } // fim do metodo escutarCluster

  /*
   * Metodo: processarMensagemCluster
   * Funcao: Trata mensagens de sondagem, resposta, heartbeat e sincronizacao
   */
  private void processarMensagemCluster(PacoteCluster pacote, InetAddress enderecoRemetente) {
    int remetente = pacote.getIdRemetente();

    // Filtro de loopback: ignora mensagens enviadas por este proprio no
    if (this.id != -1 && remetente == this.id) {
      return;
    } // fim do if

    if (remetente > 0) {
      idsConhecidos.add(remetente);
      if (pacote.isLider() && enderecoRemetente != null) {
        int conjRem = Servidor.calcularConjuntoPorId(remetente);
        ipsLideresConjunto.put(conjRem, enderecoRemetente);
      }
    } // fim do if

    boolean pertenceAoMesmoConjunto = mesmoConjunto(remetente);

    if (pertenceAoMesmoConjunto && pacote.isLider()) {
      idLiderAtual = remetente;
      ultimoHeartbeatRecebido = System.currentTimeMillis();
    } // fim do if

    switch (pacote.getTipo()) {
      case SONDAGEM:
        if (this.id != -1) {
          // Responde informando que este no esta ativo
          enviarPacote(new PacoteCluster(PacoteCluster.Tipo.RESPOSTA_SONDAGEM, this.id, servidor.isLider(), null));
          if (servidor.isLider() && (!servidor.usuariosOnline.isEmpty() || !servidor.grupos.isEmpty())) {
            // Lider com dados ativos sincroniza o novo no que acabou de entrar
            enviarSnapshot();
          } // fim do if
        } // fim do if
        break;

      case RESPOSTA_SONDAGEM:
        if (pertenceAoMesmoConjunto && pacote.isLider()) {
          idLiderAtual = remetente;
          ultimoHeartbeatRecebido = System.currentTimeMillis();
        } // fim do if
        break;

      case SOLICITAR_ESTADO:
        // Atende a solicitacao se possuir dados em memoria ou se for o Lider ativo do mesmo conjunto
        if ((servidor.isLider() && pertenceAoMesmoConjunto)
            || (remetente != this.id && (!servidor.usuariosOnline.isEmpty() || !servidor.grupos.isEmpty()))) {
          System.out.println("[SYNC] No (ID " + remetente + ") solicitou o estado. Enviando snapshot da memoria...");
          enviarSnapshot();
        } // fim do if
        break;

      case HEARTBEAT:
        if (this.id == -1 || !pertenceAoMesmoConjunto) {
          break; // Ignora se ainda em fase de atribuicao de ID ou se o Heartbeat for de outro
                 // conjunto
        } // fim do if

        // ALGORITMO BULLY (Restrito ao mesmo Conjunto): Quanto menor o numero, MAIOR a
        // prioridade
        if (remetente < this.id) {
          // Ha um lider de maior prioridade ativo no meu conjunto
          ultimoHeartbeatRecebido = System.currentTimeMillis();
          idLiderAtual = remetente;

          // PREEMPCAO: Se eu achava que era lider do conjunto, devo me rebaixar
          // imediatamente
          if (servidor.isLider()) {
            System.out.println("\n[BULLY-PREEMPCAO] Heartbeat recebido do Servidor com maior prioridade no Conjunto "
                + servidor.getConjunto() + " (ID: " + remetente + "). Rebaixando para Standby!");
            // Transfere o estado sincronicamente antes de rebaixar para garantir que o novo lider receba os grupos
            enviarSnapshotSincrono();
            servidor.rebaixarParaStandby();
            estadoConjuntoRecebido = true;
            estadoGlobalRecebido = true;
          } else if (!estadoConjuntoRecebido) {
            pedirEstadoAoCluster();
          } // fim do if
        } else if (remetente > this.id && pacote.isLider()) {
          // Um servidor de menor autoridade do meu conjunto pensa que e lider, mas eu
          // existo!
          if (!servidor.isLider()) {
            System.out.println("[BULLY] Detectado servidor de ID maior (" + remetente
                + ") como lider no Conjunto " + servidor.getConjunto() + ". Reclamando lideranca por precedencia!");
            servidor.promoverParaLider();
          } // fim do if
        } // fim do if
        break;

      case SYNC_ESTADO:
        if (this.id == -1) {
          if (pacote.getDadosSync() != null) {
            pacotesSyncPendentes.put(pacote.getDadosSync().getConjuntoOrigem(), pacote.getDadosSync());
          }
          break;
        } // fim do if
        if (pacote.getDadosSync() != null) {
          boolean aceitar = false;
          if (pertenceAoMesmoConjunto) {
            // Mesmo conjunto: aceita se:
            // 1. Ainda nao recebeu o estado inicial do seu proprio conjunto; OU
            // 2. Opera como Standby e a mensagem veio de um lider ou no prioritario; OU
            // 3. Acabou de assumir a lideranca (preempcao Bully) e sua lista de grupos ainda esta vazia,
            //    recebendo a transferencia de grupos do lider anterior (ex: ID 6 transferindo para ID 3)
            if (!estadoConjuntoRecebido) {
              aceitar = true;
            } else if (!servidor.isLider() && (remetente < this.id || pacote.isLider())) {
              aceitar = true;
            } else if (servidor.isLider() && servidor.grupos.isEmpty()
                && !pacote.getDadosSync().getGrupos().isEmpty()) {
              aceitar = true;
            } // fim do if-else
          } else {
            // Outro conjunto: aceita sincronizacao GLOBAL de usuariosOnline vinda de outros conjuntos
            // sem afetar os grupos locais desta particao
            aceitar = true;
          } // fim do if-else

          if (aceitar) {
            servidor.aplicarSincronizacao(pacote.getDadosSync());
            estadoGlobalRecebido = true;
            if (pertenceAoMesmoConjunto) {
              estadoConjuntoRecebido = true;
            } // fim do if
            synchronized (lockSincronizacao) {
              lockSincronizacao.notifyAll();
            } // fim do synchronized
          } // fim do if
        } // fim do if

        if (pertenceAoMesmoConjunto && (remetente < this.id || pacote.isLider())) {
          ultimoHeartbeatRecebido = System.currentTimeMillis();
          if (pacote.isLider()) {
            idLiderAtual = remetente;
          } // fim do if
        } // fim do if
        break;
    } // fim do switch
  } // fim do metodo processarMensagemCluster

  /*
   * Metodo: loopHeartbeat
   * Funcao: Se este no for o Lider, emite pulsos de vida (Heartbeat)
   * periodicamente
   */
  private void loopHeartbeat() {
    while (executando) {
      try {
        Thread.sleep(INTERVALO_HEARTBEAT_MS);
        if (this.id != -1 && servidor.isLider()) {
          enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
        } // fim do if
      } catch (InterruptedException ignored) {
      } catch (Exception e) {
        if (executando) {
          System.err.println("[HEARTBEAT-ERRO] Falha ao enviar batimento: " + e.getMessage());
        } // fim do if
      } // fim do try-catch
    } // fim do while
  } // fim do metodo loopHeartbeat

  /*
   * Metodo: loopWatchdog
   * Funcao: Monitora a inatividade do lider. Se expirar, assume a coroa (Bully).
   * Utiliza backoff baseado no ID para evitar colisoes de promocao.
   */
  private void loopWatchdog() {
    while (executando) {
      try {
        Thread.sleep(500);

        if (this.id == -1 || servidor.isLider()) {
          continue; // Lider nao precisa monitorar outros
        } // fim do if

        int ordemNoConjunto = Math.max(0, (this.id - 1) / 3);
        long timeoutEspecifico = TIMEOUT_LIDER_MS + (ordemNoConjunto * 400L);
        long tempoSemHeartbeat = System.currentTimeMillis() - ultimoHeartbeatRecebido;
        if (tempoSemHeartbeat > timeoutEspecifico) {
          // Timeout excedido! O lider atual do conjunto caiu.
          System.out.println("\n[BULLY] Timeout do Lider do Conjunto " + servidor.getConjunto()
              + " excedido (" + tempoSemHeartbeat + "ms sem heartbeat).");
          System.out.println("[BULLY] ASSUMINDO A COROA DO CONJUNTO " + servidor.getConjunto()
              + " " + servidor.getFaixaLetras() + "!");

          servidor.promoverParaLider();
          // Notifica imediatamente o cluster para rebaixar eventuais nos concorrentes
          enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
        } // fim do if
      } catch (InterruptedException ignored) {
      } catch (Exception e) {
        if (executando) {
          System.err.println("[WATCHDOG-ERRO] Falha no monitor de lider: " + e.getMessage());
        } // fim do if
      } // fim do try-catch
    } // fim do while
  } // fim do metodo loopWatchdog

  /*
   * Metodo: replicarEstado
   * Funcao: Envia o snapshot da memoria central para os nos Standby via Multicast
   */
  public void replicarEstado() {
    enviarSnapshot();
  } // fim do metodo replicarEstado

  /*
   * Metodo: enviarSnapshot
   * Funcao: Serializa e transmite o estado atual da memoria central no canal do
   * cluster em background
   */
  public void enviarSnapshot() {
    new Thread(this::enviarSnapshotSincrono, "Thread-ReplicarEstado").start();
  } // fim do metodo enviarSnapshot

  /*
   * Metodo: enviarSnapshotSincrono
   * Funcao: Serializa e transmite imediatamente o estado atual da memoria central no canal do cluster
   */
  public void enviarSnapshotSincrono() {
    try {
      servidor.mutex.acquire();
      SyncPacote sync;
      try {
        sync = new SyncPacote(this.id, servidor.usuariosOnline, servidor.grupos);
      } finally {
        servidor.mutex.release();
      } // fim do try-finally

      enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SYNC_ESTADO, this.id, servidor.isLider(), sync));
      System.out.println("[SYNC-PUSH] Estado replicado no canal do cluster ("
          + sync.getUsuariosOnline().size() + " usuario(s), "
          + sync.getGrupos().size() + " grupo(s): " + sync.getGrupos().keySet() + ").");
    } catch (Exception e) {
      System.err.println("[SYNC-ERRO] Falha ao replicar estado: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo enviarSnapshotSincrono

  /*
   * Metodo: enviarPacote
   * Funcao: Serializa e transmite um PacoteCluster via socket Multicast
   */
  private void enviarPacote(PacoteCluster pacote) {
    synchronized (lockEnvio) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(pacote);
        oos.flush();

        byte[] dados = baos.toByteArray();
        DatagramPacket datagramPacket = new DatagramPacket(dados, dados.length, grupoMulticast, PORTA_CLUSTER);
        socketCluster.send(datagramPacket);
      } catch (Exception e) {
        if (executando) {
          System.err.println("[CLUSTER-ENVIO-ERRO] " + e.getMessage());
        } // fim do if
      } // fim do try-catch
    } // fim do synchronized
  } // fim do metodo enviarPacote

  /*
   * Metodo: parar
   * Funcao: Encerra o socket e as threads de redundancia
   */
  public void parar() {
    this.executando = false;
    try {
      if (socketCluster != null && !socketCluster.isClosed()) {
        socketCluster.leaveGroup(grupoMulticast);
        socketCluster.close();
      } // fim do if
    } catch (Exception ignored) {
    } // fim do try-catch
  } // fim do metodo parar
}
