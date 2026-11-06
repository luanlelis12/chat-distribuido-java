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
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class Servidor {
  public static final int PORTA_TCP_BASE = 6789;
  public static final int PORTA_UDP_BASE = 7777;
  private final int PORTA_DESCOBERTA = 8888;

  // Configuracao de Particionamento (Sharding) e Portas Dinamicas
  private int conjunto = -1;
  private String faixaLetras = "[A..I]";
  private int portaTCP = PORTA_TCP_BASE;
  private int portaUDP = PORTA_UDP_BASE;

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
    if (idForcado != null && idForcado > 0) {
      configurarParticao(idForcado, false);
    } // fim do if
  } // fim do construtor

  /*
   * Metodo: calcularConjuntoPorId
   * Funcao: Calcula a qual conjunto (1, 2 ou 3) o servidor pertence usando ID % 3
   * Parametros: id (ID do servidor)
   * Retorno: int (1, 2 ou 3)
   */
  public static int calcularConjuntoPorId(int id) {
    int resto = id % 3;
    if (resto == 1) {
      return 1; // Conjunto 1: [A..I]
    } else if (resto == 2) {
      return 2; // Conjunto 2: [J..R]
    } else {
      return 3; // Conjunto 3 (resto == 0): [S..Z]
    } // fim do if-else
  } // fim do metodo calcularConjuntoPorId

  /*
   * Metodo: obterFaixaPorConjunto
   * Funcao: Retorna a representacao textual da faixa de letras do conjunto
   * Parametros: conjunto (1, 2 ou 3)
   * Retorno: String (faixa de letras)
   */
  public static String obterFaixaPorConjunto(int conjunto) {
    switch (conjunto) {
      case 1:
        return "[A..I]";
      case 2:
        return "[J..R]";
      case 3:
        return "[S..Z]";
      default:
        return "[DESCONHECIDO]";
    } // fim do switch
  } // fim do metodo obterFaixaPorConjunto

  /*
   * Metodo: obterIpLocalServidor
   * Funcao: Descobre o IP real da interface de rede ativa do servidor (evitando 127.0.1.1 no Linux)
   */
  public static String obterIpLocalServidor() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface iface = interfaces.nextElement();
        if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
          continue;
        }
        Enumeration<InetAddress> enderecos = iface.getInetAddresses();
        while (enderecos.hasMoreElements()) {
          InetAddress addr = enderecos.nextElement();
          if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isAnyLocalAddress()) {
            return addr.getHostAddress();
          }
        }
      }
    } catch (Exception ignored) {
    }
    try {
      return InetAddress.getLocalHost().getHostAddress();
    } catch (Exception e) {
      return "127.0.0.1";
    }
  }

  /*
   * Metodo: configurarParticao
   * Funcao: Define conjunto, faixa de letras e portas TCP/UDP com base no ID do servidor
   * Parametros: id (ID do servidor), exibirLog (booleano para exibir log de configuracao)
   * Retorno: void
   */
  private void configurarParticao(int id, boolean exibirLog) {
    this.conjunto = calcularConjuntoPorId(id);
    this.faixaLetras = obterFaixaPorConjunto(this.conjunto);
    int offset = this.conjunto - 1;
    this.portaTCP = PORTA_TCP_BASE + offset;
    this.portaUDP = PORTA_UDP_BASE + offset;

    if (exibirLog) {
      String ipLocal = obterIpLocalServidor();
      System.out.println("===================================================");
      System.out.println("[SERVIDOR] IP do Servidor: " + ipLocal);
      System.out.println("[SHARDING] Servidor ID " + id + " -> Conjunto " + this.conjunto + " " + this.faixaLetras);
      System.out.println("[PORTAS] configuradas -> TCP: " + this.portaTCP + " | UDP: " + this.portaUDP);
      System.out.println("===================================================");
    } // fim do if
  } // fim do metodo configurarParticao

  public Integer getIdForcado() {
    return idForcado;
  }

  public int getIdServidor() {
    return idServidor;
  }

  public void setIdServidor(int idServidor) {
    boolean exibirLog = (this.idForcado == null || this.idForcado != idServidor);
    this.idServidor = idServidor;
    configurarParticao(idServidor, exibirLog);
  }

  public int getConjunto() {
    return conjunto;
  }

  public String getFaixaLetras() {
    return faixaLetras;
  }

  public int getPortaTCP() {
    return portaTCP;
  }

  public int getPortaUDP() {
    return portaUDP;
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

    String ipLocal = obterIpLocalServidor();
    System.out.println("\n***************************************************");
    System.out.println("[COROACAO] SERVIDOR ASSUMIU A LIDERANCA! (ID: " + idServidor
        + " | Conjunto " + conjunto + " " + faixaLetras + " | IP: " + ipLocal + ")");
    System.out.println("[COROACAO] Abrindo portas em " + ipLocal + ": TCP (" + portaTCP + "), UDP (" + portaUDP
        + "), Descoberta (" + PORTA_DESCOBERTA + ")");
    System.out.println("***************************************************\n");

    iniciarServicosCliente();

    // Notifica os clientes existentes sobre a presenca do novo lider
    notificarClientesNovaLideranca();

    // Notifica os standbys e demais conjuntos se ja possuir grupos ou usuarios ativos na memoria
    if (gerenteRedundancia != null && (!this.grupos.isEmpty() || !this.usuariosOnline.isEmpty())) {
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
    System.out.println("[PREEMPCAO] No com maior prioridade detectado no Conjunto " + conjunto + " " + faixaLetras + ".");
    System.out.println("[STANDBY] Rebaixando este servidor (ID: " + idServidor + ") para STANDBY.");
    System.out.println("[STANDBY] Liberando portas de clientes (TCP " + portaTCP + " / UDP " + portaUDP + ")...");
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
      threadTCP = new ServidorTCP(portaTCP, this);
      threadTCP.start();

      threadUDP = new ServidorUDP(portaUDP, this);
      threadUDP.start();

      iniciarServidorDescoberta();
    } catch (Exception e) {
      System.err.println("[ERRO] Falha ao iniciar servicos de cliente: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo iniciarServicosCliente

  /*
   * Metodo: notificarClientesNovaLideranca
   * Funcao: Envia um datagrama UDP a cada cliente online notificando a presenca e IP do novo lider
   */
  private void notificarClientesNovaLideranca() {
    new Thread(() -> {
      try {
        Thread.sleep(400); // Pausa graciosa para estabilizar a subida dos servicos
        List<Usuario> lista;
        mutex.acquire();
        try {
          lista = new ArrayList<>(usuariosOnline.values());
        } finally {
          mutex.release();
        } // fim do try-finally

        if (lista.isEmpty()) {
          return;
        } // fim do if

        Protocol.APDU apduAviso = new Protocol.APDU("CONFIRM", "0", 0, "SERVIDOR",
            this.portaTCP + ":" + this.portaUDP, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeObject(apduAviso);
        out.flush();
        byte[] dados = baos.toByteArray();

        try (DatagramSocket socketEnvio = new DatagramSocket()) {
          for (Usuario u : lista) {
            try {
              DatagramPacket pacote = new DatagramPacket(dados, dados.length, u.getIp(), u.getPorta());
              socketEnvio.send(pacote);
              System.out.println("[LIDER] Notificado cliente '" + u.getNome() + "' ("
                  + u.getIp().getHostAddress() + ":" + u.getPorta() + ") sobre nova lideranca.");
            } catch (Exception ignored) {
            } // fim do try-catch
          } // fim do for
        } // fim do try-with-resources
      } catch (Exception e) {
        System.err.println("[LIDER-AVISO] Falha ao notificar clientes sobre lideranca: " + e.getMessage());
      } // fim do try-catch
    }, "Thread-NotificarClientesLideranca").start();
  } // fim do metodo notificarClientesNovaLideranca

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
            if (conjunto > 1) {
              try {
                Thread.sleep(50L * (conjunto - 1));
              } catch (InterruptedException ignored) {
              }
            }
            byte[] resposta = ("IP:" + portaTCP + ":" + portaUDP).getBytes();
            DatagramPacket pacoteRes = new DatagramPacket(
                resposta, resposta.length, pacote.getAddress(), pacote.getPort());
            socketDescoberta.send(pacoteRes);
          } // fim do if
        } // fim do while 
      } catch (Exception e) {
        if (!isLider) {
          // Fechamento esperado ao ser rebaixado para Standby
        } else {
          System.err.println("[BROADCAST-ERRO] " + e.getMessage());
        } // fim do if-else
      } // fim do try-catch
    });
    threadDescoberta.start();
  } // fim do metodo iniciarServidorDescoberta

  /*
   * Metodo: calcularConjuntoPorNome
   * Funcao: Calcula a qual conjunto (1, 2 ou 3) um nome (grupo ou usuario) pertence pela 1a letra
   */
  public static int calcularConjuntoPorNome(String chave) {
    if (chave == null || chave.trim().isEmpty()) {
      return 1;
    }
    char inicial = Character.toUpperCase(chave.trim().charAt(0));
    if (inicial >= 'A' && inicial <= 'I') {
      return 1;
    } else if (inicial >= 'J' && inicial <= 'R') {
      return 2;
    } else if (inicial >= 'S' && inicial <= 'Z') {
      return 3;
    }
    return 1;
  }

  /*
   * Metodo: calcularPortaTCPPorConjunto
   * Funcao: Retorna a porta TCP (6789, 6790 ou 6791) correspondente ao conjunto (1, 2 ou 3)
   */
  public static int calcularPortaTCPPorConjunto(int conjuntoAlvo) {
    return PORTA_TCP_BASE + (conjuntoAlvo - 1);
  }

  /*
   * Metodo: calcularPortaUDPPorConjunto
   * Funcao: Retorna a porta UDP (7777, 7778 ou 7779) correspondente ao conjunto (1, 2 ou 3)
   */
  public static int calcularPortaUDPPorConjunto(int conjuntoAlvo) {
    return PORTA_UDP_BASE + (conjuntoAlvo - 1);
  }

  /*
   * Metodo: obterIpLiderConjunto
   * Funcao: Retorna o InetAddress do Lider responsavel pelo conjuntoAlvo (1, 2 ou 3)
   * usando o mapeamento do cluster ou fallback para localhost (127.0.0.1)
   */
  public InetAddress obterIpLiderConjunto(int conjuntoAlvo) {
    try {
      if (conjuntoAlvo == this.conjunto) {
        return InetAddress.getByName("127.0.0.1");
      }
      if (gerenteRedundancia != null) {
        InetAddress ipCluster = gerenteRedundancia.obterIpLiderConjunto(conjuntoAlvo);
        if (ipCluster != null) {
          return ipCluster;
        }
      }
      return InetAddress.getByName("127.0.0.1");
    } catch (Exception e) {
      return null;
    }
  }

  /*
   * Metodo: aplicarSincronizacao
   * Funcao: Atualiza a memoria central com snapshot recebido:
   * - usuariosOnline (Global): atualizado integralmente a partir de qualquer Lider da rede
   * - grupos (Parcial): atualizado APENAS se o pacote vier do mesmo conjunto
   * Parametros: pacote (SyncPacote contendo usuarios online e grupos)
   */
  public void aplicarSincronizacao(SyncPacote pacote) {
    if (pacote == null) {
      return;
    }

    int conjuntoOrigem = pacote.getConjuntoOrigem();
    boolean mesmoConjunto = (conjuntoOrigem == this.conjunto);

    // Se o pacote veio do mesmo conjunto e este no ja e o Lider ativo COM grupos na memoria, ignora
    // (mas se for um novo Lider assumindo por preempcao com grupos vazios, permite receber os grupos do lider anterior!)
    if (mesmoConjunto && this.isLider && !this.grupos.isEmpty()) {
      return;
    } // fim do if

    try {
      mutex.acquire();
      try {
        // 1. REPLICACAO PARCIAL E GLOBAL DO MESMO CONJUNTO
        if (mesmoConjunto) {
          this.usuariosOnline.clear();
          this.usuariosOnline.putAll(pacote.getUsuariosOnline());

          this.grupos.clear();
          for (Map.Entry<String, ArrayList<Usuario>> entry : pacote.getGrupos().entrySet()) {
            ArrayList<Usuario> listaSincronizada = new ArrayList<>();
            for (Usuario u : entry.getValue()) {
              Usuario refGlobal = this.usuariosOnline.getOrDefault(u.getNome(), u);
              this.usuariosOnline.putIfAbsent(u.getNome(), refGlobal);
              listaSincronizada.add(refGlobal);
            }
            if (!listaSincronizada.isEmpty()) {
              this.grupos.put(entry.getKey(), listaSincronizada);
            }
          }

          System.out.println("[SYNC-CONJUNTO " + this.conjunto + "] Memoria sincronizada pelo Servidor ID "
              + pacote.getIdOrigem() + " (Mesmo Conjunto " + this.faixaLetras + ") -> "
              + usuariosOnline.size() + " usuario(s) [Global: " + usuariosOnline.keySet() + "], "
              + grupos.size() + " grupo(s) [Parcial: " + grupos.keySet() + "].");
        } else {
          // 2. REPLICACAO GLOBAL DE OUTRO CONJUNTO: Mescla usuariosOnline (logins/bloqueios/IPs)
          // preservando integralmente os grupos locais e evitando que um no recem-iniciado apague usuarios
          if (pacote.getUsuariosOnline() != null && !pacote.getUsuariosOnline().isEmpty()) {
            this.usuariosOnline.putAll(pacote.getUsuariosOnline());
          }
          for (ArrayList<Usuario> membros : this.grupos.values()) {
            for (int i = 0; i < membros.size(); i++) {
              Usuario atualizado = this.usuariosOnline.get(membros.get(i).getNome());
              if (atualizado != null) {
                membros.set(i, atualizado);
              }
            }
          }

          System.out.println("[SYNC-GLOBAL] Usuarios online sincronizados pelo Lider ID "
              + pacote.getIdOrigem() + " (Conjunto " + conjuntoOrigem + ") -> "
              + usuariosOnline.size() + " usuario(s) online " + usuariosOnline.keySet()
              + ". Grupos locais " + this.faixaLetras
              + " preservados (" + grupos.size() + " grupo(s): " + grupos.keySet() + ").");
        }
      } finally {
        mutex.release();
      } // fim do try-finally

      if (mesmoConjunto && this.isLider && !this.grupos.isEmpty()) {
        notificarAlteracaoMemoria();
      }
    } catch (Exception e) {
      System.err.println("[SYNC-ERRO] Falha ao aplicar sincronizacao: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo aplicarSincronizacao

  /*
   * Metodo: notificarAlteracaoMemoria
   * Funcao: Dispara replicacao automatica do estado atual para os servidores Standby
   */
  public void notificarAlteracaoMemoria() {
    if (isLider && gerenteRedundancia != null) {
      gerenteRedundancia.replicarEstado();
    } // fim do if
  } // fim do metodo notificarAlteracaoMemoria
}