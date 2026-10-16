/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 13/10/2026
* Nome.............: Principal.java
* Funcao...........: Ponto de entrada do servidor com suporte a ID forçado ou auto-descoberta
*******************************************************************/

import Model.Servidor;
import java.net.InetAddress;

public class Principal {

  public static void main(String[] args) {
    Integer idForcado = null;

    if (args.length > 0) {
      try {
        int parsed = Integer.parseInt(args[0]);
        if (parsed > 0) {
          idForcado = parsed;
          System.out.println("[CONFIG] ID informado via parametro: " + idForcado);
        } else {
          System.err.println("[AVISO] O ID deve ser um numero positivo (> 0). Usando auto-atribuicao dinamica.");
        }
      } catch (NumberFormatException e) {
        System.err.println("[AVISO] Parametro invalido ('" + args[0] + "'). Usando auto-atribuicao dinamica.");
      }
    } else {
      System.out.println("[CONFIG] Nenhum ID passado. O cluster auto-atribuira o proximo ID livre.");
    }

    try {
      System.out.println("IP LOCAL DO SERVIDOR: " + InetAddress.getLocalHost().getHostAddress());
    } catch (Exception e) {
      System.out.println("Nao foi possivel identificar o IP do servidor.");
    }

    Servidor servidor = new Servidor(idForcado);
    servidor.start();
  }

}