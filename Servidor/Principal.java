/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 05/11/2026
* Nome.............: Principal.java
* Funcao...........: Ponto de entrada do servidor com suporte a ID e particionamento (Sharding)
*******************************************************************/

import Model.Servidor;
import java.net.InetAddress;

public class Principal {

  public static void main(String[] args) {
    Integer idForcado = null;

    String ipServidor = Servidor.obterIpLocalServidor();

    if (args.length > 0) {
      try {
        int parsed = Integer.parseInt(args[0]);
        if (parsed > 0) {
          idForcado = parsed;
          int conjunto = Servidor.calcularConjuntoPorId(idForcado);
          String faixa = Servidor.obterFaixaPorConjunto(conjunto);
          int portaTCP = Servidor.PORTA_TCP_BASE + (conjunto - 1);
          int portaUDP = Servidor.PORTA_UDP_BASE + (conjunto - 1);

          System.out.println("===================================================");
          System.out.println("[SERVIDOR] Iniciando no IP: " + ipServidor);
          System.out.println("[CONFIG] ID do Servidor informado: " + idForcado);
          System.out.println("[SHARDING] Conjunto atribuido (ID % 3): Conjunto " + conjunto + " " + faixa);
          System.out.println("[PORTAS] Atendimento de Clientes -> TCP: " + portaTCP + " | UDP: " + portaUDP);
          System.out.println("===================================================");
        } else {
          System.err.println("[AVISO] O ID deve ser um numero positivo (> 0). Usando auto-atribuicao dinamica.");
        } // fim do if
      } catch (NumberFormatException e) {
        System.err.println("[AVISO] Parametro invalido ('" + args[0] + "'). Usando auto-atribuicao dinamica.");
      } // fim do try-catch
    } else {
      System.out.println("===================================================");
      System.out.println("[SERVIDOR] Iniciando no IP: " + ipServidor);
      System.out.println("[CONFIG] Nenhum ID passado via argumento (ex: java Principal <ID>).");
      System.out.println("[CONFIG] O cluster auto-atribuira o proximo ID livre e calculara o conjunto.");
      System.out.println("===================================================");
    } // fim do if

    Servidor servidor = new Servidor(idForcado);
    servidor.start();
  } // fim do main

}