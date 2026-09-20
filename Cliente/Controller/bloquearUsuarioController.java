/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 17/09/2026
* Ultima alteracao.: 20/09/2026
* Nome.............: bloquearUsuarioController.java
* Funcao...........: Gerencia a interface de bloquear usuario
*******************************************************************/
package Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class bloquearUsuarioController {

  @FXML
  private Pane barraSuperior;

  @FXML
  private TextField nomeUsuarioField;

  private double xOffset = 0;
  private double yOffset = 0;

  @FXML
  public void initialize() {
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
    }
  }
  
  @FXML
  private ToggleGroup tipoDeAcao; // Grupo dos botoes Bloquear/Desbloquear

  private clienteController controladorPai;

  @FXML
  public void confirmarAcao(ActionEvent event) {
    String nomeDigitado = nomeUsuarioField.getText();
    
    RadioButton selecionado = (RadioButton) tipoDeAcao.getSelectedToggle();
    boolean isBloquear = selecionado != null && selecionado.getText().equalsIgnoreCase("BLOQUEAR");
    
    controladorPai.processarBloqueioUsuario(nomeDigitado, isBloquear); 
    
    fecharTela(event);
  } // fim do metodo confirmarAcao

  public void fecharTela(ActionEvent event) {
    Stage janela = (Stage) ((Node) event.getSource()).getScene().getWindow();
    janela.close();
  } // fim do metodo fecharTela

  public void setControladorPai(clienteController controladorPai) {
    this.controladorPai = controladorPai;
  }
}