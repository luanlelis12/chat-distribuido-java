/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 20/09/2026
* Nome.............: Cliente.java
* Funcao...........: Gerencia as apdus e a comunicacao com o servidor
*******************************************************************/
package Model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;

import Controller.clienteController;

import java.nio.charset.StandardCharsets;

import Protocol.APDU;

public class Cliente extends Thread {

  public static final int PORTA_SERVIDOR_UDP_BASE = 7777;
  public static final int PORTA_SERVIDOR_TCP_BASE = 6789;
  private final int PORTA_SERVIDOR_UDP = PORTA_SERVIDOR_UDP_BASE;
  private final int PORTA_SERVIDOR_TCP = PORTA_SERVIDOR_TCP_BASE;
  private final int TIMEOUT_TCP = 5000;
  private final int TIMEOUT_LIST_CONJUNTO = 2000;
  private final int TIMEOUT_LOGOUT = 1000;

  private int portaClienteUDP;

  private final String GRUPO = "grupo";
  private final String PRIVADO = "priv";

  private String nomeCliente;
  private InetAddress ipCliente;
  private InetAddress ipServidor;
  private final InetAddress[] ipsConjunto = new InetAddress[4];
  private DatagramSocket endpointCliente;
  private volatile boolean escutaUDPAtiva = false;

  /*
   * Metodo: calcularConjuntoPorNome
   * Funcao: Determina a qual conjunto (1, 2 ou 3) uma chave (nomeGrupo, nomeCliente ou destinatario)
   * pertence com base na sua primeira letra:
   * - Conjunto 1: [A..I]
   * - Conjunto 2: [J..R]
   * - Conjunto 3: [S..Z]
   */
  public static int calcularConjuntoPorNome(String chave) {
    if (chave == null || chave.trim().isEmpty()) {
      return 1;
    }
    char inicial = Character.toUpperCase(chave.trim().charAt(0));
    if (inicial >= 'A' && inicial <= 'I') {
      return 1; // Conjunto 1: [A..I]
    } else if (inicial >= 'J' && inicial <= 'R') {
      return 2; // Conjunto 2: [J..R]
    } else if (inicial >= 'S' && inicial <= 'Z') {
      return 3; // Conjunto 3: [S..Z]
    }
    return 1; // Fallback padrao para Conjunto 1
  }

  /*
   * Metodo: obterFaixaConjunto
   * Funcao: Retorna a representacao textual da faixa de letras do conjunto (1, 2 ou 3)
   */
  public static String obterFaixaConjunto(int conjunto) {
    switch (conjunto) {
      case 1:
        return "[A..I]";
      case 2:
        return "[J..R]";
      case 3:
        return "[S..Z]";
      default:
        return "[A..I]";
    }
  }

  /*
   * Metodo: calcularPortaTCPPorConjunto
   * Funcao: Retorna a porta TCP do lider do conjunto informado (6789, 6790 ou 6791)
   */
  public static int calcularPortaTCPPorConjunto(int conjunto) {
    return PORTA_SERVIDOR_TCP_BASE + (conjunto - 1);
  }

  /*
   * Metodo: calcularPortaUDPPorConjunto
   * Funcao: Retorna a porta UDP do lider do conjunto informado (7777, 7778 ou 7779)
   */
  public static int calcularPortaUDPPorConjunto(int conjunto) {
    return PORTA_SERVIDOR_UDP_BASE + (conjunto - 1);
  }

  /*
   * Metodo: calcularPortaTCPPorNome
   * Funcao: Calcula diretamente a porta TCP (6789, 6790 ou 6791) a partir da letra inicial do nome
   */
  public static int calcularPortaTCPPorNome(String chave) {
    int conjunto = calcularConjuntoPorNome(chave);
    return calcularPortaTCPPorConjunto(conjunto);
  }

