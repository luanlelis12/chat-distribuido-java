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
  private final Object lockEnvio = new Object();
  private final Object lockSincronizacao = new Object();
  private volatile boolean estadoInicialRecebido = false;
  private volatile int idLiderAtual = -1;

  public GerenteRedundancia(Servidor servidor) {
    this.servidor = servidor;
  }

  public int getId() {
    return id;
  }

  /*
   * Metodo: start
   * Funcao: Inicializa socket multicast, determina ID e dispara as threads de controle
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

      // Fase de definicao do ID (Forcado vs Auto-Descoberta) e sincronizacao de estado
      determinarId();

      // Inicia threads de envio periodico de Heartbeat e Watchdog
      new Thread(this::loopHeartbeat, "Thread-Cluster-Heartbeat").start();
      new Thread(this::loopWatchdog, "Thread-Cluster-Watchdog").start();

    } catch (Exception e) {
      System.err.println("[REDUNDANCIA-ERRO] Falha critica ao iniciar modulo de redundancia: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /*
   * Metodo: determinarId
   * Funcao: Define o ID fixado ou descobre o menor ID disponivel via sondagem na rede,
   * garantindo a obtencao previa do estado da memoria antes de assumir a lideranca.
   */
  private void determinarId() {
    System.out.println("[REDUNDANCIA] Sondando cluster para mapear os outros nos ativos...");
    enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SONDAGEM, -1, false, null));

    try {
      Thread.sleep(JANELA_SONDAGEM_MS);
    } catch (InterruptedException ignored) {
    }

    if (servidor.getIdForcado() != null) {
      this.id = servidor.getIdForcado();
      servidor.setIdServidor(this.id);
      System.out.println("[REDUNDANCIA] ID fixado manualmente por parametro: " + this.id);
      idsConhecidos.add(this.id);
    } else {
      // Encontra o menor ID positivo (1, 2, 3...) ainda nao utilizado no cluster
      int menorId = 1;
      while (idsConhecidos.contains(menorId)) {
        menorId++;
      }
      this.id = menorId;
      servidor.setIdServidor(this.id);
      idsConhecidos.add(this.id);
      System.out.println("[REDUNDANCIA] Auto-atribuicao finalizada! ID atribuido: " + this.id);
    }

    // Avalia papel inicial (Bully: se nao ha nenhum ID menor que o meu ativo, sou o lider!)
    boolean temMenorAtivo = false;
    boolean haOutrosNos = false;
    for (int outroId : idsConhecidos) {
      if (outroId != this.id) {
        haOutrosNos = true;
        if (outroId < this.id) {
          temMenorAtivo = true;
        }
      }
    }

    if (!temMenorAtivo) {
      // Sou o servidor com maior autoridade (menor ID)
      if (haOutrosNos) {
        // Ha outros servidores na rede ja em execucao (ex: servidor 2 era lider provisorio).
        // Se ainda nao recebemos o snapshot de estado, solicitamos explicitamente antes de assumir
        if (!estadoInicialRecebido) {
          System.out.println("[REDUNDANCIA] Detectados outros servidores ativos no cluster.");
          System.out.println("[REDUNDANCIA] Solicitando transferencia de estado antes de assumir a lideranca...");
          pedirEstadoAoCluster();
          aguardarSincronizacao(2000);
        }
      } else {
        // Primeiro servidor a subir no cluster: estado inicial limpo e legitimo
        estadoInicialRecebido = true;
      }

      // Envia pulso de preempcao para qualquer lider provisorio liberar as portas
      enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
      try {
        Thread.sleep(400); // Pausa graciosa para o lider anterior fechar os sockets
      } catch (InterruptedException ignored) {
      }
      servidor.promoverParaLider();
      enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
    } else {
      this.ultimoHeartbeatRecebido = System.currentTimeMillis();
      System.out.println("[REDUNDANCIA] Servidor (ID " + this.id + ") operando em modo STANDBY.");
      if (!estadoInicialRecebido) {
        pedirEstadoAoCluster();
      }
    }
  }

  /*
   * Metodo: pedirEstadoAoCluster
   * Funcao: Solicita snapshot completo da memoria do cluster ao Lider atual
   */
  public void pedirEstadoAoCluster() {
    System.out.println("[SYNC] Solicitando snapshot atualizado da memoria ao cluster...");
    enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SOLICITAR_ESTADO, this.id, false, null));
  }

  /*
   * Metodo: aguardarSincronizacao
   * Funcao: Bloqueia ate receber o snapshot de estado ou ate atingir timeout de seguranca
   */
  private void aguardarSincronizacao(long timeoutMs) {
    synchronized (lockSincronizacao) {
      long limite = System.currentTimeMillis() + timeoutMs;
      while (!estadoInicialRecebido && System.currentTimeMillis() < limite) {
        long restante = limite - System.currentTimeMillis();
        if (restante <= 0) {
          break;
        }
        try {
          lockSincronizacao.wait(restante);
        } catch (InterruptedException ignored) {
        }
      }
    }
    if (estadoInicialRecebido) {
      System.out.println("[SYNC] Estado inicial sincronizado com sucesso antes da promocao!");
    } else {
      System.out.println("[SYNC-AVISO] Timeout aguardando sincronizacao do cluster. Prosseguindo...");
    }
  }

  /*
   * Metodo: escutarCluster
   * Funcao: Recebe mensagens multicast de outros servidores e executa as acoes do Bully
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
          processarMensagemCluster((PacoteCluster) obj);
        }
      } catch (Exception e) {
        if (!executando) {
          break;
        }
      }
    }
  }

  /*
   * Metodo: processarMensagemCluster
   * Funcao: Trata mensagens de sondagem, resposta, heartbeat e sincronizacao
   */
  private void processarMensagemCluster(PacoteCluster pacote) {
    int remetente = pacote.getIdRemetente();

    // Filtro de loopback: ignora mensagens enviadas por este proprio no
    if (this.id != -1 && remetente == this.id) {
      return;
    }

    if (remetente > 0) {
      idsConhecidos.add(remetente);
    }

    if (pacote.isLider()) {
      idLiderAtual = remetente;
      ultimoHeartbeatRecebido = System.currentTimeMillis();
    }

    switch (pacote.getTipo()) {
      case SONDAGEM:
        if (this.id != -1) {
          // Responde informando que este no esta ativo
          enviarPacote(new PacoteCluster(PacoteCluster.Tipo.RESPOSTA_SONDAGEM, this.id, servidor.isLider(), null));
          if (servidor.isLider()) {
            // Lider aproveita para sincronizar o novo no que acabou de entrar
            enviarSnapshot();
          }
        }
        break;

      case RESPOSTA_SONDAGEM:
        if (pacote.isLider()) {
          idLiderAtual = remetente;
          ultimoHeartbeatRecebido = System.currentTimeMillis();
        }
        break;

      case SOLICITAR_ESTADO:
        // O lider atende a solicitacao enviando o snapshot.
        // Se este no tiver usuarios/grupos e o solicitante for um novo lider, tambem atende
        if (servidor.isLider() || (remetente != this.id && (!servidor.usuariosOnline.isEmpty() || !servidor.grupos.isEmpty()))) {
          System.out.println("[SYNC] No (ID " + remetente + ") solicitou o estado. Enviando snapshot da memoria...");
          enviarSnapshot();
        }
        break;

      case HEARTBEAT:
        if (this.id == -1) {
          break; // Ainda em fase de auto-atribuicao de ID
        }

        // ALGORITMO BULLY: Quanto menor o numero, MAIOR a prioridade
        if (remetente < this.id) {
          // Ha um lider de maior prioridade ativo
          ultimoHeartbeatRecebido = System.currentTimeMillis();
          idLiderAtual = remetente;

          // PREEMPCAO: Se eu achava que era lider, devo me rebaixar imediatamente
          if (servidor.isLider()) {
            System.out.println("\n[BULLY-PREEMPCAO] Heartbeat recebido do Servidor com maior prioridade (ID: "
                + remetente + "). Rebaixando para Standby!");
            // Transfere o estado para garantir que o novo lider e todos estejam atualizados
            enviarSnapshot();
            servidor.rebaixarParaStandby();
            estadoInicialRecebido = true;
          } else if (!estadoInicialRecebido) {
            pedirEstadoAoCluster();
          }
        } else if (remetente > this.id && pacote.isLider()) {
          // Um servidor de menor autoridade pensa que e lider, mas eu existo!
          if (!servidor.isLider()) {
            System.out.println("[BULLY] Detectado servidor de ID maior (" + remetente
                + ") como lider. Reclamando lideranca por precedencia!");
            servidor.promoverParaLider();
          }
        }
        break;

      case SYNC_ESTADO:
        if (pacote.getDadosSync() != null) {
          // Aceita sincronizacao se:
          // 1. Ainda nao recebeu o estado inicial (durante boot/sondagem); OU
          // 2. Opera como Standby e a mensagem veio de um lider ou no prioritario
          boolean aceitar = false;
          if (!estadoInicialRecebido) {
            aceitar = true;
          } else if (!servidor.isLider() && (remetente < this.id || pacote.isLider())) {
            aceitar = true;
          }

          if (aceitar) {
            servidor.aplicarSincronizacao(pacote.getDadosSync());
            estadoInicialRecebido = true;
            synchronized (lockSincronizacao) {
              lockSincronizacao.notifyAll();
            }
          }
        }

        if (remetente < this.id || pacote.isLider()) {
          ultimoHeartbeatRecebido = System.currentTimeMillis();
          if (pacote.isLider()) {
            idLiderAtual = remetente;
          }
        }
        break;
    }
  }

  /*
   * Metodo: loopHeartbeat
   * Funcao: Se este no for o Lider, emite pulsos de vida (Heartbeat) periodicamente
   */
  private void loopHeartbeat() {
    while (executando) {
      try {
        Thread.sleep(INTERVALO_HEARTBEAT_MS);
        if (this.id != -1 && servidor.isLider()) {
          enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
        }
      } catch (InterruptedException ignored) {
      } catch (Exception e) {
        if (executando) {
          System.err.println("[HEARTBEAT-ERRO] Falha ao enviar batimento: " + e.getMessage());
        }
      }
    }
  }

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
        }

        long timeoutEspecifico = TIMEOUT_LIDER_MS + Math.max(0, (this.id - 1) * 400L);
        long tempoSemHeartbeat = System.currentTimeMillis() - ultimoHeartbeatRecebido;
        if (tempoSemHeartbeat > timeoutEspecifico) {
          // Timeout excedido! O lider atual caiu.
          System.out.println("\n[BULLY] Timeout do Lider excedido (" + tempoSemHeartbeat + "ms sem heartbeat).");
          System.out.println("[BULLY] ASSUMINDO A COROA!");

          servidor.promoverParaLider();
          // Notifica imediatamente o cluster para rebaixar eventuais nos concorrentes
          enviarPacote(new PacoteCluster(PacoteCluster.Tipo.HEARTBEAT, this.id, true, null));
        }
      } catch (InterruptedException ignored) {
      } catch (Exception e) {
        if (executando) {
          System.err.println("[WATCHDOG-ERRO] Falha no monitor de lider: " + e.getMessage());
        }
      }
    }
  }

  /*
   * Metodo: replicarEstado
   * Funcao: Envia o snapshot da memoria central para os nos Standby via Multicast
   */
  public void replicarEstado() {
    enviarSnapshot();
  }

  /*
   * Metodo: enviarSnapshot
   * Funcao: Serializa e transmite o estado atual da memoria central no canal do cluster
   */
  public void enviarSnapshot() {
    new Thread(() -> {
      try {
        servidor.mutex.acquire();
        SyncPacote sync;
        try {
          sync = new SyncPacote(this.id, servidor.usuariosOnline, servidor.grupos);
        } finally {
          servidor.mutex.release();
        }

        enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SYNC_ESTADO, this.id, servidor.isLider(), sync));
        System.out.println("[SYNC-PUSH] Estado replicado no canal do cluster ("
            + sync.getUsuariosOnline().size() + " usuario(s), "
            + sync.getGrupos().size() + " grupo(s)).");
      } catch (Exception e) {
        System.err.println("[SYNC-ERRO] Falha ao replicar estado: " + e.getMessage());
      }
    }, "Thread-ReplicarEstado").start();
  }

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
        }
      }
    }
  }

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
      }
    } catch (Exception ignored) {
    }
  }
}
