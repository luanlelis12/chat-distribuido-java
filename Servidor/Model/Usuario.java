/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 15/09/2026
* Nome.............: Usuario.java
* Funcao...........: 
*******************************************************************/

package Model;

import java.net.InetAddress;
import java.util.HashSet;

public class Usuario {
  InetAddress ip;
  String nome;
  int porta;
  HashSet<String> bloqueados;

  public HashSet<String> getBloqueados() {
    return bloqueados;
  }

  public void addBloqueados(String usuarioBloqueado) {
    this.bloqueados.add(usuarioBloqueado);
  }

  public Usuario(InetAddress ip, String nome, int porta) {
    this.ip = ip;
    this.nome = nome;
    this.porta = porta;
    this.bloqueados = new HashSet<>();
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