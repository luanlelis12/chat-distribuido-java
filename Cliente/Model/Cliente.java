/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 17/09/2026
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
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;

import Controller.clienteController;

import java.nio.charset.StandardCharsets;

import Protocol.APDU;

public class Cliente extends Thread {

  private final int PORTA_SERVIDOR_UDP = 7777;
  private final int PORTA_SERVIDOR_TCP = 6789;

  private int portaClienteUDP;

  private final String GRUPO = "grupo";
  private final String PRIVADO = "priv";

  private String nomeCliente;
  private InetAddress ipCliente;
  private InetAddress ipServidor;
  private DatagramSocket endpointCliente;
  private volatile boolean escutaUDPAtiva = false;

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
   * Retorno: void
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
   * Metodo: start
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
          byte[] dadosEntrada = new byte[8192];
          DatagramPacket pacoteRecebido = new DatagramPacket(dadosEntrada, dadosEntrada.length);
          endpointCliente.receive(pacoteRecebido);

          // Extrai o objeto APDU serializado
          ByteArrayInputStream bais = new ByteArrayInputStream(pacoteRecebido.getData());
          ObjectInputStream in = new ObjectInputStream(bais);
          APDU apduRecebida = (APDU) in.readObject();

          System.out.println("CLIENTE - Recebeu APDU: " + apduRecebida.getOperacao());

