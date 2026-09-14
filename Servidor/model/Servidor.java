/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 14/09/2026
* Nome.............: Servidor.java
* Funcao...........: Gerenciar os grupos, usuarios e as apdus recebidas
*******************************************************************/

package model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import protocol.APDU;

public class Servidor extends Thread {

  private final int PORTA_UDP = 7777;
  private final int PORTA_TCP = 6789;
  private final int PORTA_DISCOVERY = 8888;

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

    // THREAD DE DESCOBERTA (Porta 8888)
    new Thread(() -> {
      try (DatagramSocket socketDesc = new DatagramSocket(PORTA_DISCOVERY)) {

        System.out.println("SERVIDOR DESCOBERTA - Escutando na porta " + PORTA_DISCOVERY);
        byte[] buffer = new byte[256];

        while (true) {

          DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
          socketDesc.receive(pacote);
          String msg = new String(pacote.getData(), 0, pacote.getLength()).trim();

          if (msg.equals("SERVIDOR_IP")) {
            byte[] resp = "IP".getBytes();
            DatagramPacket pacoteResp = new DatagramPacket(resp, resp.length, pacote.getAddress(), pacote.getPort());
            socketDesc.send(pacoteResp);
          } // fim do if

        } // fim do while
      } catch (Exception e) {
        System.out.println("SERVIDOR DESCOBERTA - Erro na descoberta: " + e.getMessage());
      } // fim do try-catch
    });

    // THREAD TCP (Controle)
    new Thread(() -> {
      try (ServerSocket servidor = new ServerSocket(PORTA_TCP)) {

        System.out.println("SERVIDOR TCP - esperando na porta " + PORTA_TCP);

        while (true) {

          Socket conexao = servidor.accept();
          new Thread(() -> {
            try {

              ObjectOutputStream saida = new ObjectOutputStream(conexao.getOutputStream());
              saida.flush();
              ObjectInputStream entrada = new ObjectInputStream(conexao.getInputStream());

              APDU apduRecebida = (APDU) entrada.readObject();
              System.out.println("SERVIDOR TCP - APDU recebida: " + apduRecebida.getOperacao());
              processarApdu(apduRecebida, conexao.getInetAddress(), saida);

            } catch (Exception e) {
              System.out.println("SERVIDOR TCP - ERRO: " + e.getMessage());
            } // fim do try-catch
          }).start();

        } // fim do while
      } catch (Exception e) {
        System.out.println("SERVIDOR TCP - Nao foi possivel iniciar! " + e.getMessage());
      } // fim do try-catch
    }).start();

