/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 01/07/2026
* Nome.............: clienteController.java
* Funcao...........: Faz a ponte de comunicacao entre a interface e a classe cliente
*******************************************************************/
package controller;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Pair;
import model.Cliente;
import model.Conversa;
import util.processadorTexto;

public class clienteController implements Initializable {

  @FXML
  private Button enviarMensagemButton;
  @FXML
  private Button enviarButton;
  @FXML
  private TextArea caixaDeMensagem;
  @FXML
  private TextArea mensagemField;
  @FXML
  private Label conversaSelecionadaLabel;
  @FXML
  private VBox vboxGrupos;
  @FXML
  private VBox conversaVBox;
  @FXML
  private Pane barraSuperior;
  @FXML
  private ScrollPane conversaScrollPane;
  @FXML
  private ToggleGroup tipoDeConversa;
  @FXML
  private Button abrirListaMembrosButton;

  private double xOffset = 0;
  private double yOffset = 0;

  static final String GRUPO = "grupo";
  static final String PRIVADO = "priv";

  private static clienteController instancia;

  private static Cliente cliente;

  private static Pair<String, String> conversaSelecionada = null; // Pair<nomeDaConversa,tipoDeConversa>
  private HashMap<Pair<String, String>, Conversa> listaConversas = new HashMap<>(); // <Pair<nomeDaConversa,tipoDeConversa>,Conversa>

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    instancia = this;
    System.out.println("O Controller foi carregado corretamente!");

    mensagemField.setOnKeyPressed((KeyEvent event) -> {
      // Verifica se a tecla apertada foi o ENTER
      if (event.getCode() == KeyCode.ENTER) {
        // Verifica se o usuario NAO esta segurando o Shift
        event.consume();
        if (!event.isShiftDown()) {
          enviarMensagem();
          mensagemField.clear();
        } // fim do if
      } // fim do if
    });

    // Abre a scrollPane da conversa na mensagem mais recente
    conversaVBox.heightProperty().addListener((observable, oldValue, newValue) -> {
      conversaScrollPane.layout();
      conversaScrollPane.setVvalue(1.0d);
    });

    // Muda o design do botao ao escrever algo na caixa de texto
    mensagemField.textProperty().addListener((observable, valorAntigo, valorNovo) -> {
      if (enviarButton != null) {
        enviarButton.getStyleClass().remove("buttonEnviar");
        enviarButton.getStyleClass().remove("buttonEnviarDes");
        if (valorNovo.trim().isEmpty()) {
          enviarButton.getStyleClass().add("buttonEnviarDes");
        } else {
          enviarButton.getStyleClass().add("buttonEnviar");
        } // fim do if
      } // fim do if
    });

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
   * Funcao: Cria um cliente e verifica se ele foi aprovado criar esse cliente
   * Parametros: nome = nome do usuario, ipServidor = id o servidor
   * Retorno: retorna se foi aprovado a criacao
   */
  public static boolean criarCliente(String nome, String ipServidor) {
    try {
      cliente = new Cliente(nome, ipServidor);
      boolean aprovado = cliente.fazerLogin();

      // Verifica se o login do cliente foi aprovada
      if (aprovado) {
        System.out.println("CLIENTE - Login aprovado!");
        cliente.start();
        return true;
      } else {
        cliente.desligarCliente();
        System.out.println("CLIENTE - Login desaprovado desligando conexao.");
        return false;
      } // fim do if-else

    } catch (Exception e) {
      e.printStackTrace();
      return false;
    } // fim do try-catch
  } // fim do metodo criarCliente

