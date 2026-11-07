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
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class ServidorTCP extends Thread {
  private static final int TIMEOUT_TCP = 5000;
  private int porta;
  private Servidor servidor;
  private ServerSocket serverSocket;
  private volatile boolean executando = true;

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
    int tentativas = 5;
    while (executando && tentativas > 0) {
      try {
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new java.net.InetSocketAddress(porta));
        System.out.println("[TCP] Escutando na porta " + porta);
        break;
      } catch (Exception e) {
        tentativas--;
        if (tentativas == 0) {
          System.out.println("[TCP-ERRO] Erro Critico na porta " + porta + ": " + e.getMessage());
          return;
        }
        try {
          Thread.sleep(300);
        } catch (InterruptedException ignored) {
        }
      }
    }

    try {
      while (executando && serverSocket != null && !serverSocket.isClosed()) {
        Socket clientSocket = serverSocket.accept();
        clientSocket.setSoTimeout(TIMEOUT_TCP);
        new Thread(() -> processarConexao(clientSocket)).start();
      } // fim do while
    } catch (Exception e) {
      if (executando) {
        System.out.println("[TCP-ERRO] Erro no accept da porta " + porta + ": " + e.getMessage());
      }
    } finally {
      parar();
    }
  } // fim do metodo run

  /*
   * Metodo: parar
   * Funcao: Fecha o serverSocket e encerra o loop de escuta
   */
  public void parar() {
    this.executando = false;
    try {
      if (this.serverSocket != null && !this.serverSocket.isClosed()) {
        this.serverSocket.close();
      }
    } catch (Exception ignored) {
    }
  }

  /*
   * Metodo: processarConexao
   * Funcao: Le o objeto APDU via socket TCP e repassa para o metodo
   * correspondente
   * Parametros: socket = socket do cliente conectado
   * Retorno: void
   */
  private void processarConexao(Socket socket) {
    try (Socket socketCliente = socket) {
      ObjectOutputStream saida = new ObjectOutputStream(socket.getOutputStream());
      saida.flush();
      ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream());

      APDU apdu = (APDU) entrada.readObject();
      String operacao = apdu.getOperacao();
      InetAddress ipClienteReal = resolverIpRealCliente(socket.getInetAddress());

      System.out.println("\n[TCP] >>> Requisicao recebida: [" + operacao + "] de '" + apdu.getNomeUsuario() + "'");

      // Roteamento Proxy/Forwarding para operacoes de grupo (JOIN, LEAVE, MEMBERS)
      if ("JOIN".equals(operacao) || "LEAVE".equals(operacao) || "MEMBERS".equals(operacao)) {
        int conjuntoAlvo = Servidor.calcularConjuntoPorNome(apdu.getNomeGrupo());
        if (conjuntoAlvo != servidor.getConjunto()) {
          if ("JOIN".equals(operacao)) {
            garantirUsuarioOnlineLocal(apdu.getNomeUsuario(), ipClienteReal, apdu.getPortaClienteUDP());
          }
          encaminharRequisicaoTCP(apdu, conjuntoAlvo, saida);
          return;
        }
      }

      // Operacao LIST: diferencia consulta interna entre servidores vs requisicao de cliente
      if ("LIST".equals(operacao)) {
        boolean consultaInterna = "INTERNAL".equals(apdu.getNomeGrupo())
            || "SERVIDOR".equals(apdu.getNomeUsuario());
        if (consultaInterna) {
          servidor.mutex.acquire();
          try {
            listarGrupos(saida);
          } finally {
            servidor.mutex.release();
          }
        } else {
          listarGruposConsolidado(saida);
        }
        return;
      }

      boolean propagarLogout = "LOGOUT".equals(operacao) && !"INTERNAL".equals(apdu.getNomeGrupo());

      servidor.mutex.acquire();
      try {
        switch (operacao) {
          case "REGISTER":
            registrarUsuario(apdu.getNomeUsuario(), ipClienteReal, apdu.getPortaClienteUDP(), saida);
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
            inserirNoGrupo(apdu.getNomeGrupo(), apdu.getNomeUsuario(), ipClienteReal,
                apdu.getPortaClienteUDP(), saida);
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

      if (propagarLogout) {
        propagarLogoutRemoto(apdu.getNomeUsuario());
      }

    } catch (java.io.EOFException | java.net.SocketException ignored) {
      // Conexao encerrada sem envio de APDU
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Erro ao processar requisicao: " + e.getMessage());
    } // fim do try-catch
  } // fim do metodo processarConexao

  /*
   * Metodo: resolverIpRealCliente
   * Funcao: Caso a conexao venha via loopback (127.0.0.1), converte para o IP real da placa de rede
   * para que servidores e backups em outras maquinas consigam alcancar o cliente via UDP
   */
  private InetAddress resolverIpRealCliente(InetAddress ipSocket) {
    try {
      if (ipSocket != null && ipSocket.isLoopbackAddress()) {
        String ipLocal = Servidor.obterIpLocalServidor();
        if (ipLocal != null && !ipLocal.equals("127.0.0.1")) {
          return InetAddress.getByName(ipLocal);
        }
      }
    } catch (Exception ignored) {
    }
    return ipSocket;
  }

  /*
   * Metodo: propagarLogoutRemoto
   * Funcao: Propaga o comando de LOGOUT internamente para as outras particoes ativas do cluster
   */
  private void propagarLogoutRemoto(String nomeUsuario) {
    if (nomeUsuario == null || nomeUsuario.trim().isEmpty()) {
      return;
    }
    for (int c = 1; c <= 3; c++) {
      if (c == servidor.getConjunto()) {
        continue;
      }
      final int conjuntoAlvo = c;
      new Thread(() -> {
        try (Socket socketInterno = new Socket()) {
          InetAddress ipLider = servidor.obterIpLiderConjunto(conjuntoAlvo);
          int portaLider = Servidor.calcularPortaTCPPorConjunto(conjuntoAlvo);
          String hostLider = ipLider != null ? ipLider.getHostAddress() : "127.0.0.1";
          socketInterno.connect(new InetSocketAddress(hostLider, portaLider), 800);
          socketInterno.setSoTimeout(800);

          ObjectOutputStream out = new ObjectOutputStream(socketInterno.getOutputStream());
          out.flush();
          ObjectInputStream in = new ObjectInputStream(socketInterno.getInputStream());

          out.writeObject(new APDU("LOGOUT", "INTERNAL", nomeUsuario.trim(), null, 0));
          out.flush();
          in.readObject();
        } catch (Exception ignored) {
        }
      }).start();
    }
  }

  /*
   * Metodo: validarNomeUsuario
   * Funcao: Valida o nome de usuario
   * Parametros: nome = nome de usuario
   * Retorno: boolean
   */
  private boolean validarNomeUsuario(String nome) {
    return nome != null && nome.trim().length() >= 2 && nome.trim().length() <= 20
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
   * Metodo: usuarioFantasmaInativo
   * Funcao: Verifica se um registro existente em usuariosOnline pertence a um cliente local
   * que sofreu queda abrupta (kill -9), testando se a porta UDP antiga ja foi liberada pelo SO
   */
  private boolean usuarioFantasmaInativo(Usuario existente, InetAddress novoIp, int novaPortaUDP) {
    if (existente == null) {
      return true;
    }
    if (existente.getPorta() == novaPortaUDP && existente.getIp() != null && existente.getIp().equals(novoIp)) {
      return true;
    }
    try {
      InetAddress ipExistente = existente.getIp();
      boolean ipLocal = ipExistente != null
          && (ipExistente.isLoopbackAddress() || java.net.NetworkInterface.getByInetAddress(ipExistente) != null);
      if (ipLocal) {
        try (java.net.DatagramSocket socketTeste = new java.net.DatagramSocket(existente.getPorta())) {
          // Conseguiu abrir a porta UDP antiga: o processo anterior do cliente ja morreu!
          return true;
        } catch (Exception portaEmUso) {
          // Porta UDP antiga continua aberta: o cliente original ainda esta ativo!
          return false;
        }
      }
    } catch (Exception ignored) {
    }
    return false;
  }

  /*
   * Metodo: registrarUsuario
   * Funcao: Adiciona um novo usuario na lista de conectados (ou recupera sessao apos queda abrupta)
   * Parametros: nome = nome de usuario, ip = ip do cliente, portaUDP = porta que
   * o cliente ira escutar as apdus, saida = fluxo TCP
   * Retorno: void
   */
  public void registrarUsuario(String nome, InetAddress ip, int portaUDP, ObjectOutputStream saida) {
    boolean registrado = false;
    try {
      String nomeValido = nome != null ? nome.trim() : "";
      Usuario existente = servidor.usuariosOnline.get(nomeValido);

      if (validarNomeUsuario(nomeValido) && (existente == null || usuarioFantasmaInativo(existente, ip, portaUDP))) {
        Usuario novoUsuario = new Usuario(ip, nomeValido, portaUDP);
        if (existente != null) {
          for (String b : existente.getBloqueados()) {
            novoUsuario.addBloqueados(b);
          }
          for (ArrayList<Usuario> membros : servidor.grupos.values()) {
            for (int i = 0; i < membros.size(); i++) {
              if (membros.get(i).getNome().equals(nomeValido)) {
                membros.set(i, novoUsuario);
              }
            }
          }
          System.out.println("      -> [RECONEXAO] Sessao anterior inativa de '" + nomeValido
              + "' atualizada para nova porta UDP " + portaUDP + ".");
        }
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
      if (registrado) {
        servidor.notificarAlteracaoMemoria();
      }
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao registrar usuario: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do registrarUsuario

  /*
   * Metodo: garantirUsuarioOnlineLocal
   * Funcao: Garante que o usuario esteja registrado com o IP real do cliente antes de
   * encaminhar um JOIN via Proxy TCP para outro conjunto
   */
  private void garantirUsuarioOnlineLocal(String nomeUsuario, InetAddress ipCliente, int portaUDP) {
    if (nomeUsuario == null || !validarNomeUsuario(nomeUsuario.trim()) || ipCliente == null || portaUDP <= 0) {
      return;
    }
    String nomeValido = nomeUsuario.trim();
    boolean alterado = false;
    try {
      servidor.mutex.acquire();
      try {
        Usuario u = servidor.usuariosOnline.get(nomeValido);
        if (u == null) {
          servidor.usuariosOnline.put(nomeValido, new Usuario(ipCliente, nomeValido, portaUDP));
          alterado = true;
        } else if (u.getPorta() != portaUDP || u.getIp() == null || !u.getIp().equals(ipCliente)) {
          u.setIp(ipCliente);
          u.setPorta(portaUDP);
          alterado = true;
        }
      } finally {
        servidor.mutex.release();
      }
      if (servidor.isLider() && servidor.getGerenteRedundancia() != null) {
        servidor.getGerenteRedundancia().enviarSnapshotSincrono();
      } else if (alterado) {
        servidor.notificarAlteracaoMemoria();
      }
    } catch (Exception ignored) {
    }
  }

  /*
   * Metodo: encaminharRequisicaoTCP
   * Funcao: Atua como Proxy/Roteador transparente, encaminhando a APDU via TCP interno
   * ao Lider responsavel pelo conjuntoAlvo e devolvendo a resposta ao cliente conectado
   */
  private void encaminharRequisicaoTCP(APDU apdu, int conjuntoAlvo, ObjectOutputStream saidaCliente) {
    InetAddress ipLider = servidor.obterIpLiderConjunto(conjuntoAlvo);
    int portaLider = Servidor.calcularPortaTCPPorConjunto(conjuntoAlvo);
    String hostLider = ipLider != null ? ipLider.getHostAddress() : "127.0.0.1";

    System.out.println("      -> [PROXY-TCP] Grupo '" + apdu.getNomeGrupo() + "' pertence ao Conjunto "
        + conjuntoAlvo + ". Encaminhando [" + apdu.getOperacao() + "] para Lider " + hostLider + ":" + portaLider);

    try (Socket socketInterno = new Socket()) {
      socketInterno.connect(new InetSocketAddress(hostLider, portaLider), 2000);
      socketInterno.setSoTimeout(3000);

      ObjectOutputStream outInterno = new ObjectOutputStream(socketInterno.getOutputStream());
      outInterno.flush();
      ObjectInputStream inInterno = new ObjectInputStream(socketInterno.getInputStream());

      outInterno.writeObject(apdu);
      outInterno.flush();

      Object resposta = inInterno.readObject();
      System.out.println("      -> [PROXY-TCP] Resposta recebida do Conjunto " + conjuntoAlvo + ": " + resposta);

      if (saidaCliente != null) {
        saidaCliente.writeObject(resposta);
        saidaCliente.flush();
      }
    } catch (Exception e) {
      System.err.println("      -> [PROXY-TCP-ERRO] Falha ao encaminhar [" + apdu.getOperacao()
          + "] para Conjunto " + conjuntoAlvo + " (" + hostLider + ":" + portaLider + "): " + e.getMessage());
      try {
        if (saidaCliente != null) {
          saidaCliente.close();
        }
      } catch (Exception ignored) {
      }
    }
  }

  /*
   * Metodo: listarGrupos
   * Funcao: Envia a string contendo os nomes dos grupos existentes na particao local
   * Parametros: saida = fluxo TCP
   * Retorno: void
   */
  public void listarGrupos(ObjectOutputStream saida) {
    try {
      System.out.println("      -> Enviando lista local de grupos do Conjunto " + servidor.getConjunto() + ".");
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
   * Metodo: listarGruposConsolidado
   * Funcao: Consolida os grupos locais com os grupos das demais particoes ativas do cluster
   * e devolve uma lista unificada ao cliente
   */
  private void listarGruposConsolidado(ObjectOutputStream saida) {
    Set<String> todosGrupos = new LinkedHashSet<>();
    try {
      servidor.mutex.acquire();
      try {
        todosGrupos.addAll(servidor.grupos.keySet());
      } finally {
        servidor.mutex.release();
      }
    } catch (Exception ignored) {
    }

    for (int c = 1; c <= 3; c++) {
      if (c == servidor.getConjunto()) {
        continue;
      }
      Set<String> remotos = consultarGruposRemotos(c);
      todosGrupos.addAll(remotos);
    }

    try {
      String str = String.join(",", todosGrupos);
      System.out.println("      -> [PROXY-LIST] Enviando lista consolidada das 3 particoes: [" + str + "]");
      if (saida != null) {
        saida.writeObject("OK: " + str);
        saida.flush();
      }
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao enviar lista consolidada de grupos: " + e.getMessage());
    }
  }

  /*
   * Metodo: consultarGruposRemotos
   * Funcao: Consulta internamente via TCP os grupos existentes no Lider de outra particao
   */
  private Set<String> consultarGruposRemotos(int conjuntoAlvo) {
    Set<String> resultado = new LinkedHashSet<>();
    InetAddress ipLider = servidor.obterIpLiderConjunto(conjuntoAlvo);
    int portaLider = Servidor.calcularPortaTCPPorConjunto(conjuntoAlvo);
    String hostLider = ipLider != null ? ipLider.getHostAddress() : "127.0.0.1";

    try (Socket socketInterno = new Socket()) {
      socketInterno.connect(new InetSocketAddress(hostLider, portaLider), 1200);
      socketInterno.setSoTimeout(1500);

      ObjectOutputStream outInterno = new ObjectOutputStream(socketInterno.getOutputStream());
      outInterno.flush();
      ObjectInputStream inInterno = new ObjectInputStream(socketInterno.getInputStream());

      APDU apduInterna = new APDU("LIST", "INTERNAL", "SERVIDOR", null, 0);
      outInterno.writeObject(apduInterna);
      outInterno.flush();

      String resposta = (String) inInterno.readObject();
      if (resposta != null && resposta.startsWith("OK:")) {
        String dados = resposta.substring(3).trim();
        if (!dados.isEmpty()) {
          for (String g : dados.split(",")) {
            if (!g.trim().isEmpty()) {
              resultado.add(g.trim());
            }
          }
        }
      }
    } catch (Exception ignored) {
      // Particao remota sem Lider ativo no momento; segue com as particoes disponiveis
    }
    return resultado;
  }

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
      ArrayList<String> nomesUsuarios = new ArrayList<>();
      if (servidor.grupos.containsKey(nomeGrupo)) {
        for (Usuario u : servidor.grupos.get(nomeGrupo)) {
          nomesUsuarios.add(u.getNome());
        } // fim do for
      } // fim do if
      if (saida != null) {
        saida.writeObject("OK: " + String.join(",", nomesUsuarios));
        saida.flush();
      } // fim do if
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao listar membros do grupo '" + nomeGrupo + "': " + e.getMessage());
    } // fim do try-catch
  } // fim do listarMembros

  /*
   * Metodo: inserirNoGrupo
   * Funcao: Insere o usuario num grupo especifico criando a lista caso ele ainda
   * nao exista
   * Parametros: nomeGrupo = grupo alvo, nomeUsuario = usuario a inserir, ip = IP do cliente,
   * portaUDP = porta UDP do cliente, saida = fluxo TCP
   * Retorno: void
   */
  public void inserirNoGrupo(String nomeGrupo, String nomeUsuario, InetAddress ip, int portaUDP,
      ObjectOutputStream saida) {
    String nomeGrupoValido = nomeGrupo != null ? nomeGrupo.trim() : "";
    String nomeUsuarioValido = nomeUsuario != null ? nomeUsuario.trim() : "";
    Usuario usuarioReal = servidor.usuariosOnline.get(nomeUsuarioValido);
    boolean sucesso = false;

    // Caso o usuario tenha feito login noutro conjunto e o pacote Multicast tenha atrasado
    // ou este servidor tenha subido depois, sincroniza o usuario automaticamente no JOIN
    if (usuarioReal == null && validarNomeUsuario(nomeUsuarioValido) && ip != null && portaUDP > 0) {
      usuarioReal = new Usuario(ip, nomeUsuarioValido, portaUDP);
      servidor.usuariosOnline.put(nomeUsuarioValido, usuarioReal);
      System.out.println("      -> [SYNC-AUTO] Usuario '" + nomeUsuarioValido
          + "' adicionado a lista global de usuarios online deste conjunto.");
    }

    if (usuarioReal != null) {
      if (portaUDP > 0) {
        usuarioReal.setPorta(portaUDP);
      }
      if (usuarioReal.getIp() == null && ip != null) {
        usuarioReal.setIp(ip);
      }
      sucesso = true;
      if (servidor.grupos.containsKey(nomeGrupoValido)) {
        ArrayList<Usuario> membros = servidor.grupos.get(nomeGrupoValido);
        boolean jaMembro = false;
        for (int i = 0; i < membros.size(); i++) {
          if (membros.get(i).getNome().equals(nomeUsuarioValido)) {
            membros.set(i, usuarioReal);
            jaMembro = true;
            break;
          }
        }
        if (!jaMembro) {
          membros.add(usuarioReal);
          System.out.println("      -> SUCESSO: '" + nomeUsuarioValido + "' entrou no grupo existente '" + nomeGrupoValido + "'.");
        } else {
          System.out.println("      -> SUCESSO: '" + nomeUsuarioValido + "' reconectado/atualizado no grupo '" + nomeGrupoValido + "'.");
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
      if (sucesso) {
        servidor.notificarAlteracaoMemoria();
      }
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
      if (removido) {
        servidor.notificarAlteracaoMemoria();
      }
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
      if (bloqueado) {
        servidor.notificarAlteracaoMemoria();
      }
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
      if (desbloqueado) {
        servidor.notificarAlteracaoMemoria();
      }
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
    String nomeValido = nomeUsuario != null ? nomeUsuario.trim() : "";

    if (!nomeValido.isEmpty() && validarNomeUsuario(nomeValido)) {
      for (ArrayList<Usuario> lista : servidor.grupos.values()) {
        if (lista.removeIf(u -> u.getNome().equals(nomeValido))) {
          removido = true;
        }
      } // fim do for

      servidor.grupos.entrySet().removeIf(entry -> entry.getValue().isEmpty());

      if (servidor.usuariosOnline.remove(nomeValido) != null) {
        removido = true;
      } // fim do if

      if (removido) {
        System.out.println("      -> FAXINA DE LOGOUT: O usuario '" + nomeValido + "' foi apagado de todos os grupos e do sistema.");
      } // fim do if
    } // fim do if

    try {
      if (saida != null) {
        saida.writeObject(removido ? "OK: Logout com sucesso" : "ERRO: Usuario nao encontrado.");
        saida.flush();
      } // fim do if
      if (removido) {
        servidor.notificarAlteracaoMemoria();
      }
    } catch (Exception e) {
      System.err.println("[TCP-ERRO] Falha ao processar logout: " + e.getMessage());
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo deslogarUsuario

}