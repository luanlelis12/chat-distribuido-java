/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 
* Nome.............: Usuario.java
* Funcao...........: 
*******************************************************************/

package model;

import java.net.InetAddress;

public class Usuario {
  InetAddress ip;
  String nome;
  int porta;

  public Usuario(InetAddress ip, String nome, int porta) {
    this.ip = ip;
    this.nome = nome;
    this.porta = porta;
  }

  public InetAddress getIp() {
    return ip;
  }

  public void setIp(InetAddress ip) {
    this.ip = ip;
  }

  public String getNome() {
    return nome;
  }

  public void setNome(String nome) {
    this.nome = nome;
  }

  public int getPorta() {
    return porta;
  }

  public void setPorta(int porta) {
    this.porta = porta;
  }

}