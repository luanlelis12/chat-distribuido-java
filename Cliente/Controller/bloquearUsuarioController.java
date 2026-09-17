/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 17/09/2026
* Ultima alteracao.: 17/09/2026
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
import javafx.stage.Stage;

public class bloquearUsuarioController {

  @FXML
  private TextField nomeUsuarioField;
  
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