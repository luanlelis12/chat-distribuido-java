/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 27/06/2026
* Ultima alteracao.: 29/06/2026
* Nome.............: Conversa.java
* Funcao...........: Classe para gerenciar conversas em grupo ou entre usuarios
*******************************************************************/
package Model;

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

  /*
   * Metodo: adicionarMensagem
   * Funcao: Adiciona uma nova mensagem ao historico da conversa
  * Parametros: balao = balao de mensagem a ser adicionado
   * Retorno: void
   */
  public void adicionarMensagem(HBox balao) {
    this.historico.add(balao);
  } // fim do metodo adicionarMensagem

  /*
   * Metodo: novaNotificacao
   * Funcao: Incrementa o contador de notificacoes
   * Parametros: nenhum
   * Retorno: void
   */
  public void novaNotificacao() {
    this.notificacoes++;
  } // fim do metodo novaNotificacao

  /*
   * Metodo: lerNotificacoes
   * Funcao: Zera o contador de notificacoes
   * Parametros: nenhum
   * Retorno: void
   */
  public void lerNotificacoes() {
    this.notificacoes = 0;
  } // fim do metodo lerNotificacoes

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

  /*
   * Metodo: setNotificacaoLabel
   * Funcao: Define o label usado para exibir notificacoes
   * Parametros: notificacaoLabel = label de notificacoes
   * Retorno: void
   */
  public void setNotificacaoLabel(Label notificacaoLabel) {
    this.notificacaoLabel = notificacaoLabel;
    atualizarTela();
  } // fim do metodo setNotificacaoLabel

  public void setNotificacaoImage(ImageView notificacaoImage) {
    this.notificacaoImage = notificacaoImage;
  }

  /*
   * Metodo: setNotificacoes
   * Funcao: Atualiza a quantidade de notificacoes e a tela
   * Parametros: notificacoes = nova quantidade de notificacoes
   * Retorno: void
   */
  public void setNotificacoes(int notificacoes) {
    this.notificacoes = notificacoes;
    atualizarTela();
  } // fim do metodo setNotificacoes

  /*
   * Metodo: atualizarTela
   * Funcao: Atualiza a exibicao das notificacoes da conversa
   * Parametros: nenhum
   * Retorno: void
   */
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
      } // fim do if
    } // fim do if
  } // fim do metodo atualizarTela

}