/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 17/06/2026
* Ultima alteracao.: 20/09/2026
* Nome.............: ServidorUDP.java
* Funcao...........: Lidar com pacotes rapidos (mensagens, ticks e visualizacao unica) sem garantir conexao
*******************************************************************/
package Model;

import Protocol.APDU;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;

public class ServidorUDP extends Thread {
  private static final class RastreioMensagem {
    private final HashSet<String> esperados;
    private final HashSet<String> recebidos;
    private final HashSet<String> lidos;

    private RastreioMensagem(HashSet<String> esperados) {
      this.esperados = esperados;
      this.recebidos = new HashSet<>();
      this.lidos = new HashSet<>();
    }
  }

  private final Object rastreioLock = new Object();
  private int porta;
  private Servidor servidor;
  private DatagramSocket endpointServidor;

  /*
   * Metodo: ServidorUDP (Construtor)
   * Funcao: Inicializar os atributos e abrir a porta UDP
   * Parametros: porta = porta de escuta UDP, servidor = instancia principal da
   * memoria central
   */
  public ServidorUDP(int porta, Servidor servidor) {
    this.porta = porta;
    this.servidor = servidor;
    try {
      this.endpointServidor = new DatagramSocket(porta);
    } catch (Exception e) {
      System.out.println("[UDP-ERRO] Falha ao iniciar Servidor UDP.");
    } // fim do try-catch
  } // fim do ServidorUDP

  /*
   * Metodo: run
   * Funcao: Executa o loop infinito lendo objetos APDU da rede e encaminhando pro
   * roteador
   * Parametros: nenhum
   * Retorno: void
   */
  @Override
  public void run() {
    if (endpointServidor == null) {
      System.err.println("[UDP-ERRO] Escuta UDP nao iniciada porque o socket nao foi criado.");
      return;
    }

    System.out.println("[UDP] Escutando na porta " + porta);
    while (true) {
      try {
        byte[] dadosEntrada = new byte[65507];
        DatagramPacket pacote = new DatagramPacket(dadosEntrada, dadosEntrada.length);
        endpointServidor.receive(pacote);

        ByteArrayInputStream bais = new ByteArrayInputStream(pacote.getData(), 0, pacote.getLength());
        ObjectInputStream in = new ObjectInputStream(bais);
        APDU apduRecebida = (APDU) in.readObject();

        new Thread(() -> {
          try {
            servidor.mutex.acquire();
            try {
              processarApduUDP(apduRecebida);
            } finally {
              servidor.mutex.release();
            }
          } catch (Exception e) {
            System.err.println("[UDP-ERRO] Falha ao processar APDU: " + e.getMessage());
          }
        }).start();

      } catch (Exception e) {
        System.err.println("[UDP-ERRO] Falha ao receber APDU: " + e.getMessage());
      } // fim do try-catch
    } // fim do while
  } // fim do run

  /*
   * Metodo: processarApduUDP
   * Funcao: Analisa o conteudo da operacao e decide qual fluxo de mensagem chamar
   * Parametros: apdu = apdu extraida do pacote UDP
   * Retorno: void
   */
  private void processarApduUDP(APDU apdu) {
    String operacao = apdu.getOperacao();

    if (!operacao.equals("CONFIRM")) {
      System.out.println("\n[UDP] >>> Objeto recebido: [" + operacao + "] enviado por '" + apdu.getNomeUsuario() + "'");
    } // fim do if

    switch (operacao) {
      case "SEND":
        avisarRecebimentoServidor(apdu);
        enviarMensagem(apdu, apdu.getNomeGrupo(), apdu.getNomeUsuario());
        break;
      case "SENDPVT":
        avisarRecebimentoServidor(apdu);
        enviarMensagemPrivado(apdu, apdu.getDestinatario(), apdu.getNomeUsuario());
        break;
      case "SENDVU":
        avisarRecebimentoServidor(apdu);
        if (apdu.getDestinatario() != null) {
          enviarMensagemPrivado(apdu, apdu.getDestinatario(), apdu.getNomeUsuario());
        } else {
          enviarMensagem(apdu, apdu.getNomeGrupo(), apdu.getNomeUsuario());
        }
        break;
      case "CONFIRM":
        encaminharConfirmacao(apdu);
        break;
    } // fim do switch-case
  } // fim do processarApduUDP

