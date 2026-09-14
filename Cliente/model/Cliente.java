/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 30/06/2026
* Nome.............: Cliente.java
* Funcao...........: Gerencia as apdus e a comunicacao com o servidor
*******************************************************************/
package model;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;

import controller.clienteController;

public class Cliente extends Thread {

  private final int PORTA_UDP = 8080;
  private final int PORTA_TCP = 8081;

  private final String GRUPO = "grupo";
  private final String PRIVADO = "priv";

  private String nomeCliente;
  private InetAddress ipCliente;
  private InetAddress ipServidor;
  private DatagramSocket endpointCliente;

  public Cliente(String nomeCliente, String ipServidor) {
    try {
      this.nomeCliente = nomeCliente;
      ipCliente = InetAddress.getLocalHost();
      this.ipServidor = InetAddress.getByName(ipServidor);
      endpointCliente = new DatagramSocket(PORTA_UDP);
      System.out.println("CLIENTE estabelecido: nome = " + nomeCliente + " / ip = " + ipCliente);
    } catch (Exception e) {
      System.out.println("ERRO: Nao foi possivel inicializar o cliente");
      e.printStackTrace();
    } // fim do try-catch
  } // fim do construtor

  @Override
  public synchronized void start() {
    new Thread(() -> {
      try {
        while (true) {
          byte[] dadosEntrada = new byte[1024];

          DatagramPacket pacoteRecebido = new DatagramPacket(dadosEntrada, dadosEntrada.length);
          System.out.println("CLIENTE - esperando uma mensagem...");
          endpointCliente.receive(pacoteRecebido);

          String apduRecebida = new String(pacoteRecebido.getData(), 0, pacoteRecebido.getLength(),
              StandardCharsets.UTF_8).trim();

          if (apduRecebida.equals("DISCOVER"))
            continue;

          System.out.println("CLIENTE - Recebeu apdu " + apduRecebida);
          new Thread(() -> {
            processarApdu(apduRecebida);
          }).start();
        }
      } catch (java.net.SocketException e) {
        if (endpointCliente.isClosed()) {
          System.out.println("CLIENTE - Escuta UDP encerrada pelo usuario (Logout).");
        } else {
          System.out.println("CLIENTE - ERRO de rede UDP: " + e.getMessage());
        } // fim do if-else
      } catch (Exception e) {
        System.out.println("CLIENTE - ERRO: Nao foi possivel receber a mensagem!");
        e.printStackTrace();
      }
    }).start();
  } // fim do metodo start

