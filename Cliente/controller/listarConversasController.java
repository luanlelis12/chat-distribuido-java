/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 29/06/2026
* Ultima alteracao.: 30/06/2026
* Nome.............: listarConversasController.java
* Funcao...........: Gerencia a interface de lista membros de um grupo ou listar os grupos no servidor
*******************************************************************/
package controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import util.processadorTexto;

public class listarConversasController implements Initializable {

  @FXML
  private VBox vboxConversas;
  @FXML
  private Pane barraSuperior;
  @FXML
  private Label tituloLabel;

  private clienteController controladorPai;
  private ArrayList<String> conversas = new ArrayList<>();
  private String tipoConversa;

  private double xOffset = 0;
  private double yOffset = 0;

  @Override
  public void initialize(URL location, ResourceBundle resources) {
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
   * Metodo: carregarDados
   * Funcao: Recebe a lista que veio do servidor e desenha os botoes
   * Parametros: itens = grupos disponiveis ou membros do grupo, tipo = define se sao usuarios ou grupos
   * Retorno: void
   */
  public void carregarDados(ArrayList<String> itens, String tipo) {
    this.tipoConversa = tipo;
    this.conversas = itens;

    vboxConversas.getChildren().clear(); // Limpa itens velhos, se houver

    if (tipo.equals(clienteController.GRUPO)) {
      tituloLabel.setText("Grupos");
    } else if (tipo.equals(clienteController.PRIVADO)) {
      tituloLabel.setText("Membros");
    } // fim do if-else

    // Mostra um aviso se nao houver ninguem ou nenhum grupo
    if (conversas.isEmpty()) {
      vboxConversas.getChildren().add(new Label("Nenhum item encontrado."));
      return;
    } // fim do if

    // Exibe na tela todos os grupos/membros
    for (String item : conversas) {
      adicionarGruposOuMembros(processadorTexto.retirarFlagEscape(item));
    } // fim do for
  }

  /*
   * Metodo: adicionarGruposOuMembros
   * Funcao: adicionar os grupos/membros na tela
   * Parametros: nomeConversa = nome do membro/grupo
   * Retorno: void
   */
  public void adicionarGruposOuMembros(String nomeConversa) {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/grupoButton.fxml"));
      AnchorPane itemConversa = loader.load();

      ImageView icone = (ImageView) itemConversa.lookup("#iconeConversa");

      Label labelNome = (Label) itemConversa.lookup("#nomeConversa");
      if (labelNome != null) {
        labelNome.setText(nomeConversa);
      } // fim do if

      Label notificacaoLabel = (Label) itemConversa.lookup("#notificacaoLabel");
      ImageView notificacaoImage = (ImageView) itemConversa.lookup("#notificacaoImage");
      itemConversa.getChildren().remove(notificacaoLabel);
      itemConversa.getChildren().remove(notificacaoImage);

      Button sairConversa = (Button) itemConversa.lookup(".buttonSair");
      itemConversa.getChildren().remove(sairConversa);

      // Se for um usuario inicializa uma imagem diferente
      if (tipoConversa.equals(clienteController.PRIVADO)) {
        String caminhoImagem = "/view/img/iconPriv.png";

        try {
          Image novaImagem = new Image(getClass().getResourceAsStream(caminhoImagem));
          icone.setImage(novaImagem);
        } catch (Exception e) {
          System.out.println("Aviso: Imagem nao encontrada, mantendo a foto padrao.");
        } // fim do try-catch
      } // fim do if

      if (tipoConversa.equals(clienteController.GRUPO)) { // se for um grupo possibilita entrar no grupo clicando nele
        itemConversa.setOnMouseClicked(event -> {
          controladorPai.entrarGrupo(nomeConversa);
          fecharTela();
        });
      } else if (tipoConversa.equals(clienteController.PRIVADO)) { // se for um usuario criar uma conversa no privado clicando nele
        itemConversa.setOnMouseClicked(event -> {
          controladorPai.criarConversaPrivada(nomeConversa);
          fecharTela();
        });
      } // fim do if

      vboxConversas.getChildren().add(itemConversa);
    } catch (Exception e) {
      System.out.println("CLIENTE - Erro: Nao foi possivel carregar o visual do grupo!");
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo adicionarConversaNaTela

  /*
   * Metodo: fecharTela
   * Funcao: Fecha a tela
   * Parametros: 
   * Retorno: void
   */
  public void fecharTela() {
    Stage janela = (Stage) vboxConversas.getScene().getWindow();
    janela.close();
  }

  /*
   * Metodo: minimizarTela
   * Funcao: minimizar a tela
   * Parametros: event = evento que ativou o metodo
   * Retorno: void
   */
  public void minimizarTela(ActionEvent event) {
    Stage janela = (Stage) ((Node) event.getSource()).getScene().getWindow();
    janela.setIconified(true);
  } // fim do metodo minimizarTela

  public void setControladorPai(clienteController controladorPai) {
    this.controladorPai = controladorPai;
  }

  public ArrayList<String> getConversas() {
    return conversas;
  }

  public void setConversas(ArrayList<String> conversas) {
    this.conversas = conversas;
  }

  public void addConversas(String conversa) {
    conversas.add(conversa);
  }

  public String getTipoConversa() {
    return tipoConversa;
  }

  public void setTipoConversa(String tipoConversa) {
    this.tipoConversa = tipoConversa;
  }

}