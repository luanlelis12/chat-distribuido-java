/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 14/09/2026
* Nome.............: Principal.java
* Funcao...........: 
*******************************************************************/

import Controller.alertController;
import Controller.clienteController;
import Controller.entrarConversaController;
import Controller.listarConversasController;
import Controller.menuInicialController;
import Controller.sobreController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Parent;
import javafx.scene.Scene;

public class Principal extends Application {

  @Override
  public void start(Stage primaryStage) throws Exception {

    // Carrega o arquivo FXML da tela principal
    FXMLLoader loader = new FXMLLoader(getClass().getResource("view/menuInicial.fxml"));
    Parent root = loader.load();

    // Configura a cena
    primaryStage.initStyle(StageStyle.UNDECORATED);
    primaryStage.setTitle("cliente");
    primaryStage.setScene(new Scene(root));

    primaryStage.setOnCloseRequest(evento -> {
      Platform.exit();
      System.exit(0);
    });

    primaryStage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }

}
