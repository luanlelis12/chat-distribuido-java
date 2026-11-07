/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 20/09/2026
* Nome.............: Cliente.java
* Funcao...........: Gerencia as apdus e a comunicacao com o servidor (Thin Client)
*******************************************************************/
package Model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import Controller.clienteController;
import Network.Descobridor;
import Protocol.APDU;

public class Cliente extends Thread {

  private volatile int PORTA_SERVIDOR_UDP = 7777;
  private volatile int PORTA_SERVIDOR_TCP = 6789;
  private final int TIMEOUT_TCP = 5000;
  private final int TIMEOUT_LOGOUT = 1000;
  private final int TIMEOUT_TICK1_UDP_MS = 1200;

  private int portaClienteUDP;

  private final String GRUPO = "grupo";
  private final String PRIVADO = "priv";
  private final String BLOQUEIO = "bloqueio";

  private String nomeCliente;
  private InetAddress ipCliente;
  private InetAddress ipServidor;
  private DatagramSocket endpointCliente;
  private volatile boolean escutaUDPAtiva = false;
  private volatile boolean logoutRealizado = false;
  private Thread shutdownHookLogout;
  private final Set<String> mensagensAguardandoTick1 = ConcurrentHashMap.newKeySet();

  /*
   * Metodo: Cliente
   * Funcao: Inicializa o cliente e abre a porta UDP local
   * Parametros: nomeCliente = nome do usuario, ipServidor = IP do servidor
   * Retorno: void
   */
  public Cliente(String nomeCliente, String ipServidor) {
    try {
      this.nomeCliente = nomeCliente != null ? nomeCliente.trim() : "";
      this.ipCliente = descobrirIpLocal();
      this.ipServidor = InetAddress.getByName(ipServidor);
      this.PORTA_SERVIDOR_TCP = Descobridor.getUltimaPortaTCPDescoberta();
      this.PORTA_SERVIDOR_UDP = Descobridor.getUltimaPortaUDPDescoberta();

      this.portaClienteUDP = 5000 + (int) (Math.random() * 1000);
      this.endpointCliente = new DatagramSocket(portaClienteUDP);

      this.shutdownHookLogout = new Thread(() -> {
        if (!logoutRealizado) {
          fazerLogout();
          desligarCliente();
        }
      }, "ShutdownHook-Logout");
      Runtime.getRuntime().addShutdownHook(this.shutdownHookLogout);

      System.out.println("CLIENTE estabelecido: nome = " + this.nomeCliente + " / ip = " + ipCliente
          + " / servidor = " + this.ipServidor.getHostAddress() + ":" + PORTA_SERVIDOR_TCP);
    } catch (Exception e) {
      System.out.println("ERRO: Nao foi possivel inicializar o cliente. " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do construtor

  /*
   * Metodo: formatarDestinoServidorTCP
   * Funcao: Formata a identificacao do servidor destino (IP e Porta TCP) para o log
   */
  private String formatarDestinoServidorTCP(Socket socket) {
    if (socket == null) {
      return "Servidor Desconhecido";
    }
    String ip = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "desconhecido";
    return "Servidor [" + ip + ":" + socket.getPort() + " (TCP)]";
  }

  /*
   * Metodo: formatarDestinoServidorUDP
   * Funcao: Formata a identificacao do servidor destino (IP e Porta UDP) para o log
   */
  private String formatarDestinoServidorUDP(InetAddress ipDestino, int portaUDP) {
    String ip = ipDestino != null ? ipDestino.getHostAddress() : "desconhecido";
    return "Servidor [" + ip + ":" + portaUDP + " (UDP)]";
  }

  /*
   * Metodo: descobrirIpLocal
   * Funcao: Detecta o IP local do cliente para enviar ao servidor
   * Parametros: nenhum
   * Retorno: endereco IP local ou um endereco de fallback
   */
  private InetAddress descobrirIpLocal() {
    try {
      Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface iface = interfaces.nextElement();
        if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
          continue;
        } // fim do if

        Enumeration<InetAddress> enderecos = iface.getInetAddresses();
        while (enderecos.hasMoreElements()) {
          InetAddress endereco = enderecos.nextElement();
          if (!endereco.isLoopbackAddress() && !endereco.isAnyLocalAddress()) {
            return endereco;
          } // fim do if
        } // fim do while
      } // fim do while
    } catch (Exception e) {
      System.out.println("CLIENTE - AVISO: Nao foi possivel detectar IP local, usando localhost como fallback.");
    } // fim do try-catch

    try {
      return InetAddress.getLocalHost();
    } catch (Exception e) {
      try {
        return InetAddress.getByName("127.0.0.1");
      } catch (Exception ex) {
        return null;
      } // fim do try-catch
    } // fim do try-catch
  }

  /*
   * Metodo: reconectarServidor
   * Funcao: Caso o servidor de contato caia fisicamente, dispara broadcast na porta 8888
   * para localizar outro Lider ativo, atualiza o ipServidor e resincroniza o estado do cliente
   */
  private synchronized boolean reconectarServidor() {
    System.out.println("CLIENTE - [FAILOVER] Servidor de contato indisponivel. Buscando novo servidor via Broadcast (8888)...");
    String novoIp = Descobridor.descobrirServidor();
    if (novoIp != null) {
      try {
        this.ipServidor = InetAddress.getByName(novoIp);
        this.PORTA_SERVIDOR_TCP = Descobridor.getUltimaPortaTCPDescoberta();
        this.PORTA_SERVIDOR_UDP = Descobridor.getUltimaPortaUDPDescoberta();
        System.out.println("CLIENTE - [FAILOVER] Novo servidor de contato definido: "
            + this.ipServidor.getHostAddress() + ":" + PORTA_SERVIDOR_TCP);
        resincronizarComServidor();
        return true;
      } catch (Exception e) {
        System.out.println("CLIENTE - [FAILOVER-ERRO] Endereco IP invalido: " + e.getMessage());
      }
    }
    return false;
  }

  /*
   * Metodo: enviarApduDiretoTCP
   * Funcao: Envia uma APDU diretamente ao ipServidor atual sem disparar failover recursivo
   */
  private void enviarApduDiretoTCP(APDU apdu, int timeout) throws Exception {
    if (this.ipServidor == null) {
      return;
    }
    try (Socket socketCliente = new Socket()) {
      socketCliente.connect(new InetSocketAddress(this.ipServidor, PORTA_SERVIDOR_TCP), timeout);
      socketCliente.setSoTimeout(timeout);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());
      saida.writeObject(apdu);
      saida.flush();
      entrada.readObject();
    }
  }

