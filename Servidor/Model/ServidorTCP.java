/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 17/09/2026
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
  private int porta;
  private Servidor servidor;

  /*
   * Metodo: ServidorTCP (Construtor)
   * Funcao: Inicializar os atributos da thread tcp
   * Parametros: porta = porta de escuta, servidor = instancia principal da
   * memoria central
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
      System.out.println("SERVIDOR TCP - Escutando na porta " + porta);
      while (true) {
        Socket clientSocket = serverSocket.accept();
        new Thread(() -> processarConexao(clientSocket)).start();
      } // fim do while
    } catch (Exception e) {
      System.out.println("SERVIDOR TCP - Erro Critico na porta " + porta);
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
    try {
      ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());
      ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
      saida.flush();

      APDU apdu = (APDU) entrada.readObject();
      String operacao = apdu.getOperacao();

      servidor.mutex.acquire(); // Bloqueia a memoria

      switch (operacao) {
        case "REGISTER":
          registrarUsuario(apdu.getNomeUsuario(), socket.getInetAddress(), apdu.getPortaClienteUDP(), saida);
          break;
        case "LIST":
          listarGrupos(saida);
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

      servidor.mutex.release(); // Libera a memoria

    } catch (Exception e) {
    } // fim do try-catch
  } // fim do metodo processarConexao

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
      if (nome != null && !nome.trim().isEmpty() && !servidor.usuariosOnline.containsKey(nome)) {
        Usuario novoUsuario = new Usuario(ip, nome, portaUDP);
        servidor.usuariosOnline.put(nome, novoUsuario);
        registrado = true;
        System.out.println("SERVIDOR TCP - " + nome + " logou no sistema.");
      } // fim do if
      if (saida != null) {
        saida.writeObject(registrado ? "OK: registrado com sucesso" : "ERRO: nome de usuario em uso");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
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
      String str = String.join(",", servidor.grupos.keySet());
      if (saida != null) {
        saida.writeObject("OK: " + str);
        saida.flush();
      } // fim do if
    } catch (Exception e) {
    } // fim do try-catch
  } // fim do listarGrupos

  /*
   * Metodo: listarMembros
   * Funcao: Envia a string contendo os membros que estao num grupo
   * Parametros: nomeGrupo = nome do grupo alvo, saida = fluxo TCP
   * Retorno: void
   */
  public void listarMembros(String nomeGrupo, ObjectOutputStream saida) {
    try {
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
    Usuario usuarioReal = servidor.usuariosOnline.get(nomeUsuario);
    boolean sucesso = true;
    if (usuarioReal != null) {
      if (servidor.grupos.containsKey(nomeGrupo)) {
        if (!servidor.grupos.get(nomeGrupo).contains(usuarioReal)) {
          servidor.grupos.get(nomeGrupo).add(usuarioReal);
        } else
          sucesso = false;
      } else {
        ArrayList<Usuario> listaUsuario = new ArrayList<>();
        listaUsuario.add(usuarioReal);
        servidor.grupos.put(nomeGrupo, listaUsuario);
      } // fim do if
    } else
      sucesso = false;

    try {
      if (saida != null) {
        saida.writeObject(sucesso ? "OK: entrou no grupo" : "ERRO: falhou ao entrar no grupo");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
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
      if (lista.isEmpty())
        servidor.grupos.remove(nomeGrupo);
    } // fim do if
    try {
      if (saida != null) {
        saida.writeObject(removido ? "OK: Saiu do grupo" : "ERRO: Usuario nao encontrado");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
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
    if (quemPediu != null && alvo != null && !alvo.trim().isEmpty()) {
      quemPediu.addBloqueados(alvo.trim());
      bloqueado = true;
    } // fim do if
    try {
      if (saida != null) {
        saida.writeObject(bloqueado ? "OK: Bloqueou com sucesso" : "ERRO: Falha ao bloquear");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
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
    if (quemPediu != null && alvo != null && !alvo.trim().isEmpty()) {
      quemPediu.getBloqueados().remove(alvo.trim());
      desbloqueado = true;
    } // fim do if
    try {
      if (saida != null) {
        saida.writeObject(desbloqueado ? "OK: Desbloqueou com sucesso" : "ERRO: Falha ao desbloquear");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
    } // fim do try-catch
  } // fim do desbloquearUsuario

  /*
   * Metodo: deslogarUsuario
   * Funcao: Remove o usuario do map central de usuarios online
   * Parametros: nomeUsuario = usuario a deslogar, saida = fluxo TCP
   * Retorno: void
   */
  public void deslogarUsuario(String nomeUsuario, ObjectOutputStream saida) {
    boolean removido = false;
    if (servidor.usuariosOnline.containsKey(nomeUsuario)) {
      servidor.usuariosOnline.remove(nomeUsuario);
      removido = true;
      System.out.println("SERVIDOR TCP - " + nomeUsuario + " deslogou.");
    } // fim do if
    try {
      if (saida != null) {
        saida.writeObject(removido ? "OK: Logout com sucesso" : "ERRO: Usuario nao encontrado.");
        saida.flush();
      } // fim do if
    } catch (Exception e) {
    } // fim do try-catch
  } // fim do metodo deslogarUsuario
}