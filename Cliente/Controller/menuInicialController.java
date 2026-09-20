/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 15/06/2026
* Ultima alteracao.: 20/09/2026
* Nome.............: menuInicialController.java
* Funcao...........: Gerencia a interface do menuInicial e comunica com o cliente.java para criar o cliente
*******************************************************************/
package Controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import Controller.clienteController;
import Network.Descobridor;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class menuInicialController implements Initializable {

  @FXML
  private TextField nomeTextField;
  @FXML
  private Pane barraSuperior;

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
   * Metodo: criarCliente
   * Funcao: inicializa o cliente
   * Parametros: event = evento que iniciou o metodo
   * Retorno: void
   */
  public void criarCliente(ActionEvent event) {
    String nomeCliente = nomeTextField.getText();

    if (nomeCliente == null || nomeCliente.trim().isEmpty()) { // verifica se o nome eh vazio
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/View/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();

        controladorDoAlerta.setDetalhes("Nome Obrigatorio", "Por favor, digite um nome de usuario valido antes de tentar conectar.");

        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);

        janelaAlerta.show();
      } catch (IOException e) {
        System.out.println("CLIENTE - Erro: Nao foi possivel carregar o alerta!");
        e.printStackTrace();
      } // fim do try-catch
      return;
    } // fim do if

    // Faz um broadcast para encontrar o servidor
    String ipServidor = Descobridor.descobrirServidor();

    if (ipServidor == null) { // se o servidor estiver fora de ar emitir alert
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/View/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();
        controladorDoAlerta.setDetalhes("Servidor Nao Encontrado", "Nao foi possivel localizar o servidor na rede local. Verifique se ele esta ligado e tente novamente.");
        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);
        janelaAlerta.show();
      } catch (IOException e) {
        System.out.println("CLIENTE - Erro ao abrir alerta!");
        e.printStackTrace();
      } // fim do try-catch
      return;
    } // fim do if

    // Se achou, tenta conectar enviando o IP que descobriu
    boolean sucesso = clienteController.criarCliente(nomeCliente, ipServidor);

    if (!sucesso) {
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/View/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();
        controladorDoAlerta.setDetalhes("Nome Indisponivel", "Este nome de usuario ja esta conectado no chat. Por favor, escolha um nome diferente para entrar.");

        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);
        janelaAlerta.show();
      } catch (IOException e) {
        System.out.println("CLIENTE - Erro ao abrir alerta!");
        e.printStackTrace();
      } // fim do try-catch
      return;
    } // fim do if

    System.out.println("CLIENTE - criando usuario " + nomeCliente + ".");

    try {
      Parent novaRaiz = FXMLLoader.load(getClass().getResource("/View/chat.fxml"));
      Scene novaCena = new Scene(novaRaiz);

      Stage primaryStage = (Stage) ((Node) event.getSource()).getScene().getWindow();

      primaryStage.setScene(novaCena);
      primaryStage.show();
    } catch (IOException e) {
      System.out.println("CLIENTE - Erro: Nao foi possivel trocar de tela");
      e.printStackTrace();
    } // fim do try-catch

  } // fim do metodo criarCliente

  /*
   * Metodo: fecharAplicacao
   * Funcao: Faz logout e encerra a aplicacao
   * Parametros: nenhum
   * Retorno: void
   */
  public void fecharAplicacao() {
    clienteController.encerrarAplicacao();
  } // fim do metodo fecharAplicacao

  /*
   * Metodo: abrirSobre
   * Funcao: abrir o sobre do aplicativo
   * Parametros: event = evento que inicializou a funcao
   * Retorno: void
   */
  public void abrirSobre(ActionEvent event) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/View/sobre.fxml"));
      Parent root = loader.load();

      Stage janelaSobre = new Stage();
      janelaSobre.setScene(new Scene(root));

      janelaSobre.initStyle(StageStyle.UNDECORATED);

      janelaSobre.initModality(Modality.APPLICATION_MODAL);

      janelaSobre.show();
    } catch (IOException e) {
      System.out.println("CLIENTE - Erro: Nao foi possivel carregar a tela Sobre: ");
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo abrirSobre

}