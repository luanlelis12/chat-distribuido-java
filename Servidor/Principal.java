/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 14/09/2026
* Nome.............: Principal.java
* Funcao...........: 
*******************************************************************/

import Model.Servidor;

public class Principal {

  public static void main(String[] args) {
    Servidor servidor = new Servidor();
    servidor.start();
  }

}