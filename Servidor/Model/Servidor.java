/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 18/09/2026
* Nome.............: Servidor.java
* Funcao...........: Gerenciar a memoria central compartilhada e inicializar as threads UDP e TCP
*******************************************************************/
package Model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class Servidor {
  private final int PORTA_TCP = 6789;
  private final int PORTA_UDP = 7777;

  // Memoria Global Compartilhada
  public Map<String, Usuario> usuariosOnline = new ConcurrentHashMap<>();
  public Map<String, ArrayList<Usuario>> grupos = new ConcurrentHashMap<>();
  public Semaphore mutex = new Semaphore(1);

  // Mapas de Rastreio
  public Map<String, HashSet<String>> esperadosGrupo = new ConcurrentHashMap<>();
  public Map<String, HashSet<String>> recebidosGrupo = new ConcurrentHashMap<>();
  public Map<String, HashSet<String>> lidosGrupo = new ConcurrentHashMap<>();

  /*
   * Metodo: start
   * Funcao: Inicializa as threads de escuta TCP, UDP e o Broadcast
   * Parametros: nenhum
   * Retorno: void
   */
  public void start() {
    System.out.println("------------------");
    System.out.println("SERVIDOR INICIADO");
    System.out.println("------------------");

    new ServidorTCP(PORTA_TCP, this).start();
    new ServidorUDP(PORTA_UDP, this).start();
    iniciarServidorDescoberta();

  } // fim do metodo start

  /*
   * Metodo: iniciarServidorDescoberta
   * Funcao: Escuta pacotes em broadcast na porta 8888 e responde informando o IP
   * do servidor
   * Parametros: nenhum
   * Retorno: void
   */
  private void iniciarServidorDescoberta() {
    new Thread(() -> {
      try {
        java.net.DatagramSocket socketDesc = new java.net.DatagramSocket(8888);
        System.out.println("SERVIDOR BROADCAST - Escutando na porta 8888");
        while (true) {
          byte[] buffer = new byte[1024];
          java.net.DatagramPacket pacote = new java.net.DatagramPacket(buffer, buffer.length);
          socketDesc.receive(pacote);

          String mensagem = new String(pacote.getData(), 0, pacote.getLength()).trim();
          if (mensagem.equals("SERVIDOR_IP")) {
            byte[] resposta = "IP".getBytes();
            java.net.DatagramPacket pacoteRes = new java.net.DatagramPacket(resposta, resposta.length,
                pacote.getAddress(), pacote.getPort());
            socketDesc.send(pacoteRes);
          } // fim do if
        } // fim do while
      } catch (Exception e) {
      } // fim do try-catch
    }).start();
  } // fim do metodo iniciarServidorDescoberta

}