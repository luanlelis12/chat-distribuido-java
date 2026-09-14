/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 27/06/2026
* Ultima alteracao.: 27/06/2026
* Nome.............: entrarConversaController.java
* Funcao...........: Gerencia a interface e o comunica para o clienteController sobre a criacao de grupo ou criar uma conversa privada com um usuario
*******************************************************************/
package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;

public class entrarConversaController {

  @FXML
  private TextField nomeConversaField;
  @FXML
  private ToggleGroup tipoDeConversa;

  private clienteController controladorPai;

  /*
   * Metodo: confirmarEntrada
   * Funcao: Transmite para o clienteController se o usuario quer criar um
   * grupo ou comecar uma conversa no privado
   * Parametros: event = evento que inicializou a funcao
   * Retorno: void
   */
  @FXML
  public void confirmarEntrada(ActionEvent event) {
    String nomeDigitado = nomeConversaField.getText();

    RadioButton selecionado = (RadioButton) tipoDeConversa.getSelectedToggle();

    if (selecionado != null && selecionado.getText().equalsIgnoreCase("Grupo")) {
      controladorPai.entrarGrupo(nomeDigitado);
    } else {
      controladorPai.criarConversaPrivada(nomeDigitado);
    } // fim do if-else
    fecharTela(event);
  }

  /*
   * Metodo: fecharTela
   * Funcao: Fecha a tela
   * Parametros: event = evento que inicializou a funcao
   * Retorno: void
   */
  public void fecharTela(ActionEvent event) {
    Stage janela = (Stage) ((Node) event.getSource()).getScene().getWindow();
    janela.close();
  } // fim do metodo fecharTela

  public void setControladorPai(clienteController controladorPai) {
    this.controladorPai = controladorPai;
  }

}