    // THREAD UDP (Mensagens)
    new Thread(() -> {
      try {

        System.out.println("SERVIDOR UDP - esperando na porta " + PORTA_UDP);

        while (true) {

          byte[] dadosEntrada = new byte[8192];
          DatagramPacket pacoteRecebido = new DatagramPacket(dadosEntrada, dadosEntrada.length);
          endpointServidor.receive(pacoteRecebido);

          ByteArrayInputStream bais = new ByteArrayInputStream(pacoteRecebido.getData());
          ObjectInputStream in = new ObjectInputStream(bais);
          APDU apduRecebida = (APDU) in.readObject();

          new Thread(() -> {
            processarApdu(apduRecebida, pacoteRecebido.getAddress(), null);
          }).start();

        } // fim do while
      } catch (Exception e) {
        System.out.println("SERVIDOR UDP - ERRO!");
      } // fim do try-catch
    }).start();

  } // fim do metodo start

  /*
   * Metodo: processarApdu
   * Funcao: Pegar a APDU recebida e determinar qual operacao realizar
   * Parametros: apdu = APDU recebida, ipCliente = ip do cliente que enviou a
   * apdu, saida = fluxo de saida TCP para responder ao cliente
   * Retorno: void
   */
  public void processarApdu(APDU apdu, InetAddress ipCliente, ObjectOutputStream saida) {
    String operacao = apdu.getOperacao().toUpperCase();

    switch (operacao) {
      case "REGISTER":
        try {
          mutex.acquire();
          logarUsuario(apdu.getNomeUsuario(), ipCliente, apdu.getPortaClienteUDP(), saida);
          mutex.release();
        } catch (Exception e) {}
        break;

      case "JOIN":
        try {
          mutex.acquire();
          inserirNoGrupo(apdu.getNomeGrupo(), apdu.getNomeUsuario(), ipCliente, apdu.getPortaClienteUDP(), saida);
          mutex.release();
        } catch (Exception e) {}
        break;

      case "LEAVE":
        try {
          mutex.acquire();
          sairDoGrupo(apdu.getNomeGrupo(), apdu.getNomeUsuario(), saida);
          mutex.release();
        } catch (Exception e) {}
        break;

      case "SEND":
      case "SENDVU":
        try {
          mutex.acquire();
          enviarMensagem(apdu, apdu.getNomeGrupo(), apdu.getNomeUsuario());
          mutex.release();
        } catch (Exception e) {}
        break;

      case "SENDPVT":
        try {
          mutex.acquire();
          enviarMensagemPrivado(apdu, apdu.getDestinatario(), apdu.getNomeUsuario());
          mutex.release();
        } catch (Exception e) {}
        break;

      case "CONFIRM":
        try {
          mutex.acquire();
          encaminharConfirmacao(apdu);
          mutex.release();
        } catch (Exception e) {}
        break;

      case "LIST":
        try {
          mutex.acquire();
          listarConversas(saida);
          mutex.release();
        } catch (Exception e) {}
        break;

      case "MEMBERS":
        try {
          mutex.acquire();
          listarMembrosGrupo(apdu.getNomeGrupo(), saida);
          mutex.release();
        } catch (Exception e) {}
        break;

      case "USERS":
        try {
          mutex.acquire();
          listarUsuariosOnline(saida);
          mutex.release();
        } catch (Exception e) {}
        break;

      case "BLOCK":
        try {
          if (saida != null) {
            saida.writeObject("OK: " + apdu.getDestinatario() + " foi bloqueado.");
            saida.flush();
          }
        } catch (Exception e) {}
        break;

      case "UNBLOCK":
        try {
          if (saida != null) {
            saida.writeObject("OK: " + apdu.getDestinatario() + " foi desbloqueado.");
            saida.flush();
          }
        } catch (Exception e) {}
        break;
    } // fim do switch-case
  } // fim do metodo processarApdu

  /*
   * Metodo: logarUsuario
   * Funcao: Pega as informacoes do cliente, verifica se o nome ja esta em uso, armazena e retorna a confirmacao via TCP
   * Parametros: nome = nome do cliente, ipCliente = IP do cliente, portaUDP = porta UDP do cliente, saida = fluxo de saida TCP
   * Retorno: void
   */
  public void logarUsuario(String nome, InetAddress ipCliente, int portaUDP, ObjectOutputStream saida) {
    boolean aprovado = !usuariosOnline.containsKey(nome);
    
    if (aprovado) {
      Usuario novoUsuario = new Usuario(ipCliente, nome, portaUDP);
      usuariosOnline.put(nome, novoUsuario);
    } // fim do if
    
    try {
      if (saida != null) {
        // O cliente exige exatamente esta resposta
        saida.writeObject(aprovado ? "OK: registrado" : "ERRO: Nome em uso");
        saida.flush();
      } // fim do if
    } catch (Exception e) {}
  } // fim do metodo logarUsuario

  /*
   * Metodo: inserirNoGrupo
   * Funcao: Adiciona o usuario a um grupo especifico e retorna a confirmacao via TCP
   * Parametros: nomeGrupo = nome do grupo, nomeUsuario = nome do cliente, ipCliente = IP do cliente, portaUDP = porta UDP do cliente, saida = fluxo de saida TCP
   * Retorno: void
   */
  public void inserirNoGrupo(String nomeGrupo, String nomeUsuario, InetAddress ipCliente, int portaUDP, ObjectOutputStream saida) {
    Usuario novoUsuario = new Usuario(ipCliente, nomeUsuario, portaUDP);
    boolean sucesso = true;

    if (grupos.containsKey(nomeGrupo)) {
      if (!grupos.get(nomeGrupo).contains(novoUsuario)) {
        grupos.get(nomeGrupo).add(novoUsuario);
      } else {
        sucesso = false;
      } // fim do if
    } else {
      ArrayList<Usuario> listaUsuario = new ArrayList<>();
      listaUsuario.add(novoUsuario);
      grupos.put(nomeGrupo, listaUsuario);
    } // fim do if

    try {
      if (saida != null) {
        saida.writeObject(sucesso ? "OK: entrou no grupo com sucesso" : "ERRO: Nome de usuario ja em uso neste grupo");
        saida.flush();
      } // fim do if
    } catch (Exception e) {}
  } // fim do metodo inserirNoGrupo

  /*
   * Metodo: sairDoGrupo
   * Funcao: Remove o usuario de um grupo e envia a confirmacao de saida via TCP
   * Parametros: nomeGrupo = nome do grupo, nomeUsuario = nome do cliente, saida = fluxo de saida TCP
   * Retorno: void
   */
  public void sairDoGrupo(String nomeGrupo, String nomeUsuario, ObjectOutputStream saida) {
    boolean removido = false;
    if (grupos.containsKey(nomeGrupo)) {
      ArrayList<Usuario> listaDeUsuarios = grupos.get(nomeGrupo);
      
      for (Usuario usuario : listaDeUsuarios) {
        if (usuario.getNome().equals(nomeUsuario)) {
          listaDeUsuarios.remove(usuario);
          removido = true;
          break;
        } // fim do if
      } // fim do for
      
      if (listaDeUsuarios.isEmpty()) {
        grupos.remove(nomeGrupo);
      } // fim do if
    } // fim do if

    try {
      if (saida != null) {
        saida.writeObject(removido ? "OK: Saiu do grupo com sucesso" : "ERRO: Usuario nao encontrado no grupo.");
        saida.flush();
      } // fim do if
    } catch (Exception e) {}
  } // fim do metodo sairDoGrupo

  /*
   * Metodo: listarConversas
   * Funcao: Gera uma lista com todos os grupos existentes e envia para o cliente via TCP
   * Parametros: saida = fluxo de saida TCP para responder ao cliente
   * Retorno: void
   */
  private void listarConversas(ObjectOutputStream saida) {
    StringBuilder sb = new StringBuilder("OK: ");
    
    for (String grupo : grupos.keySet()) {
      sb.append(grupo).append(",");
    } // fim do for
    
    try {
      if (saida != null) {
        saida.writeObject(sb.toString());
        saida.flush();
      } // fim do if
    } catch (Exception e) {}
  } // fim do metodo listarConversas

  /*
   * Metodo: listarMembrosGrupo
   * Funcao: Gera uma lista com todos os usuarios de um grupo especifico e envia via TCP
   * Parametros: nomeGrupo = nome do grupo, saida = fluxo de saida TCP para responder ao cliente
   * Retorno: void
   */
  private void listarMembrosGrupo(String nomeGrupo, ObjectOutputStream saida) {
    StringBuilder sb = new StringBuilder("OK: ");
    
    if (grupos.containsKey(nomeGrupo)) {
      for (Usuario usuario : grupos.get(nomeGrupo)) {
        sb.append(usuario.getNome()).append(",");
      } // fim do for
    } // fim do if

    try {
      if (saida != null) {
        saida.writeObject(sb.toString());
        saida.flush();
      } // fim do if
    } catch (Exception e) {}
  } // fim do metodo listarMembrosGrupo

  /*
   * Metodo: listarUsuariosOnline
   * Funcao: Gera uma lista com todos os usuarios conectados no servidor e envia via TCP
   * Parametros: saida = fluxo de saida TCP para responder ao cliente
   * Retorno: void
   */
  private void listarUsuariosOnline(ObjectOutputStream saida) {
    StringBuilder sb = new StringBuilder("OK: ");

    for (String nome : usuariosOnline.keySet()) {
      sb.append(nome).append(",");
    } // fim do for
    
    try {
      if (saida != null) {
        saida.writeObject(sb.toString());
        saida.flush();
      } // fim do if
    } catch (Exception e) {}
  } // fim do metodo listarUsuariosOnline

  /*
   * Metodo: enviarMensagem
   * Funcao: Encaminha uma APDU de mensagem para todos os usuarios de um grupo, exceto o remetente
   * Parametros: apdu = APDU com a mensagem, nomeGrupo = grupo alvo, nomeUsuarioRemetente = usuario que enviou
   * Retorno: void
   */
  public void enviarMensagem(APDU apdu, String nomeGrupo, String nomeUsuarioRemetente) {
    if (!grupos.containsKey(nomeGrupo)) return;
    ArrayList<Usuario> listaDeUsuarios = grupos.get(nomeGrupo);
    
    for (Usuario usuario : listaDeUsuarios) {
      if (!usuario.getNome().equals(nomeUsuarioRemetente)) {
        enviarObjetoUDP(apdu, usuario.getIp(), usuario.getPorta());
      } // fim do if
    } // fim do for
  } // fim do metodo enviarMensagem

  /*
   * Metodo: enviarMensagemPrivado
   * Funcao: Encaminha uma APDU de mensagem privada diretamente para um unico destinatario
   * Parametros: apdu = APDU com a mensagem, nomeUsuarioDestino = destinatario, nomeUsuarioRemetente = remetente
   * Retorno: void
   */
  public void enviarMensagemPrivado(APDU apdu, String nomeUsuarioDestino, String nomeUsuarioRemetente) {
    Usuario usuarioDestino = usuariosOnline.get(nomeUsuarioDestino);
    if (usuarioDestino != null) {
      enviarObjetoUDP(apdu, usuarioDestino.getIp(), usuarioDestino.getPorta());
    } // fim do if
  } // fim do metodo enviarMensagemPrivado

  /*
   * Metodo: encaminharConfirmacao
   * Funcao: Devolve os ticks de confirmacao (recebido/lido) para o dono original da mensagem
   * Parametros: apdu = APDU contendo o status de confirmacao
   * Retorno: void
   */
  public void encaminharConfirmacao(APDU apdu) {
    Usuario dono = usuariosOnline.get(apdu.getDonoDaMensagem());
    if (dono != null) {
      enviarObjetoUDP(apdu, dono.getIp(), dono.getPorta());
    } // fim do if
  } // fim do metodo encaminharConfirmacao

  /*
   * Metodo: enviarObjetoUDP
   * Funcao: Serializa um objeto APDU e envia para o IP e porta especificados via datagrama UDP
   * Parametros: apdu = objeto APDU a ser enviado, ipDestino = IP do destinatario, portaDestino = porta UDP do destinatario
   * Retorno: void
   */
  private void enviarObjetoUDP(APDU apdu, InetAddress ipDestino, int portaDestino) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(baos);
      out.writeObject(apdu);
      out.flush();
      byte[] dados = baos.toByteArray();
      
      DatagramPacket pacote = new DatagramPacket(dados, dados.length, ipDestino, portaDestino);
      endpointServidor.send(pacote);
    } catch (Exception e) {
      System.out.println("SERVIDOR UDP - ERRO ao encaminhar objeto.");
    } // fim do try-catch
  } // fim do metodo enviarObjetoUDP

}