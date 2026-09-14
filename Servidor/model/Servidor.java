/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 01/07/2026
* Nome.............: Servidor.java
* Funcao...........: Gerenciar os grupos, usuarios e as apdus recebidas
*******************************************************************/

package model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class Servidor extends Thread {

  private final int PORTA_UDP = 8080;
  private final int PORTA_TCP = 8081;

  private InetAddress ipServidor;
  private DatagramSocket endpointServidor;
  private static Semaphore mutex = new Semaphore(1);

  private Map<String, ArrayList<Usuario>> grupos = new HashMap<>();
  private Map<String, Usuario> usuariosOnline = new HashMap<>();

  public Servidor() {
    try {
      ipServidor = InetAddress.getLocalHost();
      endpointServidor = new DatagramSocket(PORTA_UDP);
      System.out.println("SERVIDOR estabelecido: ip = " + ipServidor);
    } catch (Exception e) {
      System.out.println("ERRO: Nao foi possivel iniciar o servidor!");
    }
  }

  @Override
  public synchronized void start() {

    new Thread(() -> {
      try {
        ServerSocket servidor = new ServerSocket(PORTA_TCP);
        while (true) {
          System.out.println("SERVIDOR TCP - esperando receber alguma conexao...");
          Socket conexao = servidor.accept();
          new Thread(() -> {
            try {
              System.out.println("SERVIDOR TCP - estabelecendo conexao com ip = " + conexao.getInetAddress() + ".");

              ObjectOutputStream saida = new ObjectOutputStream(conexao.getOutputStream());
              saida.flush();
              ObjectInputStream entrada = new ObjectInputStream(conexao.getInputStream());

              String apduRecebida = ((String) entrada.readObject()).trim();

              System.out.println("SERVIDOR TCP - APDU recebida: " + apduRecebida + ".");

              processarApdu(apduRecebida, conexao.getInetAddress(), saida);
            } catch (IOException | ClassNotFoundException e) {
              System.out.println("SERVIDOR TCP - ERRO: Nao foi possivel receber a APDU do cliente!");
            }
          }).start();
        }
      } catch (Exception e) {
        System.out.println("SERVIDOR TCP - ERRO: Nao foi possivel iniciar o socket TCP!");
      }
    }).start();

    try {
      while (true) {
        byte[] dadosEntrada = new byte[1024];

        DatagramPacket pacoteRecebido = new DatagramPacket(dadosEntrada, dadosEntrada.length);
        System.out.println("SERVIDOR UDP - esperando receber algum pacote...");

        endpointServidor.receive(pacoteRecebido);
        System.out.println("SERVIDOR UDP - recebendo pacote do ip = " + pacoteRecebido.getAddress() + ".");

        String apduRecebida = new String(pacoteRecebido.getData(), 0, pacoteRecebido.getLength(),
            StandardCharsets.UTF_8).trim();

        // ======= INTERCEPTA O BROADCAST DE DESCOBERTA =======
        if (apduRecebida.equals("DISCOVER")) {
          byte[] resposta = "DISCOVER_OK".getBytes();
          DatagramPacket pacoteResposta = new DatagramPacket(resposta, resposta.length, pacoteRecebido.getAddress(),
              pacoteRecebido.getPort());
          endpointServidor.send(pacoteResposta);
          continue;
        }
        // ====================================================

        new Thread(() -> {
          System.out.println("SERVIDOR UDP - APDU recebida: " + apduRecebida + ".");
          processarApdu(apduRecebida, pacoteRecebido.getAddress(), null);
        }).start();
      } // fim do while
    } catch (Exception e) {
      System.out.println("SERVIDOR UDP - ERRO: Nao foi possivel iniciar o socket UDP!");
    } // fim do try-catch
  }

  /*
   * Metodo: processarApdu
   * Funcao: Pegar a APDU recebida e determinar qual eh o comando realizar
   * Parametros: apduRecebida = APDU enviada pelo servidor
   * Retorno: void
   */
  public void processarApdu(String apduRecebida, InetAddress ipCliente, ObjectOutputStream saida) {
    String[] partes = dividirApdu(apduRecebida);
    switch (partes[0]) {
      case "JOIN":
        try {
          String grupo = partes[1];
          String nome = partes[2];
          mutex.acquire();
          inserirNoGrupo(grupo, nome, ipCliente);
          mutex.release();

          if (saida != null) {
            saida.writeObject("JOIN_OK");
            saida.flush();
          } // fim do if
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU JOIN.");
          e.getStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      case "LEAVE":
        try {
          String grupo = partes[1];
          String nome = partes[2];
          mutex.acquire();
          sairDoGrupo(grupo, nome);
          mutex.release();

          if (saida != null) {
            saida.writeObject("LEAVE_OK");
            saida.flush();
          } // fim do if
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU LEAVE.");
          e.getStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      case "SEND":
        try {
          String grupo = partes[1];
          String usuarioRemetente = partes[2];
          String mensagem = partes[3];
          mutex.acquire();
          enviarMensagem(mensagem, grupo, usuarioRemetente);
          mutex.release();
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU SEND.");
          e.getStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      case "SENDPVT":
        try {
          String usuarioDestino = partes[1];
          String usuarioRemetente = partes[2];
          String mensagem = partes[3];
          mutex.acquire();
          enviarMensagemPrivado(mensagem, usuarioDestino, usuarioRemetente);
          mutex.release();
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU SENDPVT.");
          e.getStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      case "LOGIN":
        try {
          String nome = partes[1];
          mutex.acquire();
          logarUsuario(nome, ipCliente, saida);
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU LOGIN.");
          e.getStackTrace();
        } // fim do try-catch
        break;
      case "LOGOUT":
        try {
          String nome = partes[1];
          mutex.acquire();

          if (usuariosOnline.containsKey(nome)) {
            usuariosOnline.remove(nome);
          } // fim do if

          mutex.release();
          System.out.println("SERVIDOR TCP - Usuario " + nome + " deslogado.");

        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU LOGOUT.");
          e.printStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      case "LISTCVS":
        try {
          String nome = partes[1];
          mutex.acquire();
          listarConversas(nome);
          mutex.release();
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU LISTCVS.");
          e.getStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      case "LISTMEMBERS":
        try {
          String nomeGrupo = partes[1];
          String nome = partes[2];
          mutex.acquire();
          listarMembrosGrupo(nomeGrupo, nome);
          mutex.release();
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU LISTMEMBERS.");
          e.getStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      case "CHECKUSER":
        try {
          String nomeAlvo = partes[1];
          mutex.acquire();

          boolean existe = usuariosOnline.containsKey(nomeAlvo);
          mutex.release();

          if (saida != null) {
            saida.writeObject(existe ? "USER_OK" : "USER_NOT_FOUND");
            saida.flush();
          } // fim do if
        } catch (Exception e) {
          System.out.println("SERVIDOR - ERRO: Nao foi possivel processar a APDU CHECKUSER.");
          e.printStackTrace();
          mutex.release();
        } // fim do try-catch
        break;
      default:
        break;
    }
  } // fim do metodo processarApdu

  /*
   * Metodo: dividirApdu
   * Funcao: Pegar a APDU recebida e divide num array
   * Parametros: apdu = APDU enviada pelo servidor
   * Retorno: String[]
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
   * Metodo: logarUsuario
   * Funcao: Pega as informacoes do cliente, verifica se ja tem com um nome igual
   * ja logado, caso nao, armazena essas informacoes e retorna uma mensagem de
   * confirmacao
   * Parametros: nome = nome do cliente, ipCliente = endereco ip do cliente, saida
   * = caminho de saida para retornar a mensagem
   * Retorno: void
   */
  public void logarUsuario(String nome, InetAddress ipCliente, ObjectOutputStream saida) {
    try {
      boolean nomeEmUso = usuariosOnline.containsKey(nome);

      boolean aprovado = !nomeEmUso;

      if (aprovado) {
        Usuario novoUsuario = new Usuario(ipCliente, nome, PORTA_TCP);
        usuariosOnline.put(nome, novoUsuario);
      } // fim do if

      mutex.release();

      if (saida != null) {
        saida.writeObject(aprovado ? "LOGIN_OK" : "LOGIN_ERROR");
        saida.flush();
      } // fim do if
      System.out.println("SERVIDOR TCP - Validando LOGIN de " + nome + " (Aprovado: " + aprovado + ").");

    } catch (Exception e) {
      System.out.println("SERVIDOR - ERRO Crítico no logarUsuario:");
      e.printStackTrace();
      mutex.release();
    } // fim do try-catch
  } // fim do metodo logarUsuario

  /*
   * Metodo: inserirNoGrupo
   * Funcao: Insere o usuario num grupo
   * Parametros: nomeUsuario = nome do cliente, ipCliente = endereco ip do
   * cliente, nomeGrupo = nome do grupo
   * Retorno: void
   */
  public void inserirNoGrupo(String nomeGrupo, String nomeUsuario, InetAddress ipCliente) {
    Usuario novoUsuario = new Usuario(ipCliente, nomeUsuario, PORTA_TCP);
    if (grupos.containsKey(nomeGrupo)) {
      if (!grupos.get(nomeGrupo).contains(novoUsuario)) {
        System.out.println("SERVIDOR TCP - Adicionando " + nomeUsuario + " no grupo " + nomeGrupo + ".");
        grupos.get(nomeGrupo).add(novoUsuario);

        enviarMensagem(nomeUsuario + " entrou no grupo.", nomeGrupo, "SERVIDOR");
      } // fim do if
    } else {
      System.out
          .println("SERVIDOR TCP - Criando novo grupo " + nomeGrupo + " adicionando usuario " + nomeUsuario + ".");
      ArrayList<Usuario> listaUsuario = new ArrayList<>();
      listaUsuario.add(novoUsuario);
      grupos.put(nomeGrupo, listaUsuario);

      enviarMensagem(nomeUsuario + " criou o grupo.", nomeGrupo, "SERVIDOR");
    } // fim do if-else
  } // fim do metodo inserirNoGrupo

  /*
   * Metodo: sairDoGrupo
   * Funcao: Retira o usuario do grupo
   * Parametros: nomeUsuario = nome do cliente, nomeGrupo = nome do grupo
   * Retorno: void
   */
  public void sairDoGrupo(String nomeGrupo, String nomeUsuario) {
    ArrayList<Usuario> listaDeUsuarios = grupos.get(nomeGrupo);
    for (Usuario usuario : listaDeUsuarios) {
      if (usuario.getNome().equals(nomeUsuario)) {
        grupos.get(nomeGrupo).remove(usuario);
        System.out.println("SERVIDOR TCP - Removendo o usuario " + nomeUsuario + " do grupo " + nomeGrupo + ".");
        break;
      } // fim do if
    } // fim do for
    if (grupos.get(nomeGrupo).isEmpty()) {
      System.out.println("SERVIDOR TCP - O grupo " + nomeGrupo + " possui zero usuarios apagando grupo.");
      grupos.remove(nomeGrupo);
    } else {
      enviarMensagem(nomeUsuario + " saiu do grupo.", nomeGrupo, "SERVIDOR");
    } // fim do if-else
  } // fim do metodo sairDoGrupo

  /*
   * Metodo: enviarMensagem
   * Funcao: envia uma mensagem para todos os usarios do grupo, tirando o
   * remetente da mensagem
   * Parametros: nomeUsuario = nome do cliente, nomeGrupo = nome do grupo,
   * mensagem = mensagem enviada pelo usuario
   * Retorno: void
   */
  public void enviarMensagem(String mensagem, String nomeGrupo, String nomeUsuario) {
    ArrayList<Usuario> listaDeUsuarios = grupos.get(nomeGrupo);
    for (Usuario usuario : listaDeUsuarios) {
      if (!(usuario.getNome().equals(nomeUsuario))) {
        try {
          byte[] dadosEnviados = new byte[1024];

          String apdu = new String("SEND~~" + nomeGrupo + "~~" + nomeUsuario + "~~" + mensagem + "\n");
          dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

          DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length, usuario.getIp(),
              PORTA_UDP);
          System.out.println(
              "SERVIDOR UDP - enviando mensagem para usuario " + usuario.getNome() + " ip = " + usuario.getIp() + ".");
          endpointServidor.send(datagramaEnviado);
        } catch (Exception e) {
          System.out.println("SERVIDOR UDP - ERRO: Nao foi possivel enviar a mensagem!");
        } // fim do try-catch
      } // fim do if
    } // fim do for
  } // fim do metodo enviarMensagem

  /*
   * Metodo: enviarMensagemPrivado
   * Funcao: envia uma mensagem para apenas um unico usuario
   * Parametros: nomeUsuarioDestino = nome do cliente que vai receber a mensagem,
   * nomeUsuarioRemetente = usuario remetente, mensagem = mensagem enviada pelo
   * remetente
   * usuario
   * Retorno: void
   */
  public void enviarMensagemPrivado(String mensagem, String nomeUsuarioDestino, String nomeUsuarioRemetente) {
    Usuario usuarioDestino = usuariosOnline.get(nomeUsuarioDestino);
    try {
      byte[] dadosEnviados = new byte[1024];

      String apdu = new String("SENDPVT~~" + nomeUsuarioDestino + "~~" + nomeUsuarioRemetente + "~~" + mensagem + "\n");
      dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

      DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length, usuarioDestino.getIp(),
          PORTA_UDP);
      System.out.println(
          "SERVIDOR UDP - enviando mensagem para usuario " + usuarioDestino.getNome() + " ip = "
              + usuarioDestino.getIp() + ".");
      endpointServidor.send(datagramaEnviado);
    } catch (Exception e) {
      System.out.println("SERVIDOR UDP - ERRO: Nao foi possivel enviar a mensagem privada!");
    } // fim do try-catch
  } // fim do metodo enviarMensagemPrivado

  /*
   * Metodo: listarConversas
   * Funcao: retorna uma lista de todos os grupos
   * Parametros: nomeUsuarioRemetente = usuario que pediu a lista de grupos
   * Retorno: void
   */
  private void listarConversas(String nomeUsuarioRemetente) {
    Usuario usuarioRemetente = usuariosOnline.get(nomeUsuarioRemetente);
    try {
      byte[] dadosEnviados = new byte[1024];

      String apdu = new String("LISTCVS");
      for (String grupo : grupos.keySet()) {
        apdu += ("~~" + grupo);
      }
      apdu += "\n";

      dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

      DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length,
          usuarioRemetente.getIp(),
          PORTA_UDP);
      System.out.println(
          "SERVIDOR UDP - enviando lista de todos os grupos para usuario " + usuarioRemetente.getNome()
              + " ip = "
              + usuarioRemetente.getIp() + ".");
      endpointServidor.send(datagramaEnviado);
    } catch (Exception e) {
      System.out.println("SERVIDOR UDP - ERRO: Nao foi possivel enviar lista de todos os grupos!");
    } // fim do try-catch
  } // fim do if

  /*
   * Metodo: listarMembrosGrupo
   * Funcao: retorna uma lista de todos os usuarios de um grupo
   * Parametros: nomeUsuarioRemetente = usuario que pediu a lista de membros,
   * nomeGrupo = grupo que o usuario que a lista
   * Retorno: void
   */
  private void listarMembrosGrupo(String nomeGrupo, String nomeUsuarioRemetente) {
    ArrayList<Usuario> membros = grupos.get(nomeGrupo);
    Usuario usuarioRemetente = usuariosOnline.get(nomeUsuarioRemetente);
    try {
      byte[] dadosEnviados = new byte[1024];

      String apdu = new String("LISTMEMBERS");
      for (Usuario usuario : membros) {
        if (!usuario.equals(usuarioRemetente))
          apdu += ("~~" + usuario.getNome());
      } // fim do for
      apdu += "\n";

      dadosEnviados = apdu.getBytes(StandardCharsets.UTF_8);

      DatagramPacket datagramaEnviado = new DatagramPacket(dadosEnviados, dadosEnviados.length,
          usuarioRemetente.getIp(),
          PORTA_UDP);
      System.out.println(
          "SERVIDOR UDP - enviando membros do grupo " + nomeGrupo + " para usuario " + usuarioRemetente.getNome()
              + " ip = "
              + usuarioRemetente.getIp() + ".");
      endpointServidor.send(datagramaEnviado);
    } catch (Exception e) {
      System.out.println("SERVIDOR UDP - ERRO: Nao foi possivel enviar os membros do grupo " + nomeGrupo + "!");
    } // fim do try-catch
  } // fim do metodo listarMembrosGrupo

}