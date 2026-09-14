/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 27/06/2026
* Ultima alteracao.: 29/06/2026
* Nome.............: Conversa.java
* Funcao...........: Classe para gerenciar conversas em grupo ou entre usuarios
*******************************************************************/
package model;

import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import java.util.ArrayList;

public class Conversa {
  private String nome;
  private String tipo; // "grupo" ou "priv"
  private ArrayList<HBox> historico;
  private int notificacoes;
  private Label notificacaoLabel;
  private ImageView notificacaoImage;

  public Conversa(String nome, String tipo) {
    this.nome = nome;
    this.tipo = tipo;
    this.historico = new ArrayList<>();
    this.notificacoes = 0;
  }

  public void adicionarMensagem(HBox balao) {
    this.historico.add(balao);
  }

  public void novaNotificacao() {
    this.notificacoes++;
  }

  public void lerNotificacoes() {
    this.notificacoes = 0;
  }

  public ArrayList<HBox> getHistorico() {
    return historico;
  }

  public int getNotificacoes() {
    return notificacoes;
  }

  public String getNome() {
    return nome;
  }

  public String getTipo() {
    return tipo;
  }

  public void setNome(String nome) {
    this.nome = nome;
  }

  public void setTipo(String tipo) {
    this.tipo = tipo;
  }

  public void setHistorico(ArrayList<HBox> historico) {
    this.historico = historico;
  }

  public void setNotificacaoLabel(Label notificacaoLabel) {
    this.notificacaoLabel = notificacaoLabel;
    atualizarTela();
  }

  public void setNotificacaoImage(ImageView notificacaoImage) {
    this.notificacaoImage = notificacaoImage;
  }

  public void setNotificacoes(int notificacoes) {
    this.notificacoes = notificacoes;
    atualizarTela();
  }

  private void atualizarTela() {
    if (this.notificacaoLabel != null) {
      if (this.notificacoes > 0) {
        this.notificacaoLabel.setText(String.valueOf(this.notificacoes));
        this.notificacaoLabel.setVisible(true);
        this.notificacaoImage.setVisible(true);
      } else {
        this.notificacaoLabel.setVisible(false);
        this.notificacaoLabel.setText("");
        this.notificacaoImage.setVisible(false);
      }
    }
  }

}