# 💬 Sistema de Chat Distribuído (Java)

Aplicativo de mensageria baseado na arquitetura Cliente-Servidor desenvolvido como projeto acadêmico de Sistemas Distribuídos e Redes de Computadores. O sistema permite comunicação em tempo real em rede local utilizando um protocolo híbrido customizado (TCP e UDP), operado através de uma interface gráfica com estética retro clássica.

## 🚀 Funcionalidades Principais

* **Autodescoberta de Servidor (Broadcast):** Localização automática do servidor na rede local através de pacotes UDP Broadcast, eliminando a necessidade de configuração manual de IP.
* **Comunicação em Tempo Real:** 
  * Salas de chat em grupo dinâmicas.
  * Mensagens privadas (Direct Messages) com validação de disponibilidade.
* **Visualização Única (VU):** Envio de mensagens sensíveis que são destruídas permanentemente da interface após a primeira leitura.
* **Confirmações de Leitura (Ticks):** Rastreamento assíncrono do ciclo de vida da mensagem (Enviada, Entregue, Lida) semelhante a aplicativos de mercado.
* **Controle de Privacidade:** Sistema de bloqueio e desbloqueio de usuários processado diretamente no servidor.

## ⚙️ Arquitetura de Rede e Protocolo

O projeto divide as responsabilidades da camada de transporte para equilibrar confiabilidade e performance na troca de objetos (`APDU`):

1. **Canal de Descoberta (UDP):** O cliente emite um pacote em *broadcast*; o servidor intercepta e devolve o IP de conexão.
2. **Canal de Controle (TCP):** Garante a entrega exata e a ordem de comandos que alteram o estado crítico da aplicação (`REGISTER`, `JOIN`, `LEAVE`, `BLOCK`). 
3. **Canal de Mensagens (UDP):** Trafega os objetos pesados de texto e notificações de status (`SEND`, `SENDPVT`, `CONFIRM`) sem travar a *thread* principal, permitindo alta concorrência.

## 🛠️ Tecnologias e Estruturas Utilizadas

* **Linguagem:** Java (JDK 8+)
* **Interface Gráfica:** JavaFX
* **Sockets API:** `java.net.Socket`, `java.net.DatagramSocket`, `java.net.ServerSocket`
* **Concorrência:** Threads para manipulação de múltiplos clientes simultâneos no servidor.

## 🏃 Como Executar Localmente

### Pré-requisitos
* Java Development Kit (JDK) instalado.
* JavaFX SDK configurado na sua IDE (Eclipse, VS Code, IntelliJ).

### Passos de Inicialização
1. Compile e execute a classe `Principal.java` localizada no diretório `/Servidor`. O console exibirá o IP local e informará que as portas estão abertas.
2. Compile e execute a classe `Principal.java` localizada no diretório `/Cliente`.
3. O cliente realizará a varredura na rede para encontrar o servidor. Caso o *firewall* bloqueie o *broadcast*, um pop-up solicitará a inserção manual do IP (fornecido no console do servidor).
4. Insira seu nome de usuário e inicie a comunicação.