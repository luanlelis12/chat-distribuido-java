/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 27/06/2026
* Ultima alteracao.: 18/09/2026
* Nome.............: alertController.java
* Funcao...........: Gerencia a interface dos alerts
*******************************************************************/
package Controller;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class alertController implements Initializable {

  @FXML
  private Pane barraSuperior;
  @FXML
  private Label tituloLabel;
  @FXML
  private Label mensagemLabel;
  @FXML 
  private ImageView imagem;
  @FXML
  private TextField inputTextField;

  private boolean confirmado = false;
  private String valorConfirmado = null;

  private double xOffset = 0;
  private double yOffset = 0;

  /*
   * Metodo: initialize
   * Funcao: Configura o arraste da janela pela barra superior
   * Parametros: location = localizacao do FXML, resources = recursos do FXML
   * Retorno: void
   */
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
    if (inputTextField != null) {
      inputTextField.setVisible(false);
    }
  } // fim do metodo setDetalhes

  /*
   * Metodo: habilitarInput
   * Funcao: Torna visivel o campo de texto no alerta para entrada manual (ex: IP do servidor)
   * Parametros: textoPrompt = dica exibida dentro do TextField
   * Retorno: void
   */
  public void habilitarInput(String textoPrompt) {
    if (inputTextField != null) {
      inputTextField.setPromptText(textoPrompt);
      inputTextField.setVisible(true);
      inputTextField.requestFocus();
    }
  } // fim do metodo habilitarInput

  /*
   * Metodo: confirmarAlerta
   * Funcao: Registra o valor digitado (caso seja um alerta de input) e fecha o pop-up pelo botao OK
   * Parametros: event = evento que inicializou a funcao
   * Retorno: void
   */
  @FXML
  public void confirmarAlerta(ActionEvent event) {
    confirmado = true;
    if (inputTextField != null && inputTextField.isVisible()) {
      valorConfirmado = inputTextField.getText() != null ? inputTextField.getText().trim() : "";
    }
    Stage janelaAlerta = (Stage) ((Node) event.getSource()).getScene().getWindow();
    janelaAlerta.close();
  } // fim do metodo confirmarAlerta

  /*
   * Metodo: fecharAlerta
   * Funcao: Fecha a janela atual pelo botao X (cancelando eventual input)
   * Parametros: event = evento que inicializou a funcao
   * Retorno: void
   */
  @FXML
  public void fecharAlerta(ActionEvent event) {
    confirmado = false;
    valorConfirmado = null;
    // Descobre qual e a janela (Stage) que contem o botao clicado
    Stage janelaAlerta = (Stage) ((Node) event.getSource()).getScene().getWindow();

    // Fecha a janela
    janelaAlerta.close();
  } // fim do metodo fecharAlerta

  /*
   * Metodo: getValorConfirmado
   * Funcao: Retorna o texto digitado pelo usuario caso ele tenha confirmado no botao OK
   * Parametros: nenhum
   * Retorno: String com o valor digitado ou null se cancelado
   */
  public String getValorConfirmado() {
    return confirmado ? valorConfirmado : null;
  } // fim do metodo getValorConfirmado

  /*
   * Metodo: setDisableImage
   * Funcao: Desabilita a imagem do alert
   * Parametros: nenhum
   * Retorno: void
   */
  public void setDisableImage() {
    imagem.setVisible(false);
  } // fim do metodo disableImage
}