  /*
   * Metodo: enviarMensagem
   * Funcao: envia para a classe cliente a mensagem que o usuario quer enviar
   * Parametros:
   * Retorno: void
   */
  public void enviarMensagem() {

    // Impede do usuario mandar mensagem sem selecionar alguma conversa
    if (conversaSelecionada == null)
      return;

    String mensagem = mensagemField.getText();
    mensagemField.clear();

    // Impede do usuario mandar mensagem vazia
    if (mensagem.equals(""))
      return;

    HBox balaoDeDialogo = criarBalaoDialogo(mensagem, "Voce", true);

    // Adiciona o balao de mensagem no historico da conversa e na interface
    listaConversas.get(conversaSelecionada).adicionarMensagem(balaoDeDialogo);
    conversaVBox.getChildren().add(balaoDeDialogo);

    mensagem = processadorTexto.inserirFlagEscape(mensagem);
    System.out.println("CLIENTE - enviando mensagem \"" + mensagem + "\"");

    if (conversaSelecionada.getValue().equals(GRUPO)) {
      cliente.enviarMensagem(processadorTexto.inserirFlagEscape(conversaSelecionada.getKey()), mensagem);
    } else if (conversaSelecionada.getValue().equals(PRIVADO)) {
      cliente.enviarMensagemPrivado(processadorTexto.inserirFlagEscape(conversaSelecionada.getKey()), mensagem);
    } // fim do if
  } // fim do metodo enviarMensagem

  /*
   * Metodo: receberMensagem
   * Funcao: Recebe uma mensagem e armazena na conversa correta
   * Parametros: mensagem = mensagem recebida, nomeConversa = nome do grupo ou
   * usuario destinario, nomeRemetente = usuario que enviou a mensagem,
   * tipoConversa = se foi mandada para um grupo ou no privado
   * Retorno: void
   */
  public static void receberMensagem(String mensagem, String nomeConversa, String nomeRemetente, String tipoConversa) {

    nomeConversa = nomeConversa.trim();
    mensagem = mensagem.trim();
    nomeRemetente = nomeRemetente.trim();

    final String msgFinal = processadorTexto.retirarFlagEscape(mensagem);
    final String nomeFinal = processadorTexto.retirarFlagEscape(nomeRemetente);
    final String nomeConversaFinal;

    if (tipoConversa.equals(GRUPO)) { // verifica se eh uma mensagem de grupo ou priv e define o nome da conversa
      nomeConversaFinal = processadorTexto.retirarFlagEscape(nomeConversa);
    } else {
      nomeConversaFinal = processadorTexto.retirarFlagEscape(nomeRemetente);
    } // fim if-else

    Platform.runLater(() -> {
      HBox balaoDeDialogo = criarBalaoDialogo(msgFinal, nomeFinal, false);

      // Verifica quem enviou a mensagem
      if (nomeFinal.equals("SERVIDOR")) {
        balaoDeDialogo = criarAvisoSistema(msgFinal);
      } else {
        balaoDeDialogo = criarBalaoDialogo(msgFinal, nomeFinal, false);
      } // fim do if-else

      Pair<String, String> chaveRecebida = new Pair<>(nomeConversaFinal, tipoConversa);

      if (!instancia.listaConversas.containsKey(chaveRecebida)) {
        instancia.adicionarConversaNaTela(nomeConversaFinal, tipoConversa);
      } // fim if

      Conversa conversa = instancia.listaConversas.get(chaveRecebida);

      if (conversaSelecionada == null || !conversaSelecionada.equals(chaveRecebida)) {

        // Pega o numero atual de notifiacoes
        int contagemAtual = conversa.getNotificacoes();

        // Adiciona +1 nas notificacoes
        conversa.setNotificacoes(contagemAtual + 1);

        System.out
            .println("CLIENTE - " + nomeConversaFinal + " tem " + (contagemAtual + 1) + " novas mensagens.");
      } // fim do if

      conversa.adicionarMensagem(balaoDeDialogo);

      if (conversaSelecionada != null && conversaSelecionada.equals(chaveRecebida)) {
        instancia.conversaVBox.getChildren().add(balaoDeDialogo);
      } // fim if
    });

    System.out
        .println(
            "CLIENTE - exibindo mensagem \"" + msgFinal + "\" de " + nomeFinal + " na conversa " + nomeConversaFinal
                + ".");
  } // fim do metodo receberMensagem

