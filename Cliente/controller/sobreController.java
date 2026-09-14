/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 27/06/2026
* Ultima alteracao.: 27/06/2026
* Nome.............: sobreController.java
* Funcao...........: Gerencia a interface sobre do trabalho
*******************************************************************/
package controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class sobreController implements Initializable {

  @FXML
  private Pane barraSuperior;
  
  private double xOffset = 0;
  private double yOffset = 0;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    System.out.println("O Controller foi carregado corretamente!");

    // posibilita o usuario mexer a interface pela barra superior do programa
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
   * Metodo: fecharSobre
   * Funcao: Fecha a janela de informacoes do trabalho
   */
  @FXML
  public void fecharSobre(ActionEvent event) {
    Stage janela = (Stage) ((Node) event.getSource()).getScene().getWindow();
    janela.close();
  } // fim do metodo fecharSobre

}