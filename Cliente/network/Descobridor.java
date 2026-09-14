/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 19/09/2026
* Ultima alteracao.: 14/09/2026
* Nome.............: Descobridor.java
* Funcao...........: Gerencia o sistema de broadcast
*******************************************************************/
package network;

public class Descobridor {

  /*
   * Metodo: descobrirServidor
   * Funcao: Envia um pacote UDP em broadcast para localizar o servidor na rede,
   * com fallback para localhost
   * Parametros: nenhum
   * Retorno: String com o IP encontrado ou null
   */
  public static String descobrirServidor() {
    System.out.println("CLIENTE - Procurando servidor na rede local...");
    try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(3000);

      byte[] dados = "SERVIDOR_IP".getBytes();
      java.net.DatagramPacket pacoteEnvio = new java.net.DatagramPacket(dados, dados.length,
          java.net.InetAddress.getByName("255.255.255.255"), 8888);
      socket.send(pacoteEnvio);

      byte[] bufferResposta = new byte[1024];
      java.net.DatagramPacket pacoteResposta = new java.net.DatagramPacket(bufferResposta, bufferResposta.length);
      socket.receive(pacoteResposta);

      String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
      if (resposta.equals("IP")) {
        String ipEncontrado = pacoteResposta.getAddress().getHostAddress();
        System.out.println("CLIENTE - Servidor encontrado no IP: " + ipEncontrado);
        return ipEncontrado;
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - Servidor nao encontrado no broadcast global (Timeout).");
    } // fim do try-catch

    return tentarLocalhostDireto();
  } // fim do metodo descobrirServidor

  /*
   * Metodo: tentarLocalhostDireto
   * Funcao: Tenta descobrir o servidor enviando o datagrama diretamente para
   * 127.0.0.1
   * Parametros: nenhum
   * Retorno: String com o IP 127.0.0.1 ou null
   */
  private static String tentarLocalhostDireto() {
    System.out.println("CLIENTE - Tentando conexao direta em localhost...");
    try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
      socket.setSoTimeout(1500);
      byte[] pedido = "SERVIDOR_IP".getBytes();
      java.net.DatagramPacket pacoteGrito = new java.net.DatagramPacket(
          pedido, pedido.length,
          java.net.InetAddress.getByName("127.0.0.1"), 8888);
      socket.send(pacoteGrito);

      byte[] buffer = new byte[256];
      java.net.DatagramPacket pacoteResposta = new java.net.DatagramPacket(buffer, buffer.length);
      socket.receive(pacoteResposta);

      String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
      if (resposta.equals("IP")) {
        System.out.println("CLIENTE - Servidor encontrado em localhost.");
        return "127.0.0.1";
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - Servidor nao encontrado em localhost.");
    } // fim do try-catch
    return null;
  } // fim do metodo tentarLocalhostDireto
}