  /*
   * Metodo: criarBalaoDialogo
   * Funcao: Exibir o balao de dialogo com a mensagem
   * Parametros: mensagem = mensagem recebida, nomeRemetente = usuario que enviou
   * a mensagem,
   * enviadaPorMim = se foi mandada por ele mesmo
   * Retorno: void
   */
  public static HBox criarBalaoDialogo(String mensagem, String nomeRemetente, boolean enviadaPorMim) {

    VBox balao = new VBox(5);

    balao.setMinWidth(200);
    balao.setMaxWidth(400);
    balao.setPadding(new Insets(10));

    // Muda o design da depende de quem enviou
    if (!enviadaPorMim && nomeRemetente != null && !nomeRemetente.isEmpty()) {
      Label labelNome = new Label(nomeRemetente);
      labelNome.setStyle("-fx-font-weight: bold; -fx-text-fill: #0000aa;");
      labelNome.setFont(new Font("System", 13));
      balao.getChildren().add(labelNome);
    } // fim do if

    Label textoMsg = new Label(mensagem);
    textoMsg.setFont(new Font("System", 14));
    textoMsg.setWrapText(true);

    textoMsg.setMaxWidth(380);

    balao.getChildren().add(textoMsg);

    HBox linha = new HBox(balao);

    if (enviadaPorMim) {
      linha.setPadding(new Insets(10, 25, 10, 15));
    } else {
      linha.setPadding(new Insets(10, 15, 10, 25));
    } // fim do if-else

    String estiloRetroBase = "-fx-border-color: #000000; " +
        "-fx-border-width: 2px; " +
        "-fx-effect: dropshadow(three-pass-box, #000000, 0, 0, 4, 4); ";

    if (enviadaPorMim) {
      linha.setAlignment(Pos.CENTER_RIGHT);
      balao.setStyle(estiloRetroBase + "-fx-background-color: #c6e7f8;");
    } else {
      linha.setAlignment(Pos.CENTER_LEFT);
      balao.setStyle(estiloRetroBase + "-fx-background-color: #FFFFFF;");
    } // fim do if-else

    return linha;
  } // fim do metodo criarBalaoDialogo