  /*
   * Metodo: enviarObjetoUDP
   * Funcao: Transforma a APDU em bytes e envia fisicamente pela rede
   * Parametros: apdu = objeto a ser enviado, ip = ip destino, porta = porta udp
   * do destino
   * Retorno: void
   */
  private void enviarObjetoUDP(APDU apdu, InetAddress ip, int porta) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(baos);
      out.writeObject(apdu);
      out.flush();
      byte[] dados = baos.toByteArray();
      DatagramPacket pacoteEnvio = new DatagramPacket(dados, dados.length, ip, porta);
      endpointServidor.send(pacoteEnvio);
    } catch (Exception e) {
      System.err.println("[UDP-ERRO] Falha ao enviar APDU para " + ip + ":" + porta + ": " + e.getMessage());
    } // fim do try-catch
  } // fim do enviarObjetoUDP

  /*
   * Metodo: avisarRecebimentoServidor
   * Funcao: Retorna o tick automatico confirmando que o UDP do servidor pegou a
   * mensagem
   * Parametros: apduOriginal = mensagem original enviada pelo usuario
   * Retorno: void
   */
  private void avisarRecebimentoServidor(APDU apduOriginal) {
    System.out.println("      -> Servidor interceptou a mensagem. Retornando TICK 1 (Cinza) para o remetente.");
    APDU tick1 = new APDU("CONFIRM", apduOriginal.getIdMensagem(), 1, "SERVIDOR", apduOriginal.getNomeGrupo(),
        apduOriginal.getNomeUsuario());
    encaminharConfirmacao(tick1);
  } // fim do avisarRecebimentoServidor

  /*
   * Metodo: enviarMensagem
   * Funcao: Roteia uma mensagem de grupo listando quais membros devem retornar
   * confirmacoes
   * Parametros: apdu = mensagem APDU, nomeGrupo = destino, nomeUsuarioRemetente =
   * quem enviou
   * Retorno: void
   */
  public void enviarMensagem(APDU apdu, String nomeGrupo, String nomeUsuarioRemetente) {
    if (!servidor.grupos.containsKey(nomeGrupo))
      return;
    Usuario usuarioRemetente = servidor.usuariosOnline.get(nomeUsuarioRemetente);
    ArrayList<Usuario> listaDeUsuarios = servidor.grupos.get(nomeGrupo);

    if (usuarioRemetente == null) {
      System.out.println("      -> AVISO: Remetente nao encontrado no servidor. Mensagem ignorada.");
      return;
    } // fim do if

    HashSet<String> membrosEsperados = new HashSet<>();

    for (Usuario usuario : listaDeUsuarios) {
      if (!usuario.getNome().equals(nomeUsuarioRemetente) &&
          !usuarioRemetente.getBloqueados().contains(usuario.getNome()) &&
          !usuario.getBloqueados().contains(nomeUsuarioRemetente)) {
        membrosEsperados.add(usuario.getNome());
      } // fim do if
    } // fim do for

    System.out.println(
        "      -> Roteando mensagem de grupo para " + membrosEsperados.size() + " destinatario(s) elegivel(is).");

    synchronized (rastreioLock) {
      if (!membrosEsperados.isEmpty()) {
        servidor.esperadosGrupo.put(apdu.getIdMensagem(), membrosEsperados);
        servidor.recebidosGrupo.put(apdu.getIdMensagem(), new HashSet<>());
        servidor.lidosGrupo.put(apdu.getIdMensagem(), new HashSet<>());

        for (Usuario usuario : listaDeUsuarios) {
          if (membrosEsperados.contains(usuario.getNome())) {
            enviarObjetoUDP(apdu, usuario.getIp(), usuario.getPorta());
          } // fim do if
        } // fim do for
      } else {
        if (listaDeUsuarios.size() == 2) {
          System.out.println("      -> NEGADO: O outro usuario do grupo bloqueou o envio.");
          APDU bloqueio = new APDU("CONFIRM", apdu.getIdMensagem(), -1, "SERVIDOR", apdu.getNomeGrupo(),
              apdu.getNomeUsuario());
          enviarObjetoUDP(bloqueio, usuarioRemetente.getIp(), usuarioRemetente.getPorta());
        } else {
          System.out.println(
              "      -> AVISO: Todos no grupo bloquearam o envio. Gerando ticks finalizados para destravar a tela do cliente.");
          APDU tick2 = new APDU("CONFIRM", apdu.getIdMensagem(), 2, "SERVIDOR", apdu.getNomeGrupo(),
              apdu.getNomeUsuario());
          enviarObjetoUDP(tick2, usuarioRemetente.getIp(), usuarioRemetente.getPorta());
          APDU tick3 = new APDU("CONFIRM", apdu.getIdMensagem(), 3, "SERVIDOR", apdu.getNomeGrupo(),
              apdu.getNomeUsuario());
          enviarObjetoUDP(tick3, usuarioRemetente.getIp(), usuarioRemetente.getPorta());
        } // fim do if
      } // fim do if
    } // fim do synchronized
  } // fim do enviarMensagem

  /*
   * Metodo: enviarMensagemPrivado
   * Funcao: Roteia uma mensagem privada respeitando bloqueios e aguardando
   * confirmacoes
   * Parametros: apdu = mensagem APDU, nomeUsuarioDestino = quem recebe,
   * nomeUsuarioRemetente = quem envia
   * Retorno: void
   */
  public void enviarMensagemPrivado(APDU apdu, String nomeUsuarioDestino, String nomeUsuarioRemetente) {
    Usuario usuarioDestino = servidor.usuariosOnline.get(nomeUsuarioDestino);
    Usuario usuarioRemetente = servidor.usuariosOnline.get(nomeUsuarioRemetente);

    if (usuarioDestino != null && usuarioRemetente != null &&
        !usuarioDestino.getBloqueados().contains(nomeUsuarioRemetente) &&
        !usuarioRemetente.getBloqueados().contains(nomeUsuarioDestino)) {

      System.out
          .println("      -> Roteando mensagem privada com sucesso para o destinatario '" + nomeUsuarioDestino + "'.");
      enviarObjetoUDP(apdu, usuarioDestino.getIp(), usuarioDestino.getPorta());

      synchronized (rastreioLock) {
        HashSet<String> esperado = new HashSet<>();
        esperado.add(usuarioDestino.getNome());
        servidor.esperadosGrupo.put(apdu.getIdMensagem(), esperado);
        servidor.recebidosGrupo.put(apdu.getIdMensagem(), new HashSet<>());
        servidor.lidosGrupo.put(apdu.getIdMensagem(), new HashSet<>());
      }
    } else if (usuarioRemetente != null) {
      System.out
          .println("      -> NEGADO: Bloqueio entre os usuarios. O Destinatario nao ira receber a mensagem.");
      APDU bloqueio = new APDU("CONFIRM", apdu.getIdMensagem(), -1, "SERVIDOR", null, apdu.getNomeUsuario());
      enviarObjetoUDP(bloqueio, usuarioRemetente.getIp(), usuarioRemetente.getPorta());
    } // fim do if
  } // fim do enviarMensagemPrivado

  /*
   * Metodo: encaminharConfirmacao
   * Funcao: Avalia os ticks pendentes no servidor e repassa ao dono original
   * quando todos estiverem concluídos
   * Parametros: apdu = pacote de tick que acabou de chegar
   * Retorno: void
   */
  public void encaminharConfirmacao(APDU apdu) {
    if (apdu == null || apdu.getIdMensagem() == null || apdu.getIdMensagem().trim().isEmpty()) {
      return;
    } // fim do if

    String id = apdu.getIdMensagem();
    int status = apdu.getStatusRecebido();

    if (apdu.getNomeUsuario() == null || apdu.getNomeUsuario().trim().isEmpty()) {
      return;
    } // fim do if

    if (status == 1) {
      Usuario dono = servidor.usuariosOnline.get(apdu.getDonoDaMensagem());
      if (dono != null)
        enviarObjetoUDP(apdu, dono.getIp(), dono.getPorta());
      return;
    } // fim do if

    synchronized (rastreioLock) {
      if (servidor.esperadosGrupo.containsKey(id)) {
        HashSet<String> esperados = servidor.esperadosGrupo.get(id);
        String quemConfirmou = apdu.getNomeUsuario();

        if (quemConfirmou == null || quemConfirmou.trim().isEmpty()) {
          return;
        } // fim do if

        if (status == 2) {
          HashSet<String> recebidos = servidor.recebidosGrupo.getOrDefault(id, new HashSet<>());
          if (esperados.contains(quemConfirmou)) {
            recebidos.add(quemConfirmou);
            servidor.recebidosGrupo.put(id, recebidos);
          } // fim do if

          System.out.println("[RASTREIO TICK 2] '" + quemConfirmou + "' RECEBEU a mensagem (" + recebidos.size() + "/" + esperados.size() + ").");

          if (recebidos.containsAll(esperados)) {
            System.out.println("      -> TICK 2 COMPLETO! Todos do grupo receberam. Encaminhando confirmacao para o remetente original.");
            Usuario dono = servidor.usuariosOnline.get(apdu.getDonoDaMensagem());
            if (dono != null) {
              enviarObjetoUDP(apdu, dono.getIp(), dono.getPorta());
            } // fim do if
          } // fim do if
        } else if (status == 3) {
          HashSet<String> lidos = servidor.lidosGrupo.getOrDefault(id, new HashSet<>());
          if (esperados.contains(quemConfirmou)) {
            lidos.add(quemConfirmou);
            servidor.lidosGrupo.put(id, lidos);
          } // fim do if

          System.out.println("[RASTREIO TICK 3] '" + quemConfirmou + "' LEU a mensagem (" + lidos.size() + "/" + esperados.size() + ").");

          if (lidos.containsAll(esperados)) {
            System.out.println("      -> TICK 3 COMPLETO! Todos do grupo leram. Encaminhando confirmacao para o remetente original e apagando rastreio da RAM.");
            Usuario dono = servidor.usuariosOnline.get(apdu.getDonoDaMensagem());
            if (dono != null) {
              enviarObjetoUDP(apdu, dono.getIp(), dono.getPorta());
            } // fim do if

            servidor.esperadosGrupo.remove(id);
            servidor.recebidosGrupo.remove(id);
            servidor.lidosGrupo.remove(id);
          } // fim do if
        } // fim do if
      } else {
        Usuario dono = servidor.usuariosOnline.get(apdu.getDonoDaMensagem());
        if (dono != null) {
          enviarObjetoUDP(apdu, dono.getIp(), dono.getPorta());
        } // fim do if
      } // fim do if
    } // fim do synchronized
  } // fim do encaminharConfirmacao

}