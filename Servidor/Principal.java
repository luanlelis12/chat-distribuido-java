/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 20/09/2026
* Nome.............: Principal.java
* Funcao...........: Classe principal do servidor, responsavel por iniciar o servidor.
*******************************************************************/

import Model.Servidor;
import java.net.InetAddress;

public class Principal {

  public static void main(String[] args) {
    Servidor servidor = new Servidor();
    try {
      System.out.println("IP DO SERVIDOR: " + InetAddress.getLocalHost().getHostAddress());
    } catch (Exception e) {
      System.out.println("Nao foi possivel identificar o IP do servidor.");
    }
    servidor.start();
  }

}