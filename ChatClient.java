import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient{
    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    ByteBuffer buffer;
    SocketChannel sc;
    private String server;
    private int port;
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }
    
    // Construtor
    public ChatClient(String server, int port) throws IOException {
        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        this.server = server;
        this.port = port;
        this.buffer = ByteBuffer.allocate(16384);
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        // PREENCHER AQUI com código que envia a mensagem ao servidor
        this.buffer.clear();
        buffer.rewind();

        message = message + "\n";

        buffer.put(message.getBytes());
        buffer.flip();
        sc.write(buffer);
  }

    // Método principal do objecto
    public void run() throws IOException {
      // PREENCHER AQUI
        InetSocketAddress hostAddress = new InetSocketAddress(this.server, this.port);
    		sc = SocketChannel.open(hostAddress);
        inFromServer fs = new inFromServer(sc,chatArea);
        fs.run();
      //newMessage("teste");
    }
    
    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}

class inFromServer implements Runnable{
  JTextArea chatArea;
	ByteBuffer buffer;
	SocketChannel sc;

  public inFromServer(SocketChannel sc, JTextArea chatArea){
    this.sc = sc;
    this.chatArea = chatArea;
    this.buffer = ByteBuffer.allocate(16384);
  }

  public String processMessage(String message){
    String first  = "";
    String tail   = "";
    String word1  = "";
    String word2  = "";
    String msgout = "";
    int spacepos = message.indexOf(" "); // Position of first 'space'

    // If message has no space, only one word
    if(spacepos != -1) first = message.substring(0, spacepos);
    // Otherwise, separate the first word from message
    else first = message.substring(0);
    tail = message.substring(spacepos + 1);

    // Utility to separate nicks and messages, etc.
    spacepos = tail.indexOf(" ");
    if(spacepos != -1){
      word1 = tail.substring(0, spacepos);
      if(word1.charAt(word1.length()-1) == '\n'){
        word1 = word1.substring(0, word1.length() - 1);
      }
    }

    word2 = tail.substring(spacepos+1);

    // Remove \n from words
    if(first.charAt(first.length()-1) == '\n'){
      first = first.substring(0, first.length()-1);
    }
    if(word2.charAt(word2.length()-1) == '\n'){
      word2 = word2.substring(0, word2.length()-1);
    }

    switch(first){
      case "OK":
        msgout = "> Comando Sucedido\n";
        break;
      case "ERRO":
        msgout = "Erro\n";
        break;
      case "JOINED":
        msgout = "> " + word2 + " entrou na sala!\n";
        break;
      case "NEWNICK":
        msgout = "> " + word1 + " mudou de nome para " + word2 + "!\n";
        break;
      case "LEFT":
        msgout = "> " + word2 + " saiu da sala!\n";
        break;
      case "MESSAGE":
        msgout = word1 + ": " + word2 + "\n";
        break;
      case "PRIVATE":
        msgout = word1 + " mandou-te uma mensagem privada: " + word2 + "\n";
      default:
        msgout = message;
        break;
    }
    return msgout;
  }

  public void read() throws IOException {
      String msg;
      String new_message;
      Charset charset = Charset.forName("UTF8");
      CharsetDecoder decoder = charset.newDecoder();
        
      buffer.clear();
      sc.read(buffer);
      buffer.flip();

      msg = decoder.decode(buffer).toString();

      new_message = processMessage(msg);
      printMessage(new_message);
  }

  public void printMessage(final String message) {
        chatArea.append(message);
  }

  public void run(){
    while(true)
      try{
        read();
      }catch (IOException e){
				System.out.println("ERROR run (message from server)");
      }
  }
}