/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 27/06/2026
* Ultima alteracao.: 30/06/2026
* Nome.............: alertController.java
* Funcao...........: Gerencia a interface dos alerts
*******************************************************************/
package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class alertController implements Initializable {

  @FXML
  private Pane barraSuperior;
  @FXML
  private Label tituloLabel;
  @FXML
  private Label mensagemLabel;

  private double xOffset = 0;
  private double yOffset = 0;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    System.out.println("O Controller foi carregado corretamente!");

    if (barraSuperior != null) {
      barraSuperior.setOnMousePressed(event -> {
        xOffset = event.getSceneX();
        yOffset = event.getSceneY();
      });

      barraSuperior.setOnMouseDragged(event -> {
        Stage janela = (Stage) barraSuperior.getScene().getWindow();

        janela.setX(event.getScreenX() - xOffset);
        janela.setY(event.getScreenY() - yOffset);
      });
    } // fim do if
  } // fim do metodo initialize

  /*
   * Metodo: setDetalhes
   * Funcao: Recebe os textos personalizados e insere na tela
   * Parametros: titulo = titulo do alert, mensagem = mensagem do alert
   * Retorno: void
   */
  public void setDetalhes(String titulo, String mensagem) {
    tituloLabel.setText(titulo);
    mensagemLabel.setText(mensagem);
  } // fim do metodo setDetalhes

  /*
   * Metodo: fecharAlerta
   * Funcao: Fecha a janela atual (O pop-up do alerta)
   * Parametros: event = evento que inicializou a funcao
   * Retorno: void
   */
  @FXML
  public void fecharAlerta(ActionEvent event) {
    // Descobre qual e a janela (Stage) que contem o botao clicado
    Stage janelaAlerta = (Stage) ((Node) event.getSource()).getScene().getWindow();

    // Fecha a janela
    janelaAlerta.close();
  } // fim do metodo fecharAlerta

}