  /*
   * Metodo: entrarGrupo
   * Funcao: envia para a classe cliente o grupo que o usuario quer entrar
   * Parametros: nomeGrupo = grupo que o usuario quer entrar
   * Retorno: void
   */
  public void entrarGrupo(String nomeGrupo) {
    String nomeGrupoProcessado = processadorTexto.inserirFlagEscape(nomeGrupo);

    // Alert para impedir do usuario criar grupo com nome vazio
    if (nomeGrupoProcessado == null || nomeGrupoProcessado.trim().isEmpty()) {
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();

        controladorDoAlerta.setDetalhes("Nome do Grupo Vazio",
            "Por favor, digite um nome valido para o grupo em que deseja entrar ou criar.");

        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);

        janelaAlerta.show();
      } catch (IOException e) {
        System.out.println("Erro ao carregar o alerta!");
        e.printStackTrace();
      } // fim do try-catch
      return;
    } // fim do if

    Pair<String, String> grupo = new Pair<>(nomeGrupo, GRUPO);

    // Alert para impedir do usuario criar grupo com nome repetido
    if (listaConversas.containsKey(grupo)) {
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();

        controladorDoAlerta.setDetalhes("Ja Pertence ao Grupo",
            "Voce ja esta conectado a este grupo. Por favor, selecione-o na sua lista lateral para conversar.");

        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);

        janelaAlerta.show();
      } catch (IOException e) {
        System.out.println("Erro ao carregar o alerta!");
        e.printStackTrace();
      } // fim do try-catch
      return;
    } // fim do if

    try {
      boolean sucesso = cliente.entrarGrupo(nomeGrupoProcessado);

      if (sucesso) {
        System.out.println("CLIENTE - Entrada no grupo confirmada pelo servidor!");
        listaConversas.put(grupo, new Conversa(nomeGrupo, GRUPO));
        adicionarConversaNaTela(nomeGrupo, GRUPO);
      } else {
        System.out.println("CLIENTE - O servidor negou ou falhou a entrada no grupo.");
      } // fim do if
    } catch (Exception e) {
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo entrarGrupo

  /*
   * Metodo: sairGrupo
   * Funcao: tira o usuario da conversa e tira a conversa da interface
   * Parametros: itemConversa = AnchorPane que contem o botao do grupo
   * Retorno: void
   */
  public void sairGrupo(AnchorPane itemConversa) {
    Label label = (Label) itemConversa.lookup("#nomeConversa");

    if (label != null) {
      String nomeGrupo = label.getText();
      System.out.println("CLIENTE - Solicitando saida do grupo " + nomeGrupo + "...");

      String nomeGrupoProcessado = processadorTexto.inserirFlagEscape(nomeGrupo);

      boolean sucesso = cliente.sairGrupo(nomeGrupoProcessado);

      if (sucesso) {
        Pair<String, String> grupo = new Pair<String, String>(nomeGrupo, GRUPO);
        listaConversas.remove(grupo);

        if (conversaSelecionada != null) {
          String nomeConversaAberta = processadorTexto.retirarFlagEscape(conversaSelecionada.getKey());

          if (nomeConversaAberta.equals(nomeGrupo)) {
            conversaVBox.getChildren().clear();
            conversaSelecionadaLabel.setText("");
            conversaSelecionada = null;
            mensagemField.setDisable(true);
            abrirListaMembrosButton.setVisible(false);
          } // fim do if
        } // fim do if

        itemConversa.getChildren().clear();
        vboxGrupos.getChildren().remove(itemConversa);
      } else {
        System.out.println("CLIENTE - O servidor falhou em remover o usuario do grupo.");
      } // fim do if-else
    } // fim do if
  } // fim do metodo sairGrupo

  /*
   * Metodo: criarConversaPrivada
   * Funcao: cria uma conversa privada com outro usuario
   * Parametros: nomeUsuario = usuario que o cliente quer conversar
   * Retorno: void
   */
  public void criarConversaPrivada(String nomeUsuario) {
    String nomeUsuarioProcessado = processadorTexto.retirarFlagEscape(cliente.getNomeCliente());

    // impede de criar conversa com alguem de nome vazio
    if (nomeUsuarioProcessado == null || nomeUsuarioProcessado.trim().isEmpty()) {
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();

        controladorDoAlerta.setDetalhes("Nome do Usuario Vazio",
            "Por favor, digite um nome valido para o usuario em que deseja conversar.");

        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);

        janelaAlerta.show();
      } catch (IOException e) {
        System.out.println("Erro ao carregar o alerta!");
        e.printStackTrace();
      } // fim do try-catch
      return;
    } // fim do if

    if (nomeUsuario.equals(cliente.getNomeCliente())) {
      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();

        controladorDoAlerta.setDetalhes("Nome de Usuario Invalido",
            "Por favor, digite um nome diferente para o usuario em que deseja conversar.");

        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);

        janelaAlerta.show();
      } catch (IOException e) {
        System.out.println("Erro ao carregar o alerta!");
        e.printStackTrace();
      } // fim do try-catch
      return;
    }

    // Se o usuario ja tem essa conversa aberta apenas abre ela
    Pair<String, String> chavePrivada = new Pair<>(nomeUsuario, PRIVADO);
    if (listaConversas.containsKey(chavePrivada)) {
      abrirConversa(nomeUsuario, PRIVADO);
      return;
    } // fim do if

    boolean usuarioExiste = cliente.verificarUsuario(nomeUsuario);

    if (usuarioExiste) {
      System.out.println("CLIENTE - Usuario encontrado! Criando aba privada.");
      adicionarConversaNaTela(nomeUsuario, PRIVADO);
    } else {
      System.out.println("CLIENTE - O usuario " + nomeUsuario + " nao existe ou esta offline.");

      try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/alert.fxml"));
        Parent root = loader.load();

        alertController controladorDoAlerta = loader.getController();

        controladorDoAlerta.setDetalhes("Usuario Nao Encontrado", "O utilizador '" + nomeUsuario
            + "' nao existe ou esta offline no momento. Verifique se o nome foi digitado corretamente.");

        Stage janelaAlerta = new Stage();
        janelaAlerta.setScene(new Scene(root));
        janelaAlerta.initStyle(StageStyle.UNDECORATED);
        janelaAlerta.initModality(Modality.APPLICATION_MODAL);
        janelaAlerta.show();

      } catch (IOException e) {
        System.out.println("Erro ao carregar o alerta!");
        e.printStackTrace();
      } // fim do try-catch
    } // fim do if-else
  } // fim do metodo criarConversaPrivada

  /*
   * Metodo: adicionarConversaNaTela
   * Funcao: adiciona o grupo/usuario na interface para conversar
   * Parametros: nomeConversa = nome do usuario/grupo que esta conversando,
   * tipoConversa = define se eh um grupo e uma conversa
   * Retorno: void
   */
  public void adicionarConversaNaTela(String nomeConversa, String tipoConversa) {
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
      if (notificacaoLabel != null) {
        notificacaoLabel.setVisible(false);
        notificacaoImage.setVisible(false);
      } // fim do if

      Button sairConversa = (Button) itemConversa.lookup(".buttonSair");
      if (sairConversa != null) {
        sairConversa.setOnAction(event -> {
          sairGrupo(itemConversa);
        });
      } // fim do if

      if (tipoConversa.equals(PRIVADO)) {
        String caminhoImagem = "/view/img/iconPriv.png";

        try {
          Image novaImagem = new Image(getClass().getResourceAsStream(caminhoImagem));
          icone.setImage(novaImagem);
        } catch (Exception e) {
          System.out.println("Aviso: Imagem nao encontrada, mantendo a foto padrao.");
        } // fim do try-catch

        itemConversa.getChildren().remove(sairConversa);
      } // fim do if

      Pair<String, String> chaveConversa = new Pair<>(nomeConversa, tipoConversa);

      if (!listaConversas.containsKey(chaveConversa)) {
        listaConversas.put(chaveConversa, new Conversa(nomeConversa, tipoConversa));
      } // fim do if

      if (notificacaoLabel != null) {
        listaConversas.get(chaveConversa).setNotificacaoImage(notificacaoImage);
        listaConversas.get(chaveConversa).setNotificacaoLabel(notificacaoLabel);
      } // fim do if

      itemConversa.setOnMouseClicked(event -> {
        abrirConversa(nomeConversa, tipoConversa);
      });

      vboxGrupos.getChildren().add(itemConversa);

      if (tipoConversa.equals(GRUPO))
        abrirConversa(nomeConversa, tipoConversa);

    } catch (Exception e) {
      System.out.println("CLIENTE - Erro: Nao foi possivel carregar o visual do grupo!");
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo adicionarConversaNaTela

  /*
   * Metodo: criarAvisoSistema
   * Funcao: Cria um aviso centralizado na conversa
   * Parametros: mensagem = mensagem do servidor sobre saida de alguem ou entrada
   * num grupo
   * Retorno: void
   */
  public static HBox criarAvisoSistema(String mensagem) {
    Label textoMsg = new Label(">>> " + mensagem + " <<<");
    textoMsg.setFont(new Font("System", 12));

    String estiloRetroAlerta = "-fx-background-color: #fdf289; " +
        "-fx-text-fill: #000000; " +
        "-fx-font-weight: bold; " +
        "-fx-border-color: #000000; " +
        "-fx-border-width: 2px; " +
        "-fx-padding: 5px 15px; " +
        "-fx-effect: dropshadow(three-pass-box, #000000, 0, 0, 4, 4);";

    textoMsg.setStyle(estiloRetroAlerta);
    textoMsg.setWrapText(true);
    textoMsg.setMaxWidth(380);
    textoMsg.setAlignment(Pos.CENTER);

    HBox linha = new HBox(textoMsg);
    linha.setAlignment(Pos.CENTER);

    linha.setPadding(new Insets(10, 15, 15, 15));

    return linha;
  } // fim do metodo criarAvisoSistema

  /*
   * Metodo: abrirConversa
   * Funcao: abre a conversa (grupo/usuario)
   * Parametros: nomeConversa = nome do grupo/usuario, tipoConversa = se eh um
   * usuario ou grupo
   * Retorno: void
   */
  public void abrirConversa(String nomeConversa, String tipoConversa) {
    conversaSelecionada = new Pair<>(nomeConversa, tipoConversa);
    conversaSelecionadaLabel.setText(nomeConversa);

    listaConversas.get(conversaSelecionada).setNotificacoes(0);
    System.out.println("CLIENTE - Lidas as mensagens de: " + nomeConversa);

    if (tipoConversa.equals(GRUPO)) {
      abrirListaMembrosButton.setVisible(true);
    } else {
      abrirListaMembrosButton.setVisible(false);
    } // fim do if-else
    mensagemField.setDisable(false);

    conversaVBox.getChildren().clear();
    conversaVBox.getChildren().addAll(listaConversas.get(conversaSelecionada).getHistorico());
  } // fim do metodo abrirConversa

  /*
   * Metodo: selecionarGrupo
   * Funcao:
   * Parametros:
   * Retorno: void
   */
  public void selecionarGrupo(ActionEvent event) {
    String grupo = ((Node) event.getSource()).toString();
    System.out.println(grupo);
  } // fim do metodo selecionarGrupo

  /*
   * Metodo: abrirTelaEntrarConversa
   * Funcao: abre uma para adicionar grupos e comecar outras conversas
   * Parametros:
   * Retorno: void
   */
  public void abrirTelaEntrarConversa() {
    try {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/entrarConversa.fxml"));
      Parent root = loader.load();

      entrarConversaController controladorPopup = loader.getController();

      controladorPopup.setControladorPai(this);

      Stage janelEntrarConversa = new Stage();
      janelEntrarConversa.setScene(new Scene(root));
      janelEntrarConversa.initStyle(StageStyle.UNDECORATED);
      janelEntrarConversa.initModality(Modality.APPLICATION_MODAL);
      janelEntrarConversa.show();

    } catch (IOException e) {
      System.out.println("CLIENTE - Erro: Nao foi possivel carregar a tela de adicionar conversas: ");
      e.printStackTrace();
    } // fim do try-catch
  } // fim do metodo abrirTelaEntrarConversa

  /*
   * Metodo: abrirListaGrupos
   * Funcao: Pede ao cliente solicitar no servidor o grupos disponiveis
   * Parametros:
   * Retorno: void
   */
  public void abrirListaGrupos() {
    cliente.solicitarListaGrupos();
  } // fim do metodo abrirListaGrupos

  /*
   * Metodo: abrirListaGrupos
   * Funcao: Pede ao cliente solicitar no servidor os membros de um grupo
   * especifico
   * Parametros:
   * Retorno: void
   */
  public void abrirListaMembros() {
    if (conversaSelecionada != null && conversaSelecionada.getValue().equals(GRUPO)) {
      String nomeGrupoProcessado = processadorTexto.inserirFlagEscape(conversaSelecionada.getKey());
      cliente.solicitarListaMembros(nomeGrupoProcessado);
    } // fim do if
  } // fim do metodo abrirListaMembros

  /*
   * Metodo: exibirListaConversas
   * Funcao: Chamado pelo UDP quando a lista chega. Abre o popup com os dados.
   * Parametros: itens = grupos/usuarios, tipo = define se os itens sao grupos ou
   * usuarios
   * Retorno: void
   */
  public static void exibirListaConversas(ArrayList<String> itens, String tipo) {
    Platform.runLater(() -> {
      try {
        FXMLLoader loader = new FXMLLoader(instancia.getClass().getResource("/view/usuariosPane.fxml"));
        Parent root = loader.load();

        listarConversasController controladorPopup = loader.getController();
        controladorPopup.setControladorPai(instancia);

        controladorPopup.carregarDados(itens, tipo);

        Stage janelaLista = new Stage();
        janelaLista.setScene(new Scene(root));
        janelaLista.initStyle(StageStyle.UNDECORATED);
        janelaLista.initModality(Modality.APPLICATION_MODAL);
        janelaLista.show();

      } catch (IOException e) {
        System.out.println("CLIENTE - Erro ao abrir a lista recebida!");
        e.printStackTrace();
      } // fim do try-catch
    });
  } // fim do metodo exibirListaConversas

  /*
   * Metodo: fecharAplicacao
   * Funcao: fechar a aplicacao
   * Parametros: fecha a
   * Retorno: void
   */
  public void fecharAplicacao() {
    System.out.println("CLIENTE - Iniciando encerramento da aplicacao...");

    if (cliente != null) {
      listaConversas.forEach((chave, valor) -> {
        System.out.println("CLIENTE - Desconectando do grupo: " + valor.getNome());

        String grupoProcessado = processadorTexto.inserirFlagEscape(valor.getNome());

        cliente.sairGrupo(grupoProcessado);
      });
      cliente.fazerLogout();
      cliente.desligarCliente();

      Platform.exit();
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      } // fim do try-catch
    } // fim do if

    System.out.println("CLIENTE - Aplicacao encerrada com sucesso.");
    System.exit(0);
  } // fim do metodo fecharAplicacao

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

}
