/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 17/06/2026
* Ultima alteracao.: 19/09/2026
* Nome.............: ServidorTCP.java
* Funcao...........: Lidar com conexoes confiaveis de longa duracao e gerenciar grupos e usuarios
*******************************************************************/
package Model;

import Protocol.APDU;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ServidorTCP extends Thread {
  private static final int TIMEOUT_TCP = 5000;
  private int porta;
  private Servidor servidor;

  /*
   * Metodo: ServidorTCP (Construtor)
   * Funcao: Inicializar os atributos da thread tcp
   * Parametros: porta = porta de escuta, servidor = instancia principal da
   * memoria central
  * Retorno: void
   */
  public ServidorTCP(int porta, Servidor servidor) {
    this.porta = porta;
    this.servidor = servidor;
  } // fim do metodo ServidorTCP

  /*
   * Metodo: run
   * Funcao: Executa o loop infinito de espera por conexoes TCP
   * Parametros: nenhum
   * Retorno: void
   */
  @Override
  public void run() {
    try (ServerSocket serverSocket = new ServerSocket(porta)) {
      System.out.println("[TCP] Escutando na porta " + porta);
      while (true) {
        Socket clientSocket = serverSocket.accept();
        clientSocket.setSoTimeout(TIMEOUT_TCP);
        new Thread(() -> processarConexao(clientSocket)).start();
      } // fim do while
    } catch (Exception e) {
      System.out.println("[TCP-ERRO] Erro Critico na porta " + porta);
    } // fim do try-catch
  } // fim do metodo run

  /*
   * Metodo: processarConexao
   * Funcao: Le o objeto APDU via socket TCP e repassa para o metodo
   * correspondente
   * Parametros: socket = socket do cliente conectado
   * Retorno: void
   */
  private void processarConexao(Socket socket) {
    try (Socket socketCliente = socket) {
      ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());
      ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
      saida.flush();

      APDU apdu = (APDU) entrada.readObject();
      String operacao = apdu.getOperacao();

      System.out.println("\n[TCP] >>> Requisicao recebida: [" + operacao + "] de '" + apdu.getNomeUsuario() + "'");

      servidor.mutex.acquire();
      try {
        switch (operacao) {
          case "REGISTER":
            registrarUsuario(apdu.getNomeUsuario(), socket.getInetAddress(), apdu.getPortaClienteUDP(), saida);
            break;
          case "LIST":
            listarGrupos(saida);
            break;
          case "USERS":
            listarUsuarios(saida);
            break;
          case "MEMBERS":
            listarMembros(apdu.getNomeGrupo(), saida);
            break;
          case "BLOCK":
            bloquearUsuario(apdu.getNomeUsuario(), apdu.getDestinatario(), saida);
            break;
          case "UNBLOCK":
            desbloquearUsuario(apdu.getNomeUsuario(), apdu.getDestinatario(), saida);
            break;
          case "JOIN":
            inserirNoGrupo(apdu.getNomeGrupo(), apdu.getNomeUsuario(), saida);
            break;
          case "LEAVE":
            sairDoGrupo(apdu.getNomeGrupo(), apdu.getNomeUsuario(), saida);
            break;
          case "LOGOUT":
            deslogarUsuario(apdu.getNomeUsuario(), saida);
            break;
        } // fim do switch-case
      } finally {
        servidor.mutex.release(); // Libera a memoria
      } // fim do try-finally

    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Erro ao processar requisicao: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo processarConexao

  /*
   * Metodo: validarNomeUsuario
   * Funcao: Valida o nome de usuario
   * Parametros: nome = nome de usuario
   * Retorno: boolean
   */
  private boolean validarNomeUsuario(String nome) {
    return nome != null && nome.trim().length() >= 3 && nome.trim().length() <= 20
        && nome.matches("[a-zA-Z0-9_]+") && !nome.equals("SERVIDOR");
  } // fim do metodo validarNomeUsuario

  /*
   * Metodo: validarTextoMensagem
   * Funcao: Valida o texto de uma mensagem
   * Parametros: texto = texto da mensagem
   * Retorno: boolean
   */
  private boolean validarTextoMensagem(String texto) {
    return texto != null && !texto.trim().isEmpty() && texto.trim().length() <= 500;
  }

  /*
   * Metodo: registrarUsuario
   * Funcao: Adiciona um novo usuario na lista de conectados
   * Parametros: nome = nome de usuario, ip = ip do cliente, portaUDP = porta que
   * o cliente ira escutar as apdus, saida = fluxo TCP
   * Retorno: void
   */
  public void registrarUsuario(String nome, InetAddress ip, int portaUDP, ObjectOutputStream saida) {
    boolean registrado = false;
    try {
      String nomeValido = nome != null ? nome.trim() : "";
      if (validarNomeUsuario(nomeValido) && !servidor.usuariosOnline.containsKey(nomeValido)) {
        Usuario novoUsuario = new Usuario(ip, nomeValido, portaUDP);
        servidor.usuariosOnline.put(nomeValido, novoUsuario);
        registrado = true;
        System.out.println("      -> SUCESSO: O usuario '" + nomeValido + "' foi registrado no sistema.");
      } else {
        System.out.println("      -> NEGADO: O nome '" + nome + "' ja esta em uso ou e invalido.");
      } // fim do if
      if (saida != null) {
        saida.writeObject(registrado ? "OK: registrado com sucesso" : "ERRO: nome de usuario em uso");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao registrar usuario: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do registrarUsuario

  /*
   * Metodo: listarGrupos
   * Funcao: Envia a string contendo os nomes dos grupos existentes
   * Parametros: saida = fluxo TCP
   * Retorno: void
   */
  public void listarGrupos(ObjectOutputStream saida) {
    try {
      System.out.println("      -> Enviando lista de grupos disponiveis.");
      String str = String.join(",", servidor.grupos.keySet());
      if (saida != null) {
        saida.writeObject("OK: " + str);
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao listar grupos: " + e.getMessage());
    } // fim do try-catch
  } // fim do listarGrupos

  /*
   * Metodo: listarUsuarios
   * Funcao: Envia a string contendo os nomes de todos os usuarios online
   * Parametros: saida = fluxo TCP
   * Retorno: void
   */
  public void listarUsuarios(ObjectOutputStream saida) {
    try {
      System.out.println("      -> Enviando lista global de usuarios conectados.");
      String str = String.join(",", servidor.usuariosOnline.keySet());
      if (saida != null) {
        saida.writeObject("OK: " + str);
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao listar usuarios: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo listarUsuarios

  /*
   * Metodo: listarMembros
   * Funcao: Envia a string contendo os membros que estao num grupo
   * Parametros: nomeGrupo = nome do grupo alvo, saida = fluxo TCP
   * Retorno: void
   */
  public void listarMembros(String nomeGrupo, ObjectOutputStream saida) {
    try {
      System.out.println("      -> Enviando membros atuais do grupo '" + nomeGrupo + "'.");
      if (servidor.grupos.containsKey(nomeGrupo)) {
        ArrayList<String> nomesUsuarios = new ArrayList<>();
        for (Usuario u : servidor.grupos.get(nomeGrupo)) {
          nomesUsuarios.add(u.getNome());
        } // fim do if
        if (saida != null) {
          saida.writeObject("OK: " + String.join(",", nomesUsuarios));
          saida.flush();
        } // fim do if
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao listar membros do grupo '" + nomeGrupo + "': " + e.getMessage());
    } // fim do try-catch
  } // fim do listarMembros

  /*
   * Metodo: inserirNoGrupo
   * Funcao: Insere o usuario num grupo especifico criando a lista caso ele ainda
   * nao exista
   * Parametros: nomeGrupo = grupo alvo, nomeUsuario = usuario a inserir, saida =
   * fluxo TCP
   * Retorno: void
   */
  public void inserirNoGrupo(String nomeGrupo, String nomeUsuario, ObjectOutputStream saida) {
    String nomeGrupoValido = nomeGrupo != null ? nomeGrupo.trim() : "";
    String nomeUsuarioValido = nomeUsuario != null ? nomeUsuario.trim() : "";
    Usuario usuarioReal = servidor.usuariosOnline.get(nomeUsuarioValido);
    boolean sucesso = false;

    if (usuarioReal != null) {
      sucesso = true;
      if (servidor.grupos.containsKey(nomeGrupoValido)) {
        if (!servidor.grupos.get(nomeGrupoValido).contains(usuarioReal)) {
          servidor.grupos.get(nomeGrupoValido).add(usuarioReal);
          System.out.println("      -> SUCESSO: '" + nomeUsuarioValido + "' entrou no grupo existente '" + nomeGrupoValido + "'.");
        } else {
          sucesso = false;
        } // fim do if
      } else {
        ArrayList<Usuario> listaUsuario = new ArrayList<>();
        listaUsuario.add(usuarioReal);
        servidor.grupos.put(nomeGrupoValido, listaUsuario);
        System.out.println("      -> SUCESSO: O grupo '" + nomeGrupoValido + "' foi criado e '" + nomeUsuarioValido + "' entrou.");
      } // fim do if
    } // fim do if

    try {
      if (saida != null) {
        saida.writeObject(sucesso ? "OK: entrou no grupo" : "ERRO: falhou ao entrar no grupo");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao inserir usuario no grupo: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do inserirNoGrupo

  /*
   * Metodo: sairDoGrupo
   * Funcao: Remove o usuario da lista do grupo e deleta o grupo se esvaziar
   * Parametros: nomeGrupo = grupo alvo, nomeUsuario = usuario saindo, saida =
   * fluxo TCP
   * Retorno: void
   */
  public void sairDoGrupo(String nomeGrupo, String nomeUsuario, ObjectOutputStream saida) {
    boolean removido = false;
    if (servidor.grupos.containsKey(nomeGrupo)) {
      ArrayList<Usuario> lista = servidor.grupos.get(nomeGrupo);
      removido = lista.removeIf(u -> u.getNome().equals(nomeUsuario));
      if (lista.isEmpty()) {
        servidor.grupos.remove(nomeGrupo);
        System.out.println("      -> O grupo '" + nomeGrupo + "' ficou vazio e foi deletado da memoria.");
      } else if (removido) {
        System.out.println("      -> SUCESSO: '" + nomeUsuario + "' saiu do grupo '" + nomeGrupo + "'.");
      } // fim do if
    } // fim do if
    try {
      if (saida != null) {
        saida.writeObject(removido ? "OK: Saiu do grupo" : "ERRO: Usuario nao encontrado");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao responder saida do grupo: " + e.getMessage());
    } // fim do try-catch
  } // fim do sairDoGrupo

  /*
   * Metodo: bloquearUsuario
   * Funcao: Adiciona um alvo ao hashset de bloqueados do solicitante
   * Parametros: nomeUsuario = quem pediu o bloqueio, alvo = nome do bloqueado,
   * saida = fluxo TCP
   * Retorno: void
   */
  public void bloquearUsuario(String nomeUsuario, String alvo, ObjectOutputStream saida) {
    boolean bloqueado = false;
    Usuario quemPediu = servidor.usuariosOnline.get(nomeUsuario);
    if (quemPediu != null && alvo != null && validarNomeUsuario(alvo.trim())) {
      quemPediu.addBloqueados(alvo.trim());
      bloqueado = true;
      System.out.println("      -> SUCESSO: '" + nomeUsuario + "' acabou de BLOQUEAR '" + alvo + "'.");
    } // fim do if
    try {
      if (saida != null) {
        saida.writeObject(bloqueado ? "OK: Bloqueou com sucesso" : "ERRO: Falha ao bloquear");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao bloquear usuario: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do bloquearUsuario

  /*
   * Metodo: desbloquearUsuario
   * Funcao: Remove o alvo do hashset de bloqueados do solicitante
   * Parametros: nomeUsuario = quem pediu desbloqueio, alvo = nome desbloqueado,
   * saida = fluxo TCP
   * Retorno: void
   */
  public void desbloquearUsuario(String nomeUsuario, String alvo, ObjectOutputStream saida) {
    boolean desbloqueado = false;
    Usuario quemPediu = servidor.usuariosOnline.get(nomeUsuario);
    if (quemPediu != null && alvo != null && validarNomeUsuario(alvo.trim())) {
      quemPediu.getBloqueados().remove(alvo.trim());
      desbloqueado = true;
      System.out.println("      -> SUCESSO: '" + nomeUsuario + "' acabou de DESBLOQUEAR '" + alvo + "'.");
    } // fim do if
    try {
      if (saida != null) {
        saida.writeObject(desbloqueado ? "OK: Desbloqueou com sucesso" : "ERRO: Falha ao desbloquear");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao desbloquear usuario: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do desbloquearUsuario

  /*
   * Metodo: deslogarUsuario
   * Funcao: Remove o usuario da memoria central e limpa sua presenca de todos os
   * grupos
   * Parametros: nomeUsuario = usuario a deslogar, saida = fluxo TCP
   * Retorno: void
   */
  public void deslogarUsuario(String nomeUsuario, ObjectOutputStream saida) {
    boolean removido = false;

    if (nomeUsuario != null && validarNomeUsuario(nomeUsuario)) {
      for (ArrayList<Usuario> lista : servidor.grupos.values()) {
        lista.removeIf(u -> u.getNome().equals(nomeUsuario));
      } // fim do for

      servidor.grupos.entrySet().removeIf(entry -> entry.getValue().isEmpty());

      if (servidor.usuariosOnline.containsKey(nomeUsuario)) {
        servidor.usuariosOnline.remove(nomeUsuario);
        removido = true;
        System.out.println("      -> FAXINA DE LOGOUT: O usuario '" + nomeUsuario + "' foi apagado de todos os grupos e do sistema.");
      } // fim do if
    } // fim do if

    try {
      if (saida != null) {
        saida.writeObject(removido ? "OK: Logout com sucesso" : "ERRO: Usuario nao encontrado.");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao processar logout: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo deslogarUsuario

}