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
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Descobridor {

  /*
   * Metodo: descobrirServidor
   * Funcao: Envia broadcast UDP varrendo todas as interfaces de rede locais
   * ativas (padrao: procura qualquer lider nas portas 6789, 6790 ou 6791)
   * Parametros: nenhum
   * Retorno: String com o IP encontrado ou null
   */
  public static String descobrirServidor() {
    return descobrirServidor(-1);
  } // fim do metodo descobrirServidor

  /*
   * Metodo: descobrirServidor (com porta TCP alvo do conjunto)
   * Funcao: Descobre o IP do servidor Lider que responde pelo conjunto desejado
   * (portaTCPAlvo = 6789 para [A..I], 6790 para [J..R], 6791 para [S..Z]).
   * Coleta multiplas respostas ao broadcast na porta 8888 e verifica qual IP
   * possui a porta TCP do conjunto aberta (suportando localhost e multiplas maquinas).
   */
  public static String descobrirServidor(int portaTCPAlvo) {
    // 1. Verificacao rapida em localhost (127.0.0.1) para a porta especifica do conjunto
    if (portaTCPAlvo > 0 && testarPortaTCP("127.0.0.1", portaTCPAlvo, 250)) {
      System.out.println("CLIENTE - Lider da porta TCP " + portaTCPAlvo + " encontrado em localhost (127.0.0.1).");
      return "127.0.0.1";
    } // fim do if

    List<InetAddress> broadcasts = coletarBroadcasts();
    try {
      broadcasts.add(InetAddress.getByName("255.255.255.255"));
    } catch (Exception e) {
      System.err.println("CLIENTE - AVISO: Nao foi possivel adicionar broadcast padrao: " + e.getMessage());
    } // fim do try-catch

    String primeiroIpEncontrado = null;
    Set<String> ipsTestados = new HashSet<>();

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(1200);

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

      long limite = System.currentTimeMillis() + 2000;
      while (System.currentTimeMillis() < limite) {
        DatagramPacket pacoteResposta = new DatagramPacket(buffer, buffer.length);
        try {
          socket.receive(pacoteResposta);
          String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
          if (resposta.equals("IP")) {
            String ip = pacoteResposta.getAddress().getHostAddress();
            if (primeiroIpEncontrado == null) {
              primeiroIpEncontrado = ip;
            } // fim do if

            if (portaTCPAlvo <= 0) {
              System.out.println("CLIENTE - Servidor encontrado no IP: " + ip);
              return ip;
            } // fim do if

            if (!ipsTestados.contains(ip)) {
              ipsTestados.add(ip);
              if (testarPortaTCP(ip, portaTCPAlvo, 400)) {
                System.out.println("CLIENTE - Lider da porta TCP " + portaTCPAlvo + " encontrado no IP: " + ip);
                return ip;
              } // fim do if
            } // fim do if
          } // fim do if
        } catch (SocketTimeoutException e) {
          break;
        } // fim do try-catch
      } // fim do while
    } catch (Exception e) {
      System.out.println("CLIENTE - Erro na rede: " + e.getMessage());
    } // fim do try-catch

    String ipLocalhost = tentarLocalhostDireto(portaTCPAlvo);
    if (ipLocalhost != null) {
      return ipLocalhost;
    } // fim do if

    if (primeiroIpEncontrado != null) {
      System.out.println("CLIENTE - Usando IP descoberto no cluster: " + primeiroIpEncontrado);
      return primeiroIpEncontrado;
    } // fim do if

    return null;
  } // fim do metodo descobrirServidor

  /*
   * Metodo: testarPortaTCP
   * Funcao: Verifica rapidamente se um endereço IP esta escutando na porta TCP informada
   */
  public static boolean testarPortaTCP(String ip, int portaTCP, int timeoutMs) {
    try (Socket s = new Socket()) {
      s.connect(new InetSocketAddress(ip, portaTCP), timeoutMs);
      return true;
    } catch (Exception e) {
      return false;
    } // fim do try-catch
  } // fim do metodo testarPortaTCP

  /*
   * Metodo: tentarLocalhostDireto
   * Funcao: Tenta descobrir o servidor enviando o datagrama diretamente para
   * 127.0.0.1 ou verificando as portas TCP locais (6789, 6790, 6791)
   * Parametros: portaTCPAlvo = porta TCP desejada (ou -1 para qualquer uma)
   * Retorno: String com o IP 127.0.0.1 ou null
   */
  private static String tentarLocalhostDireto(int portaTCPAlvo) {
    System.out.println("CLIENTE - Tentando conexao direta em localhost...");
    if (portaTCPAlvo > 0 && testarPortaTCP("127.0.0.1", portaTCPAlvo, 300)) {
      System.out.println("CLIENTE - Servidor encontrado em localhost na porta TCP " + portaTCPAlvo + ".");
      return "127.0.0.1";
    } // fim do if

    int[] portasPadrao = { 6789, 6790, 6791 };
    for (int porta : portasPadrao) {
      if (testarPortaTCP("127.0.0.1", porta, 250)) {
        System.out.println("CLIENTE - Servidor encontrado em localhost na porta TCP " + porta + ".");
        return "127.0.0.1";
      } // fim do if
    } // fim do for

    try (DatagramSocket socket = new DatagramSocket()) {
      socket.setSoTimeout(1000);
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
      System.err.println("CLIENTE - AVISO: Falha ao listar interfaces de rede: " + e.getMessage());
    } // fim do try-catch
    return lista;
  } // fim do metodo coletarBroadCasts
}