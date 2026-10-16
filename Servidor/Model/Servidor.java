/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 13/10/2026
* Nome.............: Servidor.java
* Funcao...........: Gerenciar memoria central compartilhada e ciclo de vida do no (Lider vs Standby)
*******************************************************************/
package Model;

import Protocol.SyncPacote;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class Servidor {
  private final int PORTA_TCP = 6789;
  private final int PORTA_UDP = 7777;
  private final int PORTA_DESCOBERTA = 8888;

  // Redundancia e Controle de Estado
  private final Integer idForcado;
  private int idServidor = -1;
  private volatile boolean isLider = false;
  private GerenteRedundancia gerenteRedundancia;

  // Servicos de Atendimento a Clientes
  private ServidorTCP threadTCP;
  private ServidorUDP threadUDP;
  private DatagramSocket socketDescoberta;
  private Thread threadDescoberta;

  // Memoria Global Compartilhada
  public Map<String, Usuario> usuariosOnline = new ConcurrentHashMap<>();
  public Map<String, ArrayList<Usuario>> grupos = new ConcurrentHashMap<>();
  public Semaphore mutex = new Semaphore(1);

  // Mapas de Rastreio
  public Map<String, HashSet<String>> esperadosGrupo = new ConcurrentHashMap<>();
  public Map<String, HashSet<String>> recebidosGrupo = new ConcurrentHashMap<>();
  public Map<String, HashSet<String>> lidosGrupo = new ConcurrentHashMap<>();

  /*
   * Construtores
   */
  public Servidor() {
    this(null);
  }

  public Servidor(Integer idForcado) {
    this.idForcado = idForcado;
  }

  public Integer getIdForcado() {
    return idForcado;
  }

  public int getIdServidor() {
    return idServidor;
  }

  public void setIdServidor(int idServidor) {
    this.idServidor = idServidor;
  }

  public boolean isLider() {
    return isLider;
  }

  public GerenteRedundancia getGerenteRedundancia() {
    return gerenteRedundancia;
  }

  /*
   * Metodo: start
   * Funcao: Inicializa o modulo de redundancia, que decidira se este no e Lider ou Standby
   * Parametros: nenhum
   * Retorno: void
   */
  public void start() {
    System.out.println("----------------------------------------------");
    System.out.println("INICIANDO GERENCIADOR DE REDUNDANCIA DO CLUSTER");
    System.out.println("----------------------------------------------");

    this.gerenteRedundancia = new GerenteRedundancia(this);
    this.gerenteRedundancia.start();
  }

  /*
   * Metodo: promoverParaLider
   * Funcao: Assume o papel ativo no cluster, abrindo as portas TCP, UDP e Descoberta
   * Parametros: nenhum
   * Retorno: void
   */
  public synchronized void promoverParaLider() {
    if (this.isLider) {
      return;
    }
    this.isLider = true;

    System.out.println("\n***************************************************");
    System.out.println("[COROACAO] SERVIDOR ASSUMIU A LIDERANCA! (ID: " + idServidor + ")");
    System.out.println("[COROACAO] Abrindo portas: TCP (6789), UDP (7777), Descoberta (8888)");
    System.out.println("***************************************************\n");

    iniciarServicosCliente();

    // Notifica os standbys imediatamente com o estado atual que possui
    if (gerenteRedundancia != null) {
      gerenteRedundancia.replicarEstado();
    } // fim do if
  } // fim do metodo promoverParaLider

  /*
   * Metodo: rebaixarParaStandby
   * Funcao: Demove este no para passivo ao detectar um no com menor ID ativo (Preempcao).
   * Fecha ordenadamente os sockets de clientes para liberar as portas.
   * Parametros: nenhum
   * Retorno: void
   */
  public synchronized void rebaixarParaStandby() {
    if (!this.isLider) {
      return;
    } // fim do if
    this.isLider = false;

    System.out.println("\n***************************************************");
    System.out.println("[PREEMPCAO] No com maior prioridade detectado na rede.");
    System.out.println("[STANDBY] Rebaixando este servidor (ID: " + idServidor + ") para STANDBY.");
    System.out.println("[STANDBY] Liberando portas de clientes...");
    System.out.println("***************************************************\n");

    pararServicosCliente();
  } // fim do metodo rebaixarParaStandby

  /*
   * Metodo: iniciarServicosCliente
   * Funcao: Instancia e inicia as threads de escuta de clientes
   * Parametros: nenhum
   * Retorno: void
   */
  private void iniciarServicosCliente() {
    try {
      threadTCP = new ServidorTCP(PORTA_TCP, this);
      threadTCP.start();

      threadUDP = new ServidorUDP(PORTA_UDP, this);
      threadUDP.start();

      iniciarServidorDescoberta();
    } catch (Exception e) {
      System.err.println("[ERRO] Falha ao iniciar servicos de cliente: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo iniciarServicosCliente

  /*
   * Metodo: pararServicosCliente
   * Funcao: Encerra os sockets para liberar as portas ao novo lider
   * Parametros: nenhum
   * Retorno: void
   */
  private void pararServicosCliente() {
    try {
      if (threadTCP != null) {
        threadTCP.parar();
        threadTCP = null;
      } // fim do if
      if (threadUDP != null) {
        threadUDP.parar();
        threadUDP = null;
      } // fim do if
      if (socketDescoberta != null && !socketDescoberta.isClosed()) {
        socketDescoberta.close();
        socketDescoberta = null;
      } // fim do if
      if (threadDescoberta != null) {
        threadDescoberta.interrupt();
        threadDescoberta = null;
      } // fim do if
      System.out.println("[STANDBY] Portas de clientes liberadas com sucesso.");
    } catch (Exception e) {
      System.err.println("[ERRO] Falha ao parar servicos de cliente: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo pararServicosCliente

  /*
   * Metodo: iniciarServidorDescoberta
   * Funcao: Escuta pacotes em broadcast na porta 8888 e responde informando o IP
   * Parametros: nenhum
   * Retorno: void
   */
  private void iniciarServidorDescoberta() {
    threadDescoberta = new Thread(() -> {
      int tentativas = 5;
      while (isLider && tentativas > 0) {
        try {
          socketDescoberta = new DatagramSocket(null);
          socketDescoberta.setReuseAddress(true);
          socketDescoberta.bind(new java.net.InetSocketAddress(PORTA_DESCOBERTA));
          System.out.println("SERVIDOR BROADCAST - Escutando na porta " + PORTA_DESCOBERTA);
          break;
        } catch (Exception e) {
          tentativas--;
          if (tentativas == 0) {
            System.err.println("[BROADCAST-ERRO] Falha ao abrir porta " + PORTA_DESCOBERTA + ": " + e.getMessage());
            return;
          } // fim do if
          try {
            Thread.sleep(300);
          } catch (InterruptedException ignored) {
          } // fim do try-catch
        } // fim do try-catch
      } // fim do while

      try {
        while (isLider && socketDescoberta != null && !socketDescoberta.isClosed()) {
          byte[] buffer = new byte[1024];
          DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
          socketDescoberta.receive(pacote);

          String mensagem = new String(pacote.getData(), 0, pacote.getLength()).trim();
          if (mensagem.equals("SERVIDOR_IP")) {
            byte[] resposta = "IP".getBytes();
            DatagramPacket pacoteRes = new DatagramPacket(
                resposta, resposta.length, pacote.getAddress(), pacote.getPort());
            socketDescoberta.send(pacoteRes);
          }
        }
      } catch (Exception e) {
        if (!isLider) {
          // Fechamento esperado ao ser rebaixado para Standby
        } else {
          System.err.println("[BROADCAST-ERRO] " + e.getMessage());
        }
      }
    });
    threadDescoberta.start();
  }

  /*
   * Metodo: aplicarSincronizacao
   * Funcao: Atualiza a memoria central com snapshot de estado recebido do Lider
   */
  public void aplicarSincronizacao(SyncPacote pacote) {
    if (this.isLider) {
      return; // Lider nao sobrescreve sua memoria com dados externos
    }
    try {
      mutex.acquire();
      try {
        this.usuariosOnline.clear();
        this.usuariosOnline.putAll(pacote.getUsuariosOnline());

        this.grupos.clear();
        this.grupos.putAll(pacote.getGrupos());

        System.out.println("[SYNC] Memoria atualizada com sucesso pelo Lider (ID: "
            + pacote.getIdOrigem() + ") -> " + usuariosOnline.size() + " usuario(s), "
            + grupos.size() + " grupo(s).");
      } finally {
        mutex.release();
      }
    } catch (Exception e) {
      System.err.println("[SYNC-ERRO] Falha ao aplicar sincronizacao no Standby: " + e.getMessage());
    }
  }

  /*
   * Metodo: notificarAlteracaoMemoria
   * Funcao: Dispara replicacao automatica do estado atual para os servidores Standby
   */
  public void notificarAlteracaoMemoria() {
    if (isLider && gerenteRedundancia != null) {
      gerenteRedundancia.replicarEstado();
    }
  }
}