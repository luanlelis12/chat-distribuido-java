/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 13/10/2026
* Ultima alteracao.: 13/10/2026
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
  private volatile boolean estadoInicialRecebido = false;

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

      // Fase de definicao do ID (Forcado vs Auto-Descoberta)
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
   * Funcao: Define o ID fixado ou descobre o menor ID disponivel via sondagem na rede
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
    for (int outroId : idsConhecidos) {
      if (outroId != this.id && outroId < this.id) {
        temMenorAtivo = true;
        break;
      }
    }

    if (!temMenorAtivo) {
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
      pedirEstadoAoLider();
    }
  }

  /*
   * Metodo: pedirEstadoAoLider
   * Funcao: Solicita snapshot completo da memoria do cluster ao Lider atual
   */
  public void pedirEstadoAoLider() {
    if (this.id != -1 && !servidor.isLider()) {
      System.out.println("[SYNC] Solicitando snapshot atualizado da memoria ao Lider...");
      enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SOLICITAR_ESTADO, this.id, false, null));
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

    // Se este no ainda esta descobrindo seu ID, apenas registra os outros nos
    if (this.id == -1) {
      if (pacote.isLider()) {
        ultimoHeartbeatRecebido = System.currentTimeMillis();
      }
      return;
    }

    switch (pacote.getTipo()) {
      case SONDAGEM:
        if (this.id != -1) {
          // Responde informando que este no esta ativo
          enviarPacote(new PacoteCluster(PacoteCluster.Tipo.RESPOSTA_SONDAGEM, this.id, servidor.isLider(), null));
          if (servidor.isLider()) {
            // Lider aproveita para sincronizar o novo no que acabou de entrar
            new Thread(() -> {
              try {
                Thread.sleep(300);
                replicarEstado();
              } catch (Exception ignored) {
              }
            }).start();
          }
        }
        break;

      case RESPOSTA_SONDAGEM:
        if (pacote.isLider() && remetente < this.id) {
          ultimoHeartbeatRecebido = System.currentTimeMillis();
        }
        break;

      case SOLICITAR_ESTADO:
        if (servidor.isLider()) {
          System.out.println("[SYNC] Standby (ID " + remetente + ") solicitou o estado. Replicando snapshot...");
          replicarEstado();
        }
        break;

      case HEARTBEAT:
        // ALGORITMO BULLY: Quanto menor o numero, MAIOR a prioridade
        if (remetente < this.id) {
          // Ha um lider de maior prioridade ativo
          ultimoHeartbeatRecebido = System.currentTimeMillis();

          // PREEMPCAO: Se eu achava que era lider, devo me rebaixar imediatamente
          if (servidor.isLider()) {
            System.out.println("\n[BULLY-PREEMPCAO] Heartbeat recebido do Servidor com maior prioridade (ID: "
                + remetente + "). Rebaixando para Standby!");
            servidor.rebaixarParaStandby();
            estadoInicialRecebido = false;
            pedirEstadoAoLider();
          } else if (!estadoInicialRecebido) {
            pedirEstadoAoLider();
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
        if (remetente < this.id || pacote.isLider()) {
          ultimoHeartbeatRecebido = System.currentTimeMillis();
          if (!servidor.isLider() && pacote.getDadosSync() != null) {
            estadoInicialRecebido = true;
            servidor.aplicarSincronizacao(pacote.getDadosSync());
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
   * Funcao: Monitora a inatividade do lider. Se expirar, assume a coroa (Bully)
   */
  private void loopWatchdog() {
    while (executando) {
      try {
        Thread.sleep(500);

        if (this.id == -1 || servidor.isLider()) {
          continue; // Lider nao precisa monitorar outros
        }

        long tempoSemHeartbeat = System.currentTimeMillis() - ultimoHeartbeatRecebido;
        if (tempoSemHeartbeat > TIMEOUT_LIDER_MS) {
          // Timeout excedido! O lider atual caiu.
          System.out.println("\n[BULLY] Timeout do Lider excedido (" + tempoSemHeartbeat + "ms sem heartbeat).");
          System.out.println("[BULLY] Nenhum no com ID menor respondeu. ASSUMINDO A COROA!");

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
    if (!servidor.isLider()) {
      return;
    }
    new Thread(() -> {
      try {
        servidor.mutex.acquire();
        SyncPacote sync;
        try {
          sync = new SyncPacote(this.id, servidor.usuariosOnline, servidor.grupos);
        } finally {
          servidor.mutex.release();
        }

        enviarPacote(new PacoteCluster(PacoteCluster.Tipo.SYNC_ESTADO, this.id, true, sync));
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