  /*
   * Metodo: resincronizarComServidor
   * Funcao: Garante que o novo Lider assumido conheca o registro (IP/porta UDP) do cliente
   * e todos os grupos ativos nos quais o cliente estava participando antes da queda
   */
  private void resincronizarComServidor() {
    try {
      APDU apduRegister = new APDU("REGISTER", null, this.nomeCliente, null, this.portaClienteUDP);
      enviarApduDiretoTCP(apduRegister, 2000);

      ArrayList<String> gruposAtivos = clienteController.obterGruposAtivos();
      for (String grupo : gruposAtivos) {
        if (grupo != null && !grupo.trim().isEmpty()) {
          APDU apduJoin = new APDU("JOIN", grupo.trim(), this.nomeCliente, null, this.portaClienteUDP);
          enviarApduDiretoTCP(apduJoin, 2000);
        }
      }
    } catch (Exception ignored) {
    }
  }

  /*
   * Metodo: enviarRequisicaoTCP
   * Funcao: Envia uma APDU via TCP para o unico servidor de contato do cliente.
   * Se houver queda/falha fisica na conexao (IOException / SocketTimeoutException),
   * executa a redescoberta via Broadcast (8888) e repete a operacao.
   */
  private String enviarRequisicaoTCP(APDU apdu, int timeout, String detalheLog) throws IOException, ClassNotFoundException {
    for (int tentativa = 1; tentativa <= 2; tentativa++) {
      try (Socket socketCliente = new Socket()) {
        if (this.ipServidor == null && !reconectarServidor()) {
          throw new IOException("Nenhum servidor disponivel na rede.");
        }

        socketCliente.connect(new InetSocketAddress(this.ipServidor, PORTA_SERVIDOR_TCP), timeout);
        socketCliente.setSoTimeout(timeout);

        ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
        saida.flush();
        ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

        String infoExtra = (detalheLog != null && !detalheLog.isEmpty()) ? " (" + detalheLog + ")" : "";
        System.out.println("CLIENTE - [ENVIO APDU] Enviando [" + apdu.getOperacao() + "]" + infoExtra
            + " para -> " + formatarDestinoServidorTCP(socketCliente));

        saida.writeObject(apdu);
        saida.flush();

        Object respostaRecebida = entrada.readObject();
        return (respostaRecebida instanceof String) ? ((String) respostaRecebida).trim() : null;
      } catch (IOException e) {
        if (tentativa == 1 && reconectarServidor()) {
          System.out.println("CLIENTE - [FAILOVER] Repetindo operacao [" + apdu.getOperacao() + "] no novo servidor...");
          continue;
        }
        throw e;
      }
    }
    return null;
  }