          new Thread(() -> {
            processarApdu(apduRecebida);
          }).start();
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
        Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeUsuario(), apdu.getNomeUsuario(), PRIVADO, false, apdu.getIdMensagem());
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
   * Funcao: Conecta via TCP, envia o objeto APDU de JOIN e aguarda a confirmacao
   * do servidor
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
      try (Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP)) {
      socketCliente.setSoTimeout(5000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("JOIN", grupoNormalizado, this.nomeCliente.trim(), null, this.portaClienteUDP);

      System.out.println("CLIENTE - Enviando APDU JOIN para o servidor...");
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
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao JOIN.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no JOIN: " + e.getMessage());
      return false;
    } // fim do try-catch
  } // fim do metodo entrarGrupo

  /*
   * Metodo: sairGrupo
   * Funcao: Conecta via TCP, envia o objeto APDU de LEAVE e aguarda a confirmacao
   * do servidor
   * Parametros: grupo = nome do grupo que o usuario deseja sair
   * Retorno: boolean indicando se a saida foi concluida
   */
  public boolean sairGrupo(String grupo) {
    try {
      try (Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP)) {
      socketCliente.setSoTimeout(5000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      // Instancia a APDU do colega para o LEAVE
      APDU apdu = new APDU("LEAVE", grupo, this.nomeCliente, null, this.portaClienteUDP);

      System.out.println("CLIENTE - Enviando APDU LEAVE para o servidor...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      // O novo servidor responde "OK: Saiu do grupo com sucesso"
      return resposta != null && resposta.startsWith("OK:");
      }

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao LEAVE.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no LEAVE: " + e.getMessage());
      return false;
    } // fim do try-catch
  } // fim do metodo sairGrupo

  /*
   * Metodo: enviarObjetoUDP
   * Funcao: Serializa e envia uma APDU para o servidor (Evita repeticao de codigo)
   * Parametros: apdu = objeto a ser enviado
   * Retorno: void
   */
  private void enviarObjetoUDP(APDU apdu) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(baos);
      out.writeObject(apdu);
      out.flush();
      byte[] dados = baos.toByteArray();
      
      DatagramPacket pacote = new DatagramPacket(dados, dados.length, ipServidor, PORTA_SERVIDOR_UDP);
      endpointCliente.send(pacote);
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
   * Retorno: void
   */
  public String enviarMensagemPrivado(String usuarioDestino, String mensagem, boolean isVisuUnica) {
    try {
      String operacao = isVisuUnica ? "SENDVU" : "SENDPVT"; 
      APDU apdu = new APDU(operacao, null, this.nomeCliente, mensagem, this.portaClienteUDP, usuarioDestino);
      enviarObjetoUDP(apdu);
      System.out.println("CLIENTE - Enviando APDU " + operacao + " para o servidor");
      return apdu.getIdMensagem();
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem privada!");
      return null;
    } // fim do try-catch
  } // fim do metodo enviarMensagemPrivado

  /*
   * Metodo: fazerLogin
   * Funcao: Conecta via TCP e envia a APDU REGISTER para o servidor
   * Parametros: nenhum
   * Retorno: boolean (true se o login for aprovado, false caso contrario)
   */
  public boolean fazerLogin() {
    try {
      try (Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP)) {
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apduLogin = new APDU("REGISTER", null, this.nomeCliente, null, this.portaClienteUDP);

      saida.writeObject(apduLogin);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta != null && resposta.startsWith("OK: registrado");
      }

    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha no LOGIN: " + e.getMessage());
      return false;
    }
  } // fim do metodo fazerLogin

  /*
   * Metodo: fazerLogout
   * Funcao: Conecta via TCP e faz logout do usuario no servidor usando a APDU oficial
   * Parametros: nenhum
   * Retorno: void
   */
  public void fazerLogout() {
    try {
      try (Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP)) {
      socketCliente.setSoTimeout(3000);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apduLogout = new APDU("LOGOUT", null, this.nomeCliente, null, this.portaClienteUDP);
      saida.writeObject(apduLogout);
      saida.flush();

      entrada.readObject(); 
      }
    } catch (Exception e) {
      System.out.println("CLIENTE - O servidor ja estava inacessivel no logout: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo fazerLogout

  /*
   * Metodo: solicitarListaGrupos
   * Funcao: Conecta via TCP, envia a APDU LIST para receber os grupos e envia
   * para a interface
   * Parametros: nenhum
   * Retorno: void
   */
  public void solicitarListaGrupos() {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("LIST", null, this.nomeCliente, null, this.portaClienteUDP);
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      if (resposta != null && resposta.startsWith("OK: ")) {
        ArrayList<String> grupos = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(grupos, GRUPO);
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar grupos!");
    } // fim do try-catch
  } // fim do solicitarListaGrupos

  /*
   * Metodo: solicitarListaMembros
   * Funcao: Conecta via TCP, envia a APDU MEMBERS para listar os integrantes de
   * um grupo
   * Parametros: grupo = nome do grupo
   * Retorno: void
   */
  public void solicitarListaMembros(String grupo) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("MEMBERS", grupo, this.nomeCliente, null, this.portaClienteUDP);
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      if (resposta != null && resposta.startsWith("OK: ")) {
        ArrayList<String> membros = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(membros, PRIVADO);
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar membros!");
    } // fim do try-catch
  } // fim do solicitarListaMembros

  /*
   * Metodo: solicitarListaUsuarios
   * Funcao: Conecta via TCP, envia a APDU USERS para listar todos os usuarios
   * conectados globalmente
   * Parametros: nenhum
   * Retorno: void
   */
  public void solicitarListaUsuarios() {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("USERS", null, this.nomeCliente, null, this.portaClienteUDP);
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      if (resposta != null && resposta.startsWith("OK: ")) {
        ArrayList<String> usuarios = extrairListaDaResposta(resposta);
        clienteController.exibirListaConversas(usuarios, PRIVADO);
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar usuarios online!");
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
   * Funcao: Pergunta ao servidor a lista de usuarios conectados para checar se um
   * alvo especifico existe
   * Parametros: nomeUsuarioDestino = nome do usuario que desejamos verificar
   * Retorno: boolean indicando se o usuario esta online
   */
  public boolean verificarUsuario(String nomeUsuarioDestino) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      socketCliente.setSoTimeout(5000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("USERS", null, this.nomeCliente, null, this.portaClienteUDP);
      System.out.println("CLIENTE - Verificando se o usuario " + nomeUsuarioDestino + " existe...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      if (resposta != null && resposta.startsWith("OK: ")) {
        ArrayList<String> usuarios = extrairListaDaResposta(resposta);
        return usuarios.contains(nomeUsuarioDestino.trim());
      } // fim do if
      return false;

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido ao verificar usuario.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha ao verificar usuario no servidor.");
      e.printStackTrace();
      return false;
    } // fim do try-catch
  } // fim do metodo verificarUsuario

  /*
   * Metodo: bloquearUsuario
   * Funcao: 
   * Parametros:
   * Retorno: void
   */
  public boolean bloquearUsuario(String usuarioBloqueado) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      socketCliente.setSoTimeout(5000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      // Instancia a APDU do colega para o LEAVE
      APDU apdu = new APDU("BLOCK", null, this.nomeCliente, null, this.portaClienteUDP, usuarioBloqueado);

      System.out.println("CLIENTE - Enviando APDU BLOCK para o servidor...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();
      
      if (resposta != null && resposta.startsWith("OK: ")) {
        return true;
      } // fim do if

      return false;

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao BLOCK.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor!");
      return false;
    } // fim do try-catch
  } // fim do metodo bloquearUsuario

  
  /*
   * Metodo: desbloquearUsuario
   * Funcao: 
   * Parametros:
   * Retorno: void
   */
  public boolean desbloquearUsuario(String usuarioDesbloqueado) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      socketCliente.setSoTimeout(5000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      // Instancia a APDU do colega para o LEAVE
      APDU apdu = new APDU("UNBLOCK", null, this.nomeCliente, null, this.portaClienteUDP, usuarioDesbloqueado);

      System.out.println("CLIENTE - Enviando APDU UNBLOCK para o servidor...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();
      
      if (resposta != null && resposta.startsWith("OK: ")) {
        return true;
      } // fim do if

      return false;

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao UNBLOCK.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor!");
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
