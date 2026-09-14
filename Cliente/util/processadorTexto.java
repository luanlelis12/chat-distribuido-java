/* ***************************************************************
* Autor............: Luan Alves Lelis Costa
* Matricula........: 202310352
* Inicio...........: 12/06/2026
* Ultima alteracao.: 12/06/2026
* Nome.............: processadorTexto.java
* Funcao...........: Classe para gerenciar o texto, inserindo ou tirando escapes e flags
*******************************************************************/
package util;

public class processadorTexto {
  
  public processadorTexto() {}

  public static String inserirFlagEscape(String texto) {
    String textoProcessado = "";

    for (char c : texto.toCharArray()) {
      if (c == '~' || c == '{') // Se tiver uma flag no texto
        textoProcessado += '{'; // adiciona um escape
      textoProcessado+=c;
    } // fim do for

    return textoProcessado;
  } // fim do metodo inserirFlagEscape

  public static String retirarFlagEscape(String texto) {
    String textoProcessado = "";
    boolean isEscape = true;

    for (char c : texto.toCharArray()) {
      if (c == '{' && isEscape) { // Se tiver um escape no texto
        isEscape = false; // false para impedir de pular um possivel '{' do texto
        continue; // pula para o proximo caractere
      } // fim do if
      textoProcessado+=c;
      isEscape = true;
    } // fim do for

    return textoProcessado;
  } // fim do metodo retirarFlagEscape

}