  /*
   * Metodo: calcularPortaUDPPorNome
   * Funcao: Calcula diretamente a porta UDP (7777, 7778 ou 7779) a partir da letra inicial do nome
   */
  public static int calcularPortaUDPPorNome(String chave) {
    int conjunto = calcularConjuntoPorNome(chave);
    return calcularPortaUDPPorConjunto(conjunto);
  }

  /*
   * Metodo: obterChaveRoteamentoAPDU
   * Funcao: Determina qual string define o conjunto de destino de uma APDU conforme a regra de negocio:
   * - Operacoes de Grupo (JOIN, LEAVE, MEMBERS, SEND, SENDVU de grupo): primeira letra de nomeGrupo
   * - Mensagem Privada (SENDPVT, SENDVU privado): primeira letra de destinatario
   * - Operacoes Globais de Utilizador (REGISTER, LOGOUT, BLOCK, UNBLOCK, USERS): primeira letra de nomeUsuario
   * - Confirmacao (CONFIRM): nomeGrupo (se mensagem de grupo) ou nomeUsuario (se mensagem privada recebida)
   */
  public String obterChaveRoteamentoAPDU(APDU apdu) {
    if (apdu == null || apdu.getOperacao() == null) {
      return this.nomeCliente;
    }
    String op = apdu.getOperacao();
    switch (op) {
      case "JOIN":
      case "LEAVE":
      case "MEMBERS":
      case "SEND":
        return apdu.getNomeGrupo();
      case "SENDPVT":
        return apdu.getDestinatario();
      case "SENDVU":
        return (apdu.getDestinatario() != null && !apdu.getDestinatario().trim().isEmpty())
            ? apdu.getDestinatario()
            : apdu.getNomeGrupo();
      case "CONFIRM":
        return (apdu.getNomeGrupo() != null && !apdu.getNomeGrupo().trim().isEmpty())
            ? apdu.getNomeGrupo()
            : this.nomeCliente;
      case "REGISTER":
      case "LOGOUT":
      case "BLOCK":
      case "UNBLOCK":
      case "USERS":
      default:
        return this.nomeCliente;
    }
  }

  /*
   * Metodo: Cliente
   * Funcao: Inicializa o cliente e abre a porta UDP local
   * Parametros: nomeCliente = nome do usuario, ipServidor = IP do servidor
   * Retorno: void
   */
  public Cliente(String nomeCliente, String ipServidor) {
    try {
      this.nomeCliente = nomeCliente;
      this.ipCliente = descobrirIpLocal();
      this.ipServidor = InetAddress.getByName(ipServidor);

      this.portaClienteUDP = 5000 + (int) (Math.random() * 1000);
      this.endpointCliente = new DatagramSocket(portaClienteUDP);

      System.out.println("CLIENTE estabelecido: nome = " + nomeCliente + " / ip = " + ipCliente);
    } catch (Exception e) {
      System.out.println("ERRO: Nao foi possivel inicializar o cliente. " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do construtor

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

          // Atualiza dinamicamente o IP do lider do conjunto correspondente se o pacote veio de outro IP
          InetAddress ipRemetente = pacoteRecebido.getAddress();
          int portaRemetente = pacoteRecebido.getPort();
          if (ipRemetente != null) {
            int conjRem = (portaRemetente - PORTA_SERVIDOR_UDP_BASE) + 1;
            if (conjRem >= 1 && conjRem <= 3) {
              ipsConjunto[conjRem] = ipRemetente;
            }
            if (this.ipServidor == null || !ipRemetente.equals(this.ipServidor)) {
              System.out.println("CLIENTE - Detectada comunicacao do Lider em: " + ipRemetente.getHostAddress());
              this.ipServidor = ipRemetente;
            }
          }

          try {
            // Extrai o objeto APDU serializado usando somente os bytes recebidos.
            ByteArrayInputStream bais = new ByteArrayInputStream(pacoteRecebido.getData(), 0,
                pacoteRecebido.getLength());
            ObjectInputStream in = new ObjectInputStream(bais);
            APDU apduRecebida = (APDU) in.readObject();

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
        Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeGrupo(), apdu.getNomeUsuario(), GRUPO, false, apdu.getIdMensagem());  
        break;

      case "SENDPVT":
        enviarConfirmacao(apdu.getIdMensagem(), 2, null, apdu.getNomeUsuario());
        Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeUsuario(), apdu.getNomeUsuario(), PRIVADO,
            apdu.isVisualizacaoUnica(), apdu.getIdMensagem());
        break;

      case "SENDVU":
        if (apdu.getDestinatario() != null) {
          enviarConfirmacao(apdu.getIdMensagem(), 2, null, apdu.getNomeUsuario());
          Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeUsuario(), apdu.getNomeUsuario(), PRIVADO, true, apdu.getIdMensagem());
        } else {
          enviarConfirmacao(apdu.getIdMensagem(), 2, apdu.getNomeGrupo(), apdu.getNomeUsuario());
          Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeGrupo(), apdu.getNomeUsuario(), GRUPO, true, apdu.getIdMensagem());
        } // fim do if
        break;
        