  /*
   * Metodo: iniciarEscutaUDP
   * Funcao: Inicia a Thread que escuta mensagens UDP (objetos APDU) recebidas do
   * servidor
   * Parametros: nenhum
   * Retorno: void
   */
  public synchronized void iniciarEscutaUDP() {
    if (escutaUDPAtiva || endpointCliente == null || endpointCliente.isClosed()) {
      return;
    } // fim do if

    escutaUDPAtiva = true;
    new Thread(() -> {
      try {
        while (!endpointCliente.isClosed()) {
          byte[] dadosEntrada = new byte[65507];
          DatagramPacket pacoteRecebido = new DatagramPacket(dadosEntrada, dadosEntrada.length);
          endpointCliente.receive(pacoteRecebido);

          InetAddress ipRemetente = pacoteRecebido.getAddress();

          try {
            // Extrai o objeto APDU serializado usando somente os bytes recebidos.
            ByteArrayInputStream bais = new ByteArrayInputStream(pacoteRecebido.getData(), 0,
                pacoteRecebido.getLength());
            ObjectInputStream in = new ObjectInputStream(bais);
            APDU apduRecebida = (APDU) in.readObject();

            // Pacote de presenca enviado por um Standby recem-promovido a Lider
            if ("CONFIRM".equals(apduRecebida.getOperacao())
                && "0".equals(apduRecebida.getIdMensagem())
                && apduRecebida.getStatusRecebido() == 0
                && "SERVIDOR".equals(apduRecebida.getNomeUsuario())) {

              String info = apduRecebida.getNomeGrupo();
              if (info != null && info.contains(":")) {
                try {
                  String[] partes = info.split(":");
                  InetAddress novoIpLider;
                  int novaPortaTCP;
                  int novaPortaUDP;

                  if (partes.length == 3) {
                    novoIpLider = InetAddress.getByName(partes[0].trim());
                    novaPortaTCP = Integer.parseInt(partes[1].trim());
                    novaPortaUDP = Integer.parseInt(partes[2].trim());
                  } else {
                    novoIpLider = ipRemetente;
                    novaPortaTCP = Integer.parseInt(partes[0].trim());
                    novaPortaUDP = Integer.parseInt(partes[1].trim());
                  }

                  this.ipServidor = novoIpLider;
                  this.PORTA_SERVIDOR_TCP = novaPortaTCP;
                  this.PORTA_SERVIDOR_UDP = novaPortaUDP;

                  System.out.println("CLIENTE - [NOVA LIDERANCA] Ponto de contato atualizado para o novo Lider em: "
                      + this.ipServidor.getHostAddress() + ":" + this.PORTA_SERVIDOR_TCP);

                  new Thread(this::resincronizarComServidor).start();
                } catch (Exception e) {
                  System.err.println("CLIENTE - Erro ao processar nova lideranca: " + e.getMessage());
                }
              }
              continue;
            }

            System.out.println("CLIENTE - Recebeu APDU: " + apduRecebida.getOperacao());

            new Thread(() -> {
              processarApdu(apduRecebida);
            }).start();
          } catch (Exception e) {
            System.out.println("CLIENTE - AVISO: Pacote UDP invalido ignorado.");
          }
        } // fim do while
      } catch (java.net.SocketException e) {
        if (endpointCliente != null && endpointCliente.isClosed()) {
          System.out.println("CLIENTE - Escuta UDP encerrada pelo usuario (Logout).");
        } else {
          System.out.println("CLIENTE - ERRO: socket UDP foi fechado inesperadamente.");
        }
      } catch (Exception e) {
        System.out.println("CLIENTE - ERRO ao receber a mensagem!");
        e.printStackTrace();
      } finally {
        escutaUDPAtiva = false;
      } // fim do try-catch
    }).start();
  } // fim do metodo iniciarEscutaUDP

