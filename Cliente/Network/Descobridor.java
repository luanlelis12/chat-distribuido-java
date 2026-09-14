package Network;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public class Descobridor {

  /*
   * Metodo: descobrirServidor
   * Funcao: Envia broadcast UDP varrendo todas as interfaces de rede locais
   * ativas
   * Parametros: nenhum
   * Retorno: String com o IP encontrado ou null
   */
  public static String descobrirServidor() {
    List<InetAddress> broadcasts = coletarBroadcasts();
    try {
      broadcasts.add(InetAddress.getByName("255.255.255.255"));
    } catch (Exception e) {
    } // fim do try-catch

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(3000); // 3 segundos de limite

      byte[] pedido = "SERVIDOR_IP".getBytes();
      byte[] buffer = new byte[256];

      // Dispara o grito para todos os endereços de broadcast encontrados
      for (InetAddress broadcastAddr : broadcasts) {
        try {
          System.out.println("CLIENTE - Tentando broadcast em: " + broadcastAddr.getHostAddress());
          DatagramPacket pacoteGrito = new DatagramPacket(pedido, pedido.length, broadcastAddr, 8888);
          socket.send(pacoteGrito);
        } catch (Exception e) {
        } // fim do try-catch
      } // fim do for

      DatagramPacket pacoteResposta = new DatagramPacket(buffer, buffer.length);
      try {
        socket.receive(pacoteResposta);
        String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
        if (resposta.equals("IP")) {
          String ip = pacoteResposta.getAddress().getHostAddress();
          System.out.println("CLIENTE - Servidor encontrado no IP: " + ip);
          return ip;
        } // fim do if
      } catch (SocketTimeoutException e) {
        System.out.println("CLIENTE - Nenhum servidor respondeu ao broadcast.");
      } // fim do try-catch
    } catch (Exception e) {
      System.out.println("CLIENTE - Erro na rede: " + e.getMessage());
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
    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(1500);
      byte[] pedido = "SERVIDOR_IP".getBytes();
      DatagramPacket pacoteGrito = new DatagramPacket(pedido, pedido.length, InetAddress.getByName("127.0.0.1"), 8888);
      socket.send(pacoteGrito);

      byte[] buffer = new byte[256];
      DatagramPacket pacoteResposta = new DatagramPacket(buffer, buffer.length);
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
    } // fim do try-catch
    return lista;
  } // fim do metodo coletarBroadCasts
}