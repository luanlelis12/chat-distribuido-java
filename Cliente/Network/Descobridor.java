/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 18/09/2026
* Nome.............: Descobridor.java
* Funcao...........: Varrer todas as interfaces de rede do computador para descobrir o IP do servidor
*******************************************************************/
package Network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

public class Descobridor {

  private static volatile int ultimaPortaTCPDescoberta = 6789;
  private static volatile int ultimaPortaUDPDescoberta = 7777;

  public static int getUltimaPortaTCPDescoberta() {
    return ultimaPortaTCPDescoberta;
  }

  public static int getUltimaPortaUDPDescoberta() {
    return ultimaPortaUDPDescoberta;
  }

  private static void extrairPortasDaResposta(String resposta) {
    ultimaPortaTCPDescoberta = 6789;
    ultimaPortaUDPDescoberta = 7777;
    if (resposta != null && resposta.startsWith("IP:")) {
      String[] partes = resposta.split(":");
      if (partes.length >= 3) {
        try {
          ultimaPortaTCPDescoberta = Integer.parseInt(partes[1].trim());
          ultimaPortaUDPDescoberta = Integer.parseInt(partes[2].trim());
        } catch (NumberFormatException ignored) {
          ultimaPortaTCPDescoberta = 6789;
          ultimaPortaUDPDescoberta = 7777;
        }
      }
    }
  }

  /*
   * Metodo: descobrirServidor
   * Funcao: Envia broadcast UDP varrendo todas as interfaces de rede locais
   * ativas na porta 8888 (sem abrir conexoes TCP vazias nos servidores)
   * Parametros: nenhum
   * Retorno: String com o IP encontrado ou null
   */
  public static String descobrirServidor() {
    List<InetAddress> broadcasts = coletarBroadcasts();
    try {
      broadcasts.add(InetAddress.getByName("255.255.255.255"));
    } catch (Exception e) {
      System.err.println("CLIENTE - AVISO: Nao foi possivel adicionar broadcast padrao: " + e.getMessage());
    } // fim do try-catch

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(2000);

      byte[] pedido = "SERVIDOR_IP".getBytes();
      byte[] buffer = new byte[256];

      // Envia o pedido para todos os enderecos de broadcast encontrados
      for (InetAddress broadcastAddr : broadcasts) {
        try {
          System.out.println("CLIENTE - Tentando broadcast em: " + broadcastAddr.getHostAddress());
          DatagramPacket pacoteGrito = new DatagramPacket(pedido, pedido.length, broadcastAddr, 8888);
          socket.send(pacoteGrito);
        } catch (Exception e) {
          System.err.println("CLIENTE - AVISO: broadcast falhou para " + broadcastAddr.getHostAddress() + ": " + e.getMessage());
        } // fim do try-catch
      } // fim do for

      DatagramPacket pacoteResposta = new DatagramPacket(buffer, buffer.length);
      socket.receive(pacoteResposta);

      String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
      if (resposta.equals("IP") || resposta.startsWith("IP:")) {
        extrairPortasDaResposta(resposta);
        String ip = pacoteResposta.getAddress().getHostAddress();
        System.out.println("CLIENTE - Servidor encontrado com sucesso no IP: " + ip
            + " (TCP:" + ultimaPortaTCPDescoberta + " / UDP:" + ultimaPortaUDPDescoberta + ")");
        return ip;
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - Erro na rede ou tempo esgotado (Servidor Desligado?): " + e.getMessage());
    } // fim do try-catch
    return tentarLocalhostDireto();
  } // fim do metodo descobrirServidor

  /*
   * Metodo: tentarLocalhostDireto
   * Funcao: Tenta descobrir o servidor enviando o datagrama UDP diretamente para
   * 127.0.0.1 na porta 8888, util quando nao ha interfaces de rede ativas
   * Parametros: nenhum
   * Retorno: String com o IP 127.0.0.1 ou null
   */
  private static String tentarLocalhostDireto() {
    System.out.println("CLIENTE - Tentando conexao direta em localhost...");
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(2000);
      byte[] pedido = "SERVIDOR_IP".getBytes();
      DatagramPacket pacoteGrito = new DatagramPacket(pedido, pedido.length, InetAddress.getByName("127.0.0.1"), 8888);
      socket.send(pacoteGrito);

      byte[] buffer = new byte[256];
      DatagramPacket pacoteResposta = new DatagramPacket(buffer, buffer.length);
      socket.receive(pacoteResposta);

      String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
      if (resposta.equals("IP") || resposta.startsWith("IP:")) {
        extrairPortasDaResposta(resposta);
        System.out.println("CLIENTE - Servidor encontrado em localhost.");
        return "127.0.0.1";
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - Servidor nao encontrado em localhost.");
    } // fim do try-catch
    return null;
  } // fim do metodo tentarLocalhostDireto

  /*
   * Metodo: coletarBroadcasts
   * Funcao: Vasculha a placa de rede do computador para encontrar o IP de
   * broadcast correto (ex: 192.168.1.255)
   * Parametros: nenhum
   * Retorno: Lista de InetAddress
   */
  private static List<InetAddress> coletarBroadcasts() {
    List<InetAddress> lista = new ArrayList<>();
    try {
      java.util.Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
      while (interfaces.hasMoreElements()) {
        NetworkInterface ni = interfaces.nextElement();
        if (!ni.isUp() || ni.isLoopback() || ni.isVirtual())
          continue;
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
          InetAddress broadcast = ia.getBroadcast();
          if (broadcast != null) {
            lista.add(broadcast);
          } // fim do if
        } // fim do for
      } // fim do while
    } catch (Exception e) {
      System.err.println("CLIENTE - AVISO: Falha ao listar interfaces de rede: " + e.getMessage());
    } // fim do try-catch
    return lista;
  } // fim do metodo coletarBroadCasts
}