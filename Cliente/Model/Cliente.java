/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 15/09/2026
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
import java.net.Socket;
import java.util.ArrayList;

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

  public Cliente(String nomeCliente, String ipServidor) {
    try {
      this.nomeCliente = nomeCliente;
      this.ipCliente = InetAddress.getLocalHost();
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
   * Metodo: start
   * Funcao: Inicia a Thread que escuta mensagens UDP (objetos APDU) recebidas do
   * servidor
   * Parametros: nenhum
   * Retorno: void
   */
  @Override
  public synchronized void start() {
    new Thread(() -> {
      try {
        while (true) {
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
        if (endpointCliente.isClosed()) {
          System.out.println("CLIENTE - Escuta UDP encerrada pelo usuario (Logout).");
        } // fim do if
      } catch (Exception e) {
        System.out.println("CLIENTE - ERRO ao receber a mensagem!");
        e.printStackTrace();
      } // fim do try-catch
    }).start();
  } // fim do metodo start

  /*
   * Metodo: processarApdu
   * Funcao: Processar a APDU recebida via UDP e direcionar para o controller
   * atualizar a interface
   * Parametros: apduRecebida = objeto APDU deserializado recebido do servidor
   * Retorno: void
   */
  /*
   * Metodo: processarApdu
   * Funcao: processa a APDU (Objeto) recebida do servidor via UDP
   * Parametros: apdu = pacote recebido formatado
   * Retorno: void
   */
  private void processarApdu(APDU apdu) {
    String operacao = apdu.getOperacao();
    switch (operacao) {
      case "SEND":
      case "SENDVU":
        Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeGrupo(), apdu.getNomeUsuario(), GRUPO);
        break;
      case "SENDPVT":
        Controller.clienteController.receberMensagem(apdu.getTextoMensagem(), apdu.getNomeUsuario(), apdu.getNomeUsuario(), PRIVADO);
        break;
      case "CONFIRM":
        System.out.println("CLIENTE - Recebeu confirmacao (Tick): MsgID " + apdu.getIdMensagem() + " Status " + apdu.getStatusRecebido());
        break;
    }
  }

  /*
   * Metodo: entrarGrupo
   * Funcao: Conecta via TCP, envia o objeto APDU de JOIN e aguarda a confirmacao
   * do servidor
   * Parametros: grupo = nome do grupo que o usuario deseja entrar
   * Retorno: boolean indicando se a entrada foi aprovada
   */
  public boolean entrarGrupo(String grupo) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      socketCliente.setSoTimeout(5000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apdu = new APDU("JOIN", grupo, this.nomeCliente, null, this.portaClienteUDP);

      System.out.println("CLIENTE - Enviando APDU JOIN para o servidor...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta != null && resposta.startsWith("OK:");

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao JOIN.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor!");
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
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
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

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao LEAVE.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor!");
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
   * Retorno: void
   */
  public void enviarMensagem(String grupo, String mensagem) {
    try {
      APDU apdu = new APDU("SEND", grupo, this.nomeCliente, mensagem, this.portaClienteUDP);
      enviarObjetoUDP(apdu);
      System.out.println("CLIENTE - Enviando APDU SEND para o servidor");
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem!");
    } // fim do try-catch'
  } // fim do metodo enviarMensagem

  /*
   * Metodo: enviarMensagemPrivado
   * Funcao: Envia uma mensagem privada utilizando o objeto APDU
   * Parametros: usuarioDestino = usuario que vai receber, mensagem = texto
   * Retorno: void
   */
  public void enviarMensagemPrivado(String usuarioDestino, String mensagem) {
    try {
      APDU apdu = new APDU("SENDPVT", null, this.nomeCliente, mensagem, this.portaClienteUDP, usuarioDestino);
      enviarObjetoUDP(apdu);
      System.out.println("CLIENTE - Enviando APDU SENDPVT para o servidor");
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem privada!");
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
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      APDU apduLogin = new APDU("REGISTER", null, this.nomeCliente, null, this.portaClienteUDP);

      saida.writeObject(apduLogin);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta != null && resposta.startsWith("OK: registrado");

    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel comunicar com o servidor!");
      e.printStackTrace();
      return false;
    }
  } // fim do metodo fazerLogin

  /*
   * Metodo: fazerLogout
   * Funcao: Conecta via TCP e faz logout do usuario no servidor
   * Parametros:
   * Retorno: void
   */
  public void fazerLogout() {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_SERVIDOR_TCP);
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.writeObject("LOGOUT~~" + this.nomeCliente);
      saida.flush();
      socketCliente.close();
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel fazer logout!");
      e.printStackTrace();
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

      // Pede a lista global de usuarios usando a APDU "USERS"
      APDU apdu = new APDU("USERS", null, this.nomeCliente, null, this.portaClienteUDP);
      System.out.println("CLIENTE - Verificando se o usuario " + nomeUsuarioDestino + " existe...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      if (resposta != null && resposta.startsWith("OK: ")) {
        return resposta.contains(nomeUsuarioDestino + ",");
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

  public void desligarCliente() {
    endpointCliente.close();
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