  /*
   * Metodo: processarApdu
   * Funcao: Processar a APDU recebida via UDP e direcionar para o controller
   * atualizar a interface
   * Parametros: apduRecebida = objeto APDU deserializado recebido do servidor
   * Retorno: void
   */
  private void processarApdu(APDU apdu) {
    String operacao = apdu.getOperacao();
    switch (operacao) {
      case "SEND":
        enviarConfirmacao(apdu.getIdMensagem(), 2, apdu.getNomeGrupo(), apdu.getNomeUsuario());
        Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeGrupo(),
            apdu.getNomeUsuario(), GRUPO, false, apdu.getIdMensagem());
        break;

      case "SENDPVT":
        enviarConfirmacao(apdu.getIdMensagem(), 2, null, apdu.getNomeUsuario());
        Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeUsuario(),
            apdu.getNomeUsuario(), PRIVADO, apdu.isVisualizacaoUnica(), apdu.getIdMensagem());
        break;

      case "SENDVU":
        if (apdu.getDestinatario() != null) {
          enviarConfirmacao(apdu.getIdMensagem(), 2, null, apdu.getNomeUsuario());
          Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeUsuario(),
              apdu.getNomeUsuario(), PRIVADO, true, apdu.getIdMensagem());
        } else {
          enviarConfirmacao(apdu.getIdMensagem(), 2, apdu.getNomeGrupo(), apdu.getNomeUsuario());
          Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeGrupo(),
              apdu.getNomeUsuario(), GRUPO, true, apdu.getIdMensagem());
        } // fim do if
        break;

      case "CONFIRM":
        if (apdu.getIdMensagem() != null) {
          mensagensAguardandoTick1.remove(apdu.getIdMensagem());
        }
        System.out.println("CLIENTE - Recebeu confirmacao (Tick): MsgID " + apdu.getIdMensagem() + " Status "
            + apdu.getStatusRecebido());
        Controller.clienteController.atualizarStatusMensagem(apdu.getIdMensagem(), apdu.getStatusRecebido());
        break;
    } // fim do switch-case
  } // fim do metodo processarApdu

  /*
   * Metodo: entrarGrupo
   * Funcao: Envia o objeto APDU de JOIN para o servidor conectado e aguarda a confirmacao
   * Parametros: grupo = nome do grupo que o usuario deseja entrar
   * Retorno: boolean indicando se a entrada foi aprovada
   */
  public boolean entrarGrupo(String grupo) {
    String grupoNormalizado = grupo != null ? grupo.trim() : "";
    if (grupoNormalizado.isEmpty()) {
      System.out.println("CLIENTE - ERRO: Nome do grupo vazio.");
      return false;
    } // fim do if

    try {
      APDU apdu = new APDU("JOIN", grupoNormalizado, this.nomeCliente.trim(), null, this.portaClienteUDP);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, "grupo='" + grupoNormalizado + "'");
      return resposta != null && resposta.startsWith("OK:");
    } catch (SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido ao tentar entrar no grupo '" + grupoNormalizado + "'.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no JOIN do grupo '" + grupoNormalizado + "': " + e.getMessage());
      return false;
    } // fim do try-catch
  } // fim do metodo entrarGrupo

  /*
   * Metodo: sairGrupo
   * Funcao: Envia o objeto APDU de LEAVE para o servidor conectado e aguarda a confirmacao
   * Parametros: grupo = nome do grupo que o usuario deseja sair
   * Retorno: boolean indicando se a saida foi concluida
   */
  public boolean sairGrupo(String grupo) {
    try {
      APDU apdu = new APDU("LEAVE", grupo, this.nomeCliente, null, this.portaClienteUDP);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, "grupo='" + grupo + "'");
      return resposta != null && resposta.startsWith("OK:");
    } catch (SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido ao tentar sair do grupo '" + grupo + "'.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no LEAVE do grupo '" + grupo + "': " + e.getMessage());
      return false;
    } // fim do try-catch
  } // fim do metodo sairGrupo

  /*
   * Metodo: enviarObjetoUDP
   * Funcao: Serializa e envia uma APDU diretamente para o servidor conectado via UDP
   * Parametros: apdu = objeto a ser enviado
   * Retorno: void
   */
  private void enviarObjetoUDP(APDU apdu) {
    try {
      if (this.ipServidor == null && !reconectarServidor()) {
        System.out.println("CLIENTE - ERRO: Sem servidor de contato para envio UDP.");
        return;
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(baos);
      out.writeObject(apdu);
      out.flush();
      byte[] dados = baos.toByteArray();

      DatagramPacket pacote = new DatagramPacket(dados, dados.length, this.ipServidor, PORTA_SERVIDOR_UDP);
      endpointCliente.send(pacote);
      System.out.println("CLIENTE - [ENVIO APDU] Enviando [" + apdu.getOperacao() + "] para -> "
          + formatarDestinoServidorUDP(this.ipServidor, PORTA_SERVIDOR_UDP));
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO ao encaminhar objeto via UDP: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo enviarObjetoUDP

  /*
   * Metodo: iniciarWatchdogTick1
   * Funcao: Monitora se a mensagem UDP enviada recebeu o TICK 1 do servidor.
   * Caso o servidor tenha caido (ou esteja em transicao de failover) e nao responda dentro
   * do tempo limite, aciona reconectarServidor() e reenvia a APDU automaticamente.
   */
  private void iniciarWatchdogTick1(APDU apdu) {
    final String idMsg = apdu.getIdMensagem();
    if (idMsg == null) {
      return;
    }
    mensagensAguardandoTick1.add(idMsg);
    new Thread(() -> {
      try {
        Thread.sleep(TIMEOUT_TICK1_UDP_MS);
        if (mensagensAguardandoTick1.remove(idMsg) && !logoutRealizado) {
          System.out.println("CLIENTE - [FAILOVER-UDP] Sem confirmacao (TICK 1) para MsgID " + idMsg
              + ". Reconectando ao servidor e reenviando mensagem...");
          if (reconectarServidor()) {
            enviarObjetoUDP(apdu);
          }
        }
      } catch (InterruptedException ignored) {
      }
    }, "Watchdog-Tick1-" + idMsg).start();
  }

  /*
   * Metodo: enviarMensagem
   * Funcao: Envia uma mensagem para o grupo utilizando o objeto APDU
   * Parametros: grupo = nome do grupo alvo, mensagem = texto da mensagem
   * Retorno: String
   */
  public String enviarMensagem(String grupo, String mensagem, boolean isVisuUnica) {
    try {
      String operacao = isVisuUnica ? "SENDVU" : "SEND";
      APDU apdu = new APDU(operacao, grupo, this.nomeCliente, mensagem, this.portaClienteUDP);
      iniciarWatchdogTick1(apdu);
      enviarObjetoUDP(apdu);
      return apdu.getIdMensagem();
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem!");
      return null;
    } // fim do try-catch
  } // fim do metodo enviarMensagem

  /*
   * Metodo: enviarMensagemPrivado
   * Funcao: Envia uma mensagem privada utilizando o objeto APDU
   * Parametros: usuarioDestino = usuario que vai receber, mensagem = texto
   * Retorno: String com o ID da mensagem ou null em caso de erro
   */
  public String enviarMensagemPrivado(String usuarioDestino, String mensagem, boolean isVisuUnica) {
    try {
      APDU apdu = new APDU("SENDPVT", null, this.nomeCliente, mensagem, this.portaClienteUDP, usuarioDestino);
      apdu.setVisualizacaoUnica(isVisuUnica);
      iniciarWatchdogTick1(apdu);
      enviarObjetoUDP(apdu);
      return apdu.getIdMensagem();
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem privada!");
      return null;
    } // fim do try-catch
  } // fim do metodo enviarMensagemPrivado

  /*
   * Metodo: fazerLogin
   * Funcao: Conecta via TCP ao servidor de contato e envia a APDU REGISTER
   * Parametros: nenhum
   * Retorno: boolean (true se o login for aprovado, false caso contrario)
   */
  public boolean fazerLogin() {
    try {
      APDU apduLogin = new APDU("REGISTER", null, this.nomeCliente, null, this.portaClienteUDP);
      String resposta = enviarRequisicaoTCP(apduLogin, TIMEOUT_TCP, "usuario='" + this.nomeCliente + "'");
      return resposta != null && resposta.startsWith("OK: registrado");
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no LOGIN: " + e.getMessage());
      return false;
    }
  } // fim do metodo fazerLogin

  /*
   * Metodo: fazerLogout
   * Funcao: Envia a APDU LOGOUT para o servidor conectado (que propaga a remocao para todo o cluster)
   * Parametros: nenhum
   * Retorno: void
   */
  public synchronized void fazerLogout() {
    if (logoutRealizado) {
      return;
    }
    logoutRealizado = true;

    try {
      APDU apduLogout = new APDU("LOGOUT", null, this.nomeCliente, null, this.portaClienteUDP);
      enviarRequisicaoTCP(apduLogout, TIMEOUT_LOGOUT, "usuario='" + this.nomeCliente + "'");
    } catch (Exception ignored) {
    }
  } // fim do metodo fazerLogout

  /*
   * Metodo: solicitarListaGrupos
   * Funcao: Envia um unico comando LIST para o servidor conectado (que consolida as 3 particoes internamente)
   * Parametros: nenhum
   * Retorno: void
   */
  public void solicitarListaGrupos() {
    try {
      APDU apdu = new APDU("LIST", null, this.nomeCliente, null, this.portaClienteUDP);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, null);
      if (resposta != null && resposta.startsWith("OK:")) {
        ArrayList<String> grupos = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(grupos, GRUPO);
      } else {
        clienteController.exibirListaConversas(new ArrayList<>(), GRUPO);
      }
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar a lista de grupos: " + e.getMessage());
      clienteController.exibirListaConversas(new ArrayList<>(), GRUPO);
    }
  } // fim do solicitarListaGrupos

  /*
   * Metodo: solicitarListaMembros
   * Funcao: Envia a APDU MEMBERS ao servidor conectado para listar os integrantes do grupo
   * Parametros: grupo = nome do grupo
   * Retorno: void
   */
  public void solicitarListaMembros(String grupo) {
    try {
      APDU apdu = new APDU("MEMBERS", grupo, this.nomeCliente, null, this.portaClienteUDP);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, "grupo='" + grupo + "'");
      if (resposta != null && resposta.startsWith("OK:")) {
        ArrayList<String> membros = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(membros, PRIVADO);
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar membros do grupo '" + grupo + "'!");
    } // fim do try-catch
  } // fim do solicitarListaMembros

  /*
   * Metodo: solicitarListaUsuarios
   * Funcao: Envia a APDU USERS ao servidor conectado para listar todos os usuarios online
   * Parametros: nenhum
   * Retorno: void
   */
  public void solicitarListaUsuarios() {
    solicitarListaUsuariosPorModo(PRIVADO);
  } // fim do metodo solicitarListaUsuarios

  /*
   * Metodo: solicitarListaUsuariosBloqueio
   * Funcao: Solicita a lista de usuarios online ao servidor para abrir na tela de Gerenciar Bloqueios
   * Parametros: nenhum
   * Retorno: void
   */
  public void solicitarListaUsuariosBloqueio() {
    solicitarListaUsuariosPorModo(BLOQUEIO);
  } // fim do metodo solicitarListaUsuariosBloqueio

  /*
   * Metodo: solicitarListaUsuariosPorModo
   * Funcao: Consulta a APDU USERS no servidor conectado e encaminha para exibirListaConversas com o modo desejado
   */
  private void solicitarListaUsuariosPorModo(String modoExibicao) {
    try {
      APDU apdu = new APDU("USERS", null, this.nomeCliente, null, this.portaClienteUDP);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, "solicitante='" + this.nomeCliente + "'");
      if (resposta != null && resposta.startsWith("OK:")) {
        ArrayList<String> usuarios = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(usuarios, modoExibicao);
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar usuarios online!");
    } // fim do try-catch
  } // fim do metodo solicitarListaUsuariosPorModo

  /*
   * Metodo: extrairListaDaResposta
   * Funcao: Utilitario para transformar a String "OK: item1,item2," num ArrayList
   * de Strings
   * Parametros: resposta = String retornada pelo servidor TCP
   * Retorno: ArrayList<String> com os itens processados
   */
  private ArrayList<String> extrairListaDaResposta(String resposta) {
    ArrayList<String> lista = new ArrayList<>();
    String conteudo = resposta.length() > 3 ? resposta.substring(3).trim() : "";

    if (!conteudo.isEmpty()) {
      String[] itens = conteudo.split(",");
      for (String item : itens) {
        if (!item.trim().isEmpty()) {
          lista.add(item.trim());
        } // fim do if
      } // fim do for
    } // fim do if

    return lista;
  } // fim do metodo extrairListaDaResposta

  /*
   * Metodo: verificarUsuario
   * Funcao: Pergunta ao servidor conectado a lista de usuarios online para checar se o alvo existe
   * Parametros: nomeUsuarioDestino = nome do usuario que desejamos verificar
   * Retorno: boolean indicando se o usuario esta online
   */
  public boolean verificarUsuario(String nomeUsuarioDestino) {
    try {
      APDU apdu = new APDU("USERS", null, this.nomeCliente, null, this.portaClienteUDP);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, "verificando='" + nomeUsuarioDestino + "'");
      if (resposta != null && resposta.startsWith("OK:")) {
        ArrayList<String> usuarios = extrairListaDaResposta(resposta);
        return usuarios.contains(nomeUsuarioDestino.trim());
      } // fim do if
      return false;
    } catch (SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido ao verificar usuario.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha ao verificar usuario: " + e.getMessage());
      return false;
    } // fim do try-catch
  } // fim do metodo verificarUsuario

  /*
   * Metodo: bloquearUsuario
   * Funcao: Envia APDU BLOCK para o servidor conectado
   * Parametros: usuarioBloqueado = nome do usuario a bloquear
   * Retorno: boolean
   */
  public boolean bloquearUsuario(String usuarioBloqueado) {
    try {
      APDU apdu = new APDU("BLOCK", null, this.nomeCliente, null, this.portaClienteUDP, usuarioBloqueado);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, "'" + this.nomeCliente + "' -> '" + usuarioBloqueado + "'");
      return resposta != null && resposta.startsWith("OK:");
    } catch (SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido no BLOCK.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor no BLOCK!");
      return false;
    } // fim do try-catch
  } // fim do metodo bloquearUsuario

  /*
   * Metodo: desbloquearUsuario
   * Funcao: Envia APDU UNBLOCK para o servidor conectado
   * Parametros: usuarioDesbloqueado = nome do usuario a desbloquear
   * Retorno: boolean
   */
  public boolean desbloquearUsuario(String usuarioDesbloqueado) {
    try {
      APDU apdu = new APDU("UNBLOCK", null, this.nomeCliente, null, this.portaClienteUDP, usuarioDesbloqueado);
      String resposta = enviarRequisicaoTCP(apdu, TIMEOUT_TCP, "'" + this.nomeCliente + "' -> '" + usuarioDesbloqueado + "'");
      return resposta != null && resposta.startsWith("OK:");
    } catch (SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido no UNBLOCK.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor no UNBLOCK!");
      return false;
    } // fim do try-catch
  } // fim do metodo desbloquearUsuario

  /*
   * Metodo: enviarConfirmacao
   * Funcao: Monta e envia uma APDU de confirmacao (Status 2 ou 3) de volta ao servidor conectado
   * Parametros: idMensagem = ID unico da mensagem, status = 2 (entregue) ou 3 (lido),
   * nomeGrupo = onde a msg foi enviada, donoDaMensagem = quem enviou originalmente
   * Retorno: void
   */
  public void enviarConfirmacao(String idMensagem, int status, String nomeGrupo, String donoDaMensagem) {
    if (idMensagem == null || idMensagem.trim().isEmpty()) {
      return;
    }

    try {
      APDU apduConfirm = new APDU("CONFIRM", idMensagem, status, this.nomeCliente, nomeGrupo, donoDaMensagem);
      enviarObjetoUDP(apduConfirm);
      System.out.println("CLIENTE - Enviando tick (Status " + status + ") para a mensagem ID: " + idMensagem);
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO ao enviar confirmacao!");
    }
  } // fim do metodo enviarConfirmacao

  /*
   * Metodo: desligarCliente
   * Funcao: Encerra a escuta UDP e fecha o socket do cliente
   * Parametros: nenhum
   * Retorno: void
   */
  public void desligarCliente() {
    logoutRealizado = true;
    escutaUDPAtiva = false;
    if (endpointCliente != null && !endpointCliente.isClosed()) {
      endpointCliente.close();
    }
  }

  public int getPORTA_SERVIDOR_UDP() {
    return PORTA_SERVIDOR_UDP;
  }

  public int getPORTA_SERVIDOR_TCP() {
    return PORTA_SERVIDOR_TCP;
  }

  public int getPortaClienteUDP() {
    return portaClienteUDP;
  }

  public String getGRUPO() {
    return GRUPO;
  }

  public String getPRIVADO() {
    return PRIVADO;
  }

  public String getNomeCliente() {
    return nomeCliente;
  }

  public void setNomeCliente(String nomeCliente) {
    this.nomeCliente = nomeCliente;
  }

  public InetAddress getIpCliente() {
    return ipCliente;
  }

  public void setIpCliente(InetAddress ipCliente) {
    this.ipCliente = ipCliente;
  }

  public InetAddress getIpServidor() {
    return ipServidor;
  }

  public void setIpServidor(InetAddress ipServidor) {
    this.ipServidor = ipServidor;
  }

  public DatagramSocket getEndpointCliente() {
    return endpointCliente;
  }

  public void setEndpointCliente(DatagramSocket endpointCliente) {
    this.endpointCliente = endpointCliente;
  }

}