      case "CONFIRM":
        System.out.println("CLIENTE - Recebeu confirmacao (Tick): MsgID " + apdu.getIdMensagem() + " Status " + apdu.getStatusRecebido());
        Controller.clienteController.atualizarStatusMensagem(apdu.getIdMensagem(), apdu.getStatusRecebido());
        break;
    } // fim do switch-case
  } // fim do metodo processarApdu

  /*
   * Metodo: entrarGrupo
   * Funcao: Conecta via TCP ao Lider do conjunto correspondente a letra inicial do grupo,
   * envia o objeto APDU de JOIN e aguarda a confirmacao do servidor
   * Parametros: grupo = nome do grupo que o usuario deseja entrar
   * Retorno: boolean indicando se a entrada foi aprovada
   */
  public boolean entrarGrupo(String grupo) {
    String grupoNormalizado = grupo != null ? grupo.trim() : "";
    if (grupoNormalizado.isEmpty()) {
      System.out.println("CLIENTE - ERRO: Nome do grupo vazio.");
      return false;
    } // fim do if

    int conjunto = calcularConjuntoPorNome(grupoNormalizado);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try {
      try (Socket socketCliente = abrirSocketTCP(portaTCP, TIMEOUT_TCP)) {

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("JOIN", grupoNormalizado, this.nomeCliente.trim(), null, this.portaClienteUDP);

      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU JOIN ('" + grupoNormalizado
          + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + portaTCP + ")...");
      saida.writeObject(apdu);
      saida.flush();

      Object respostaRecebida = entrada.readObject();
      socketCliente.close();

      if (!(respostaRecebida instanceof String)) {
        return false;
      } // fim do if

      String resposta = ((String) respostaRecebida).trim();
      return resposta.startsWith("OK:");
      }

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Lider do Conjunto "
          + conjunto + " (TCP " + portaTCP + ") nao respondeu ao JOIN.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no JOIN (Conjunto " + conjunto + " / TCP " + portaTCP + "): " + e.getMessage());
      return false;
    } // fim do try-catch
  } // fim do metodo entrarGrupo

  /*
   * Metodo: sairGrupo
   * Funcao: Conecta via TCP ao Lider do conjunto correspondente a letra inicial do grupo,
   * envia o objeto APDU de LEAVE e aguarda a confirmacao do servidor
   * Parametros: grupo = nome do grupo que o usuario deseja sair
   * Retorno: boolean indicando se a saida foi concluida
   */
  public boolean sairGrupo(String grupo) {
    int conjunto = calcularConjuntoPorNome(grupo);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try {
      try (Socket socketCliente = abrirSocketTCP(portaTCP, TIMEOUT_TCP)) {

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      // Instancia a APDU do colega para o LEAVE
      APDU apdu = new APDU("LEAVE", grupo, this.nomeCliente, null, this.portaClienteUDP);

      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU LEAVE ('" + grupo
          + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + portaTCP + ")...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      // O novo servidor responde "OK: Saiu do grupo com sucesso"
      return resposta != null && resposta.startsWith("OK:");
      }

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Lider do Conjunto "
          + conjunto + " (TCP " + portaTCP + ") nao respondeu ao LEAVE.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no LEAVE (Conjunto " + conjunto + " / TCP " + portaTCP + "): " + e.getMessage());
      return false;
    } // fim do try-catch
  } // fim do metodo sairGrupo

  /*
   * Metodo: abrirSocketTCP (padrao baseado no nomeCliente)
   * Funcao: Abre uma conexao TCP para o conjunto correspondente ao proprio cliente
   */
  private Socket abrirSocketTCP(int timeout) throws java.io.IOException {
    return abrirSocketTCP(calcularPortaTCPPorNome(this.nomeCliente), timeout);
  }

  /*
   * Metodo: abrirSocketTCP
   * Funcao: Abre uma conexao TCP na porta especificada do conjunto alvo com timeout de conexao e leitura.
   * Suporta tanto 3 servidores na mesma maquina (localhost/LAN) quanto em IPs separados.
   * Parametros: portaTCP = porta TCP do lider do conjunto (6789, 6790 ou 6791), timeout = limite em ms
   * Retorno: socket TCP conectado
   */
  private Socket abrirSocketTCP(int portaTCP, int timeout) throws java.io.IOException {
    int conjunto = (portaTCP - PORTA_SERVIDOR_TCP_BASE) + 1;
    InetAddress ipAlvo = (conjunto >= 1 && conjunto <= 3 && ipsConjunto[conjunto] != null)
        ? ipsConjunto[conjunto]
        : ipServidor;

    // 1. Tenta conectar no IP ja conhecido para este conjunto (ou ipServidor padrao)
    if (ipAlvo != null) {
      try {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ipAlvo, portaTCP), Math.min(timeout, 1500));
        socket.setSoTimeout(timeout);
        if (conjunto >= 1 && conjunto <= 3) {
          ipsConjunto[conjunto] = ipAlvo;
        }
        return socket;
      } catch (java.io.IOException ignored) {
      }
    }

    // 2. Tentativa rapida em localhost (127.0.0.1) na porta do conjunto
    try {
      InetAddress localAddr = InetAddress.getByName("127.0.0.1");
      Socket socket = new Socket();
      socket.connect(new InetSocketAddress(localAddr, portaTCP), 400);
      socket.setSoTimeout(timeout);
      if (conjunto >= 1 && conjunto <= 3) {
        ipsConjunto[conjunto] = localAddr;
      }
      this.ipServidor = localAddr;
      return socket;
    } catch (java.io.IOException ignored) {
    }

    // 3. Descobre na rede qual IP possui especificamente esta portaTCP (6789, 6790 ou 6791) aberta
    System.out.println("CLIENTE - Procurando Lider ativo para a porta TCP " + portaTCP + " no cluster...");
    String novoIp = Network.Descobridor.descobrirServidor(portaTCP);
    if (novoIp != null) {
      try {
        InetAddress addrDescoberto = InetAddress.getByName(novoIp);
        System.out.println("CLIENTE - Lider localizado em: " + novoIp + ":" + portaTCP + ". Conectando...");
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(addrDescoberto, portaTCP), timeout);
        socket.setSoTimeout(timeout);
        if (conjunto >= 1 && conjunto <= 3) {
          ipsConjunto[conjunto] = addrDescoberto;
        }
        this.ipServidor = addrDescoberto;
        return socket;
      } catch (Exception ex) {
        throw new java.io.IOException("Falha ao conectar ao lider (" + novoIp + ":" + portaTCP + "): " + ex.getMessage(), ex);
      }
    }
    throw new java.io.IOException("Nenhum Lider respondeu na porta TCP " + portaTCP);
  }

  /*
   * Metodo: abrirSocketTCPGlobal
   * Funcao: Para operacoes GLOBAIS de utilizador (REGISTER, LOGOUT, USERS, BLOCK, UNBLOCK),
   * tenta primeiro o Lider do conjunto correspondente a 1a letra do nome. Se esse conjunto
   * nao tiver nenhum servidor ativo no momento, faz fallback automatico para outro Lider ativo
   * (ja que usuariosOnline e replicado globalmente entre os conjuntos).
   */
  private Socket abrirSocketTCPGlobal(int portaTCPPreferencial, int timeout) throws java.io.IOException {
    try {
      return abrirSocketTCP(portaTCPPreferencial, timeout);
    } catch (java.io.IOException erroPrincipal) {
      for (int c = 1; c <= 3; c++) {
        int portaAlternativa = calcularPortaTCPPorConjunto(c);
        if (portaAlternativa == portaTCPPreferencial) {
          continue;
        }
        try {
          Socket s = abrirSocketTCP(portaAlternativa, 1200);
          System.out.println("CLIENTE - [FALLBACK GLOBAL] Conjunto preferencial (TCP " + portaTCPPreferencial
              + ") indisponivel; utilizando Lider ativo do Conjunto " + c + " (TCP " + portaAlternativa + ").");
          return s;
        } catch (java.io.IOException ignored) {
        }
      }
      throw erroPrincipal;
    }
  }

  /*
   * Metodo: obterIpParaConjunto
   * Funcao: Retorna o InetAddress do Lider do conjunto informado (descobrindo-o se necessario)
   */
  private InetAddress obterIpParaConjunto(int conjunto) {
    if (conjunto >= 1 && conjunto <= 3 && ipsConjunto[conjunto] != null) {
      return ipsConjunto[conjunto];
    }
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);
    String ipDesc = Network.Descobridor.descobrirServidor(portaTCP);
    if (ipDesc != null) {
      try {
        InetAddress addr = InetAddress.getByName(ipDesc);
        if (conjunto >= 1 && conjunto <= 3) {
          ipsConjunto[conjunto] = addr;
        }
        return addr;
      } catch (Exception ignored) {
      }
    }
    return ipServidor;
  }

  /*
   * Metodo: enviarObjetoUDP
   * Funcao: Serializa e envia uma APDU para o lider do conjunto correspondente via UDP
   * Parametros: apdu = objeto a ser enviado
   * Retorno: void
   */
  private void enviarObjetoUDP(APDU apdu) {
    try {
      String chaveRoteamento = obterChaveRoteamentoAPDU(apdu);
      int conjunto = calcularConjuntoPorNome(chaveRoteamento);
      int portaUDP = calcularPortaUDPPorConjunto(conjunto);
      InetAddress ipDestino = obterIpParaConjunto(conjunto);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(baos);
      out.writeObject(apdu);
      out.flush();
      byte[] dados = baos.toByteArray();

      DatagramPacket pacote = new DatagramPacket(dados, dados.length, ipDestino, portaUDP);
      endpointCliente.send(pacote);
      System.out.println("CLIENTE - [ROTEAMENTO UDP] APDU [" + apdu.getOperacao() + "] chave='"
          + chaveRoteamento + "' -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto)
          + " (" + (ipDestino != null ? ipDestino.getHostAddress() : "null") + ":" + portaUDP + ")");
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO ao encaminhar objeto via UDP.");
    } // fim do try-catch
  } // fim do metodo enviarObjetoUDP

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
      enviarObjetoUDP(apdu);
      System.out.println("CLIENTE - Enviando APDU " + operacao + " para o servidor");
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
      enviarObjetoUDP(apdu);
      System.out.println("CLIENTE - Enviando APDU SENDPVT para o servidor");
      return apdu.getIdMensagem();
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem privada!");
      return null;
    } // fim do try-catch
  } // fim do metodo enviarMensagemPrivado

  /*
   * Metodo: fazerLogin
   * Funcao: Conecta via TCP ao Lider do conjunto correspondente a 1a letra de nomeCliente
   * e envia a APDU REGISTER
   * Parametros: nenhum
   * Retorno: boolean (true se o login for aprovado, false caso contrario)
   */
  public boolean fazerLogin() {
    int conjunto = calcularConjuntoPorNome(this.nomeCliente);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try {
      try (Socket socketCliente = abrirSocketTCPGlobal(portaTCP, TIMEOUT_TCP)) {
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apduLogin = new APDU("REGISTER", null, this.nomeCliente, null, this.portaClienteUDP);

      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU REGISTER ('" + this.nomeCliente
          + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + socketCliente.getPort() + ")...");
      saida.writeObject(apduLogin);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta != null && resposta.startsWith("OK: registrado");
      }

    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no LOGIN (Conjunto " + conjunto + " / TCP " + portaTCP + "): " + e.getMessage());
      return false;
    }
  } // fim do metodo fazerLogin

  /*
   * Metodo: fazerLogout
   * Funcao: Conecta via TCP ao Lider do conjunto correspondente a 1a letra de nomeCliente
   * e faz logout do usuario no servidor usando a APDU oficial
   * Parametros: nenhum
   * Retorno: void
   */
  public void fazerLogout() {
    int conjunto = calcularConjuntoPorNome(this.nomeCliente);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try {
      try (Socket socketCliente = abrirSocketTCPGlobal(portaTCP, TIMEOUT_LOGOUT)) {
      socketCliente.setSoTimeout(TIMEOUT_LOGOUT);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apduLogout = new APDU("LOGOUT", null, this.nomeCliente, null, this.portaClienteUDP);
      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU LOGOUT ('" + this.nomeCliente
          + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + socketCliente.getPort() + ")...");
      saida.writeObject(apduLogout);
      saida.flush();

      entrada.readObject(); 
      }
    } catch (Exception e) {
      System.out.println("CLIENTE - O servidor do Conjunto " + conjunto + " (TCP " + portaTCP
          + ") estava inacessivel no logout: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo fazerLogout

  /*
   * Metodo: solicitarListaGrupos
   * Funcao: Consulta os 3 Lideres ativos (Conjuntos 1, 2 e 3 nas portas 6789, 6790 e 6791)
   * com a APDU LIST, junta as respostas no cliente e envia para a interface
   * Parametros: nenhum
   * Retorno: void
   */
  public void solicitarListaGrupos() {
    ArrayList<String> todosGrupos = new ArrayList<>();
    boolean algumConjuntoRespondeu = false;

    for (int conjunto = 1; conjunto <= 3; conjunto++) {
      int portaTCP = calcularPortaTCPPorConjunto(conjunto);
      try (Socket socketCliente = abrirSocketTCP(portaTCP, TIMEOUT_LIST_CONJUNTO)) {
        socketCliente.setSoTimeout(TIMEOUT_LIST_CONJUNTO);

        ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
        saida.flush();
        ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

        APDU apdu = new APDU("LIST", null, this.nomeCliente, null, this.portaClienteUDP);
        System.out.println("CLIENTE - [ROTEAMENTO LIST] Consultando Lider do Conjunto "
            + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + portaTCP + ")...");
        saida.writeObject(apdu);
        saida.flush();

        String resposta = (String) entrada.readObject();
        if (resposta != null && resposta.startsWith("OK: ")) {
          algumConjuntoRespondeu = true;
          ArrayList<String> gruposConjunto = extrairListaDaResposta(resposta);
          for (String g : gruposConjunto) {
            if (!todosGrupos.contains(g)) {
              todosGrupos.add(g);
            }
          }
        }
      } catch (Exception e) {
        System.out.println("CLIENTE - AVISO: Lider do Conjunto " + conjunto + " "
            + obterFaixaConjunto(conjunto) + " (TCP " + portaTCP + ") inativo ou sem resposta ao LIST.");
      }
    }

    if (algumConjuntoRespondeu) {
      clienteController.exibirListaConversas(todosGrupos, GRUPO);
    } else {
      System.out.println("CLIENTE - ERRO: Nenhum dos 3 lideres respondeu a solicitacao LIST!");
      clienteController.exibirListaConversas(todosGrupos, GRUPO);
    }
  } // fim do solicitarListaGrupos

  /*
   * Metodo: solicitarListaMembros
   * Funcao: Conecta via TCP ao Lider do conjunto correspondente a 1a letra de nomeGrupo
   * e envia a APDU MEMBERS para listar os integrantes do grupo
   * Parametros: grupo = nome do grupo
   * Retorno: void
   */
  public void solicitarListaMembros(String grupo) {
    int conjunto = calcularConjuntoPorNome(grupo);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try (Socket socketCliente = abrirSocketTCP(portaTCP, TIMEOUT_TCP)) {
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("MEMBERS", grupo, this.nomeCliente, null, this.portaClienteUDP);
      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU MEMBERS ('" + grupo
          + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + portaTCP + ")...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();

      if (resposta != null && resposta.startsWith("OK: ")) {
        ArrayList<String> membros = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(membros, PRIVADO);
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar membros do grupo '"
          + grupo + "' no Conjunto " + conjunto + " (TCP " + portaTCP + ")!");
    } // fim do try-catch
  } // fim do solicitarListaMembros

  /*
   * Metodo: solicitarListaUsuarios
   * Funcao: Conecta via TCP ao Lider do conjunto correspondente a 1a letra de nomeCliente
   * e envia a APDU USERS para listar todos os usuarios conectados globalmente
   * Parametros: nenhum
   * Retorno: void
   */
  public void solicitarListaUsuarios() {
    int conjunto = calcularConjuntoPorNome(this.nomeCliente);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try (Socket socketCliente = abrirSocketTCPGlobal(portaTCP, TIMEOUT_TCP)) {
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("USERS", null, this.nomeCliente, null, this.portaClienteUDP);
      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU USERS ('" + this.nomeCliente
          + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + socketCliente.getPort() + ")...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();

      if (resposta != null && resposta.startsWith("OK: ")) {
        ArrayList<String> usuarios = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(usuarios, PRIVADO);
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar usuarios online no Conjunto "
          + conjunto + " (TCP " + portaTCP + ")!");
    } // fim do try-catch
  } // fim do metodo solicitarListaUsuarios

  /*
   * Metodo: extrairListaDaResposta
   * Funcao: Utilitario para transformar a String "OK: item1,item2," num ArrayList
   * de Strings
   * Parametros: resposta = String retornada pelo servidor TCP
   * Retorno: ArrayList<String> com os itens processados
   */
  private ArrayList<String> extrairListaDaResposta(String resposta) {
    ArrayList<String> lista = new ArrayList<>();
    String conteudo = resposta.substring(4);

    if (!conteudo.trim().isEmpty()) {
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
   * Funcao: Pergunta ao Lider do conjunto correspondente a 1a letra de nomeUsuarioDestino (ou nomeCliente)
   * a lista de usuarios conectados para checar se o alvo existe
   * Parametros: nomeUsuarioDestino = nome do usuario que desejamos verificar
   * Retorno: boolean indicando se o usuario esta online
   */
  public boolean verificarUsuario(String nomeUsuarioDestino) {
    int conjunto = calcularConjuntoPorNome(nomeUsuarioDestino);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try (Socket socketCliente = abrirSocketTCPGlobal(portaTCP, TIMEOUT_TCP)) {

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("USERS", null, this.nomeCliente, null, this.portaClienteUDP);
      System.out.println("CLIENTE - [ROTEAMENTO] Verificando usuario '" + nomeUsuarioDestino
          + "' via USERS -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto) + " (TCP " + socketCliente.getPort() + ")...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();

      if (resposta != null && resposta.startsWith("OK: ")) {
        ArrayList<String> usuarios = extrairListaDaResposta(resposta);
        return usuarios.contains(nomeUsuarioDestino.trim());
      } // fim do if
      return false;

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido ao verificar usuario no Conjunto "
          + conjunto + " (TCP " + portaTCP + ").");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha ao verificar usuario no Conjunto " + conjunto + " (TCP " + portaTCP + ").");
      e.printStackTrace();
      return false;
    } // fim do try-catch
  } // fim do metodo verificarUsuario

  /*
   * Metodo: bloquearUsuario
   * Funcao: Envia APDU BLOCK para o Lider do conjunto correspondente a 1a letra de nomeCliente
   * Parametros: usuarioBloqueado = nome do usuario a bloquear
   * Retorno: boolean
   */
  public boolean bloquearUsuario(String usuarioBloqueado) {
    int conjunto = calcularConjuntoPorNome(this.nomeCliente);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try (Socket socketCliente = abrirSocketTCPGlobal(portaTCP, TIMEOUT_TCP)) {

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("BLOCK", null, this.nomeCliente, null, this.portaClienteUDP, usuarioBloqueado);

      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU BLOCK ('" + this.nomeCliente
          + "' -> '" + usuarioBloqueado + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto)
          + " (TCP " + socketCliente.getPort() + ")...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      
      if (resposta != null && resposta.startsWith("OK: ")) {
        return true;
      } // fim do if

      return false;

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Lider do Conjunto "
          + conjunto + " (TCP " + portaTCP + ") nao respondeu ao BLOCK.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor no BLOCK!");
      return false;
    } // fim do try-catch
  } // fim do metodo bloquearUsuario

  
  /*
   * Metodo: desbloquearUsuario
   * Funcao: Envia APDU UNBLOCK para o Lider do conjunto correspondente a 1a letra de nomeCliente
   * Parametros: usuarioDesbloqueado = nome do usuario a desbloquear
   * Retorno: boolean
   */
  public boolean desbloquearUsuario(String usuarioDesbloqueado) {
    int conjunto = calcularConjuntoPorNome(this.nomeCliente);
    int portaTCP = calcularPortaTCPPorConjunto(conjunto);

    try (Socket socketCliente = abrirSocketTCPGlobal(portaTCP, TIMEOUT_TCP)) {

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("UNBLOCK", null, this.nomeCliente, null, this.portaClienteUDP, usuarioDesbloqueado);

      System.out.println("CLIENTE - [ROTEAMENTO] Enviando APDU UNBLOCK ('" + this.nomeCliente
          + "' -> '" + usuarioDesbloqueado + "') -> Conjunto " + conjunto + " " + obterFaixaConjunto(conjunto)
          + " (TCP " + socketCliente.getPort() + ")...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      
      if (resposta != null && resposta.startsWith("OK: ")) {
        return true;
      } // fim do if

      return false;

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Lider do Conjunto "
          + conjunto + " (TCP " + portaTCP + ") nao respondeu ao UNBLOCK.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor no UNBLOCK!");
      return false;
    } // fim do try-catch
  } // fim do metodo desbloquearUsuario

  /*
   * Metodo: enviarConfirmacao
   * Funcao: Monta e envia uma APDU de confirmacao (Status 2 ou 3) de volta ao servidor
   * Parametros: idMensagem = ID unico da mensagem, status = 2 (entregue) ou 3 (lido), nomeGrupo = onde a msg foi enviada, donoDaMensagem = quem enviou originalmente
   * Retorno: void
   */
  public void enviarConfirmacao(String idMensagem, int status, String nomeGrupo, String donoDaMensagem) {
    if (idMensagem == null || idMensagem.trim().isEmpty()) {
      return;
    }

    try {
      // O APDU de confirmacao exige saber quem e o dono original para que o servidor possa encaminhar corretamente
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