  /*
   * Metodo: processarApdu
   * Funcao: processa a apdu de acordo com o que foi recebido
   * Parametros: apduRecebida
   * Retorno: void
   */
  private void processarApdu(String apduRecebida) {
    String[] partes = dividirApdu(apduRecebida);
    switch (partes[0]) {
      case "SEND":
        try {
          String grupoDestino = partes[1];
          String usuarioRemetente = partes[2];
          String mensagem = partes[3];
          clienteController.receberMensagem(mensagem, grupoDestino, usuarioRemetente, GRUPO);
        } catch (Exception e) {
          System.out.println("CLIENTE - ERRO: Nao foi possivel processar a APDU SEND.");
        } // fim do try-catch
        break;
      case "SENDPVT":
        try {
          String usuarioDestino = partes[1];
          String usuarioRemetente = partes[2];
          String mensagem = partes[3];
          clienteController.receberMensagem(mensagem, usuarioDestino, usuarioRemetente, PRIVADO);
        } catch (Exception e) {
          System.out.println("CLIENTE - ERRO: Nao foi possivel processar a APDU SENDPVT.");
        } // fim do try-catch
        break;
      case "LISTCVS":
        try {
          ArrayList<String> grupos = new ArrayList<>();
          // Se o tamanho for maior que 1, significa que existem grupos na lista
          if (partes.length > 1) {
            grupos = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(partes, 1, partes.length)));
          } // fim do if
          // Envia a lista para o controlador principal abrir a tela
          clienteController.exibirListaConversas(grupos, GRUPO);
        } catch (Exception e) {
          System.out.println("CLIENTE - ERRO: Nao foi possivel processar a APDU LISTCVS.");
        } // fim do try-catch
        break;
      case "LISTMEMBERS":
        try {
          ArrayList<String> membros = new ArrayList<>();
          if (partes.length > 1) {
            membros = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(partes, 1, partes.length)));
          } // fim do if
          // Envia a lista para o controlador principal abrir a tela
          clienteController.exibirListaConversas(membros, PRIVADO);
        } catch (Exception e) {
          System.out.println("CLIENTE - ERRO: Nao foi possivel processar a APDU LISTMEMBERS.");
        } // fim do try-catch
        break;

      default:
        break;
    } // fim do switch-case
  } // fim do processo

  /*
   * Metodo: dividirApdu
   * Funcao: Desmanchar a apdu num array de string
   * Parametros: apdu
   * Retorno: void
   */
  public String[] dividirApdu(String apdu) {
    ArrayList<String> list = new ArrayList<>();
    int indice = 0;
    for (int i = 0; i < apdu.length(); i++) {
      if (apdu.charAt(i) == '{') {
        i++;
      } else if (apdu.charAt(i) == '~') {
        list.add(apdu.substring(indice, i).trim());
        i++;
        indice = i + 1;
      } // fim do if-else
    } // fim do for
    list.add(apdu.substring(indice, apdu.length()));
    int resultSize = list.size();
    String[] result = new String[resultSize];
    return list.subList(0, resultSize).toArray(result);
  } // fim do metodo dividirApdu

  /*
   * Metodo: entrarGrupo
   * Funcao: envia a apdu join ao servidor via TCP
   * Parametros: grupo = grupo que o usuario quer entrar
   * Retorno: void
   */
  public boolean entrarGrupo(String grupo) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_TCP);

      socketCliente.setSoTimeout(2000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      String apdu = new String("JOIN~~" + grupo + "~~" + nomeCliente + "\n");
      System.out.println("CLIENTE - Enviando APDU JOIN para o servidor...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta.equals("JOIN_OK");
    } catch (java.net.SocketTimeoutException e) {
      // Cai aqui se o tempo esgotar
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao JOIN.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor!");
      return false;
    } // fim do try-catch
  } // fim do metodo entrarGrupo

  /*
   * Metodo: sairGrupo
   * Funcao: envia a apdu leave ao servidor via TCP
   * Parametros: grupo = grupo que o usuario quer sair
   * Retorno: void
   */
  public boolean sairGrupo(String grupo) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_TCP);

      socketCliente.setSoTimeout(2000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      String apdu = new String("LEAVE~~" + grupo + "~~" + nomeCliente + "\n");
      System.out.println("CLIENTE - Enviando APDU LEAVE para o servidor...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta.equals("LEAVE_OK");
    } catch (java.net.SocketTimeoutException e) {
      // Cai aqui se o tempo esgotar
      System.out.println("CLIENTE - ERRO: Tempo limite excedido. O Servidor nao respondeu ao LEAVE.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha na conexao com o servidor!");
      return false;
    } // fim do try-catch
  } // fim do metodo sairGrupo

  /*
   * Metodo: enviarMensagemPrivado
   * Funcao: envia a apdu sendpvt ao servidor via UDP
   * Parametros: usuarioDestino = usuario que o cliente quer mandar a mensagem,
   * mensagem
   * Retorno: void
   */
  public void enviarMensagemPrivado(String usuarioDestino, String mensagem) {
    try {
      byte[] dadosEnviados = new byte[1024];

      String apdu = new String("SENDPVT~~" + usuarioDestino + "~~" + nomeCliente + "~~" + mensagem + "\n");
      dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

      System.out.println("CLIENTE - Enviando APDU SENDPVT para o servidor");
      DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length, ipServidor, PORTA_UDP);
      endpointCliente.send(datagramaEnviado);
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem no privado!");
      e.printStackTrace();
    } // fim try-catch
  } // fim do metodo enviarMensagemPrivado

  /*
   * Metodo: enviarMensagem
   * Funcao: envia a apdu send ao servidor via UDP
   * Parametros: grupo = grupo que o cliente quer mandar a mensagem, mensagem
   * Retorno: void
   */
  public void enviarMensagem(String grupo, String mensagem) {
    try {
      byte[] dadosEnviados = new byte[1024];

      String apdu = new String("SEND~~" + grupo + "~~" + nomeCliente + "~~" + mensagem + "\n");
      dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

      System.out.println("CLIENTE - Enviando APDU SEND para o servidor");
      DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length, ipServidor, PORTA_UDP);
      endpointCliente.send(datagramaEnviado);
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem!");
      e.printStackTrace();
    } // fim try-catch
  } // fim do metodo enviarMensagem

  /*
   * Metodo: fazerLogin
   * Funcao: Conecta via TCP e pergunta se o nome ja esta em uso
   * Parametros:
   * Retorno: void
   */
  public boolean fazerLogin() {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_TCP);

      // Cria a saida primeiro
      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      saida.writeObject("LOGIN~~" + this.nomeCliente);
      saida.flush();

      // Fica travado aqui esperando o servidor responder (LOGIN_OK ou LOGIN_ERROR)
      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta.equals("LOGIN_OK");
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel comunicar com o servidor!");
      e.printStackTrace();
      return false;
    } // fim do try-catch
  } // fim do metodo fazerLogin

  /*
   * Metodo: fazerLogout
   * Funcao: Conecta via TCP e faz logout do usuario no servidor
   * Parametros:
   * Retorno: void
   */
  public void fazerLogout() {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_TCP);
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
   * Funcao: envia a apdu LISTCVS ao servidor via UDP para receber lista de grupos
   * do servidor
   * Parametros:
   * Retorno: void
   */
  public void solicitarListaGrupos() {
    try {
      byte[] dadosEnviados = new byte[1024];

      String apdu = new String("LISTCVS~~" + nomeCliente + "\n");
      dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

      System.out.println("CLIENTE - Solicitando lista de grupos ao servidor...");
      DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length, ipServidor, PORTA_UDP);
      endpointCliente.send(datagramaEnviado);
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel solicitar grupos!");
    } // fim do try-catch
  } // fim do metodo solicitarListaGrupos

  /*
   * Metodo: solicitarListaMembros
   * Funcao: envia a apdu LISTMEMBERS ao servidor via UDP para receber lista de
   * membros no grupo
   * Parametros:
   * Retorno: void
   */
  public void solicitarListaMembros(String grupo) {
    try {
      byte[] dadosEnviados = new byte[1024];

      String apdu = new String("LISTMEMBERS~~" + grupo + "~~" + nomeCliente + "\n");
      dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

      System.out.println("CLIENTE - Enviando APDU SEND para o servidor");
      DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length, ipServidor, PORTA_UDP);
      endpointCliente.send(datagramaEnviado);
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Nao foi possivel enviar a mensagem!");
      e.printStackTrace();
    } // fim try-catch
  } // fim do metodo solicitarListaMembros

  /*
   * Metodo: verificarUsuario
   * Funcao: Pergunta ao servidor via TCP se um usuario especifico esta online
   */
  public boolean verificarUsuario(String nomeUsuarioDestino) {
    try {
      Socket socketCliente = new Socket(ipServidor, PORTA_TCP);
      socketCliente.setSoTimeout(5000);

      ObjectOutputStream saida = new ObjectOutputStream(socketCliente.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socketCliente.getInputStream());

      String apdu = new String("CHECKUSER~~" + nomeUsuarioDestino + "\n");
      System.out.println("CLIENTE - Verificando se o usuario " + nomeUsuarioDestino + " existe...");
      saida.writeObject(apdu);
      saida.flush();

      String resposta = (String) entrada.readObject();
      socketCliente.close();

      return resposta.equals("USER_OK");

    } catch (java.net.SocketTimeoutException e) {
      System.out.println("CLIENTE - ERRO: Tempo limite excedido ao verificar usuario.");
      return false;
    } catch (Exception e) {
      System.out.println("CLIENTE - ERRO: Falha ao verificar usuario no servidor.");
      e.printStackTrace();
      return false;
    } // fim do try-catch
  } // fim do metodo verificarUsuario

  public void desligarCliente() {
    endpointCliente.close();
  }

  public int getPORTA_UDP() {
    return PORTA_UDP;
  }

  public int getPORTA_TCP() {
    return PORTA_TCP;
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
