/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 15/06/2026
* Ultima alteracao.: 1/07/2026
* Nome.............: menuInicialController.java
* Funcao...........: Gerencia a interface do menuInicial e comunica com o cliente.java para criar o cliente
*******************************************************************/
package controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
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
import util.processadorTexto;

public class menuInicialController implements Initializable {

  @FXML
  private TextField nomeTextField;
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
  }

  /*
   * Metodo: criarCliente
   * Funcao: inicializa o cliente
   * Parametros: event = evento que iniciou o metodo
   * Retorno: void
   */
  public void criarCliente(ActionEvent event) {
    String nomeCliente = nomeTextField.getText();
    nomeCliente = processadorTexto.inserirFlagEscape(nomeCliente);

    if (nomeCliente == null || nomeCliente.trim().isEmpty()) { // verifica se o nome eh vazio
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
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
    String ipServidor = descobrirServidor();

    if (ipServidor == null) { // se o servidor estiver fora de ar emitir alert
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
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
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
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
      Parent novaRaiz = FXMLLoader.load(getClass().getResource("/view/chat.fxml"));
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
   * Metodo: fecharTela
   * Funcao: Fecha a tela
   * Parametros: 
   * Retorno: void
   */
  public void fecharAplicacao() {
    System.out.println("CLIENTE - Fechando aplicacao.");
    Platform.exit();
    System.exit(0);
  } // fim do metodo fecharAplicacao

  /*
   * Metodo: abrirSobre
   * Funcao: abrir o sobre do aplicativo
   * Parametros: event = evento que inicializou a funcao
   * Retorno: void
   */
  public void abrirSobre(ActionEvent event) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/sobre.fxml"));
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

  /*
   * Metodo: descobrirServidor
   * Funcao: Envia um pacote na rede e devolve o IP de quem responder
   * Parametros:
   * Retorno: void
   */
  private String descobrirServidor() {
    System.out.println("CLIENTE - Procurando servidor na rede local...");
    try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
      socket.setBroadcast(true);
      socket.setSoTimeout(3000);

      byte[] dados = "DISCOVER".getBytes();

      java.net.DatagramPacket pacoteEnvio = new java.net.DatagramPacket(dados, dados.length,
          java.net.InetAddress.getByName("255.255.255.255"), 8080);
      socket.send(pacoteEnvio);

      byte[] bufferResposta = new byte[1024];
      java.net.DatagramPacket pacoteResposta = new java.net.DatagramPacket(bufferResposta, bufferResposta.length);

      socket.receive(pacoteResposta);

      String resposta = new String(pacoteResposta.getData(), 0, pacoteResposta.getLength()).trim();
      if (resposta.equals("DISCOVER_OK")) {
        String ipEncontrado = pacoteResposta.getAddress().getHostAddress();
        System.out.println("CLIENTE - Servidor encontrado no IP: " + ipEncontrado);
        return ipEncontrado;
      } // fim do if
    } catch (Exception e) {
      System.out.println("CLIENTE - Servidor nao encontrado (Timeout).");
    } // fim do try-catch
    return null;
  } // fim do metodo descobrirServidor

}