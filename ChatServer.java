import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.List;
import java.util.ArrayList;

public class ChatServer{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();

  static Selector selector;
  static String nick = "";

  static Set<String> nicks = new HashSet<String>();
  static Map<String, List<ClientId> > channels = new HashMap<String, List<ClientId> >();
  static LinkedList<ClientId> clientList = new LinkedList<ClientId>();
  static String unprocessedpart = "";

  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );

    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port "+port+"\n");

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();

        while (it.hasNext()) {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
            SelectionKey.OP_ACCEPT) {

            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from "+s + "\n");

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            //sc.register( selector, SelectionKey.OP_READ );
            SelectionKey readKey = sc.register(selector, SelectionKey.OP_READ);
            ClientId cid = new ClientId(readKey,sc);
            readKey.attach(cid);

          } else if ((key.readyOps() & SelectionKey.OP_READ) ==
            SelectionKey.OP_READ) {

            SocketChannel sc = null;

            try {

              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              boolean ok = processInput(sc,key);

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) {
                key.cancel();

                Socket s = null;
                try {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s+"\n");
                  s.close();
                } catch( IOException ie ) {
                  System.err.println( "Error closing socket "+s+": "+ie + "\n");
                }
              }

            } catch( IOException ie ) {

              // On exception, remove this channel from the selector
              key.cancel();

              try {
                sc.close();
              } catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc + "\n");
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }

  // Just read the message from the socket and send it to stdout
  static private boolean processInput( SocketChannel sc, SelectionKey key) throws IOException{
    ClientId cid = (ClientId) key.attachment();
    ClientId cidTemp;
    String newnick = "";
    String msgchat = "";
    Command cmd;

    // Read the message to the buffer
    buffer.clear();
    sc.read(buffer);
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit()==0) {
      return false;
    }

    // Decode and print the message to stdout
    String message = decoder.decode(buffer).toString();

    
    //Detect '\n'
    if((int)message.charAt(message.length() -1 ) != 10){
		unprocessedpart += message;
		return true;
      }else{
    	  	unprocessedpart  += message;
    	  	message = unprocessedpart;
      }

    //Catch a command
    if(message.charAt(0) == '/' && message.charAt(1) != '/'){
      String msg      = "";
      String body     = "";
      String privmsg  = "";
      String com      = "";

      //Remove '/' from message
      msg = message.replace("/","");

      //Separate the message by spaces
      String[] words = msg.split("\\s+");
       
      //The command is the first word
      com = words[0];
      if(words.length == 2) body = words[1];
      if(words.length >= 3){
        body = words[1];
        for(int i = 2; i < words.length; i++){
          privmsg += words[i];
          privmsg += " ";
        }
      }

      switch(com){
        case "nick":
          cmd = new Command(com,body,sc,selector,nicks);
          break;
        case "join":
          cmd = new Command(com,body,sc,selector,channels);
          break;
        case "priv":
          cmd = new Command(com,body,sc,selector,privmsg);
          break;
        default:
          cmd = new Command(com,body,sc,selector,nicks);
          break;
      }

      switch(cmd.processCom(key)){
        // State transitions /nick
        case "ok_nick":
          cid.state = "outside";
          cid.nick = body;
          nicks.add(body);
          clientList.add(cid);
          sendMessage("OK",cid.key,buffer);
          break;
        case "error_nick":
          sendMessage("ERROR, Nick já utilizado",cid.key,buffer);
          break;
        case "new_nick_inside":
          List<ClientId> clients = channels.get(cid.sala);
          for(int i = 0; i < clients.size(); i++){
            cidTemp = clients.get(i);
            if(!cidTemp.nick.equals(cid.nick)){
              newnick = "> " + cid.nick + " mudou o nome para "+ body;
              sendMessage(newnick, cidTemp.key, buffer);
              }
            else sendMessage("OK",cidTemp.key, buffer);
          }
          cid.nick = body;
          break;
        //State transitions /join
        case "ok_join":
          cid.state = "inside";
          clients = channels.get(body);
          cid.sala = body;
          newnick = "JOINED " + cid.nick;
          sendChat(newnick, cid);
          clients.add(cid);
          channels.put(body, clients);
          sendMessage("OK", cid.key, buffer);
          break;
        case "ok_join_newsala":
          cid.state = "inside";
          cmd = new Command(sc, selector);
          List<ClientId> nickname = new ArrayList<ClientId>();
          cid.sala = body;
          nickname.add(cid);
          channels.put(body, nickname);
          sendMessage("OK", cid.key, buffer);
          break;
        case "ok_join_newsala_inside":
          clients = channels.get(cid.sala);
          clients.remove(cid);
          channels.put(cid.sala, clients);
          msgchat = "LEFT " + cid.nick;
          sendChat(msgchat, cid);
          if(clients.size() == 0) channels.remove(cid.sala);
          cid.sala = body;
          nickname = new ArrayList<ClientId>();
          nickname.add(cid);
          channels.put(body, nickname);
          sendMessage("OK", cid.key, buffer);
          break;
        case "ok_join_inside":
          clients = channels.get(cid.sala);
          clients.remove(cid);
          channels.put(cid.sala, clients);
          msgchat = "LEFT " + cid.nick;
          sendChat(msgchat, cid);
          if(clients.size() == 0) channels.remove(cid.sala);
          cid.sala = body;
          clients = channels.get(body);
          if(clients == null) clients = new ArrayList<ClientId>();
          channels.put(body, clients);
          msgchat = "JOINED " + cid.nick;
          sendChat(msgchat,cid);
          clients.add(cid);
          sendMessage("OK", cid.key, buffer);
          break;
        //State transitions /leave
        case "ok_leave":
          clients = channels.get(cid.sala);
          clients.remove(cid);
          channels.put(cid.sala, clients);
          cid.state = "outside";
          msgchat = "LEFT " + cid.nick;
          sendChat(msgchat, cid);
          sendMessage("OK", cid.key, buffer);
          if(clients.size() == 0) channels.remove(cid.sala);
          break;
        case "error_leave":
          sendMessage("ERROR", cid.key, buffer);
          break;  
        //State transitions /bye
        case "ok_bye":
          sendMessage("BYE", cid.key, buffer);
          cid.disconnect();
          break;
        case "leave_bye":
          clients = channels.get(cid.sala);
          clients.remove(cid);
          channels.put(cid.sala, clients);
          msgchat = "LEFT " + cid.nick;
          sendChat(msgchat, cid);
          sendMessage("BYE", cid.key, buffer);
          if(clients.size() == 0) channels.remove(cid.sala);
          cid.disconnect();
          break;
        //State transitions /priv
        case "ok_priv":
          SelectionKey privkey = findKey(body);
          if(privkey != null){
            sendMessage("> Mensagem privada enviada", cid.key, buffer);
            msgchat = "Mensagem privada de " + cid.nick + ": " + cmd.privmsg;
            sendMessage(msgchat, findKey(cmd.body), buffer);
          }
          else sendMessage("ERROR", cid.key, buffer);
          break;
        //Case not a command
        case "notacommand":
          msgchat = nick + ": " + message;
          sendChat(msgchat, cid);
          break;
        //Any other error
        case "error":
          sendMessage("ERROR", cid.key, buffer);
          break;
        //Default
        default:
          break;
        }
    }
    //Sends /message to chat when //message
    else if(message.charAt(0) == '/' && message.charAt(1) == '/'){
      if(cid.nick != null) nick = cid.nick.toString();
      else{
        sendMessage("ERROR", key, buffer);
        return true;
      }
      if(cid.state == "inside"){
        String newmessage = nick + ": " + message.substring(1);
        sendChat(newmessage, cid);
      }
    }
    else{
      if(cid.nick != null) nick = cid.nick.toString();
      else{
        sendMessage("ERROR", key, buffer);
        return true;
      }
      if(cid.state == "inside"){
        String newmessage = nick + ": " + message;
        sendChat(newmessage, cid);
      }
    }
    unprocessedpart = "";
    return true;
  }
  
  //Find the client key by his nickname
  static public SelectionKey findKey(String nick){ 
	 SelectionKey key = null;
   
	 for(int i = 0; i < clientList.size(); i++)
		 if(nick.equals(clientList.get(i).nick)) return clientList.get(i).key;
	
	 return key;
  }

  //Send message to client
  static void sendMessage(String msg, SelectionKey key, ByteBuffer buffer){
		SocketChannel sctmp;
		byte[] toBuffer;
	
		buffer.clear();
		buffer.rewind();
		
    //If it ends in '\n' (equal to int 10)
		if((int)msg.charAt(msg.length() - 1) != 10) msg = msg + "\n";
    
		toBuffer = msg.getBytes();
		buffer.put(toBuffer);
		buffer.flip();
    
		sctmp = (SocketChannel)key.channel();
	    try{
				sctmp.write(buffer);
			}catch (IOException e) {
				System.out.println("ERROR sending message");
			}
	}

  //Send message to every client in the sala
  static void sendChat(String msg, ClientId cid){
		ClientId cidTmp;
    List<ClientId> clients = channels.get(cid.sala);
    
		for(int i=0; i < clients.size(); i++){
			cidTmp = clients.get(i);
			sendMessage(msg, cidTmp.key, buffer);
		}
	}
  
}

class Command{
  String privmsg  = "";
  String com      = "";
  String body     = "";
  SocketChannel sc;
  Selector selector;
  Set<String> nicks;
  Map<String,List<ClientId> > channels;

  // /leave and /bye
  public Command(String com, SocketChannel sc, Selector selector){
    this.com = com;
    this.sc = sc;
    this.selector = selector;
  }

  // /nick <name>
  public Command(String com, String body, SocketChannel sc, Selector selector, Set<String> nicks){
    this.com = com;
    this.body = body;
    this.sc = sc;
    this.selector = selector;
    this.nicks = nicks;
  }

  // /join <channel>
  public Command(String com, String body, SocketChannel sc, Selector selector, Map<String,List <ClientId>> channels){
    this.com = com;
    this.body = body;
    this.sc = sc;
    this.selector = selector;
    this.channels = channels;
  }
  // /priv <message>
  public Command(String com, String body, SocketChannel sc, Selector selector, String privmsg){
    this.privmsg = privmsg;
    this.com = com;
    this.body = body;
    this.sc = sc;
    this.selector = selector;
  }

  // empty
  public Command(SocketChannel sc, Selector selector){
    this.sc = sc;
    this.selector = selector;
  }

  //Checks if nickname is taken
  private boolean duplicateNick(){
    return nicks.contains(body);
  }

  //Checks if channel exists
  private boolean channelExists(){
    return channels.containsKey(body);
  }

  public String processCom(SelectionKey key){
    ClientId cid = (ClientId) key.attachment();

    //Process /nick
    if(this.com.equals("nick")){
      if(!duplicateNick()){
        if(cid.state == "init") return "ok_nick";
        else if(cid.state == "inside") return "new_nick_inside";
        return "ok_nick";
      }
      else return "error_nick";
    }

  //Process /join
    else if(this.com.equals("join")){
      if(cid.nick != null){
        if(channelExists()){
          if(cid.state != "inside") return "ok_join";
          else return "ok_join_inside";
        }
        else{
          if(cid.state != "inside") return "ok_join_newsala";
          else return "ok_join_newsala_inside";
        }
      }
      return "error_join";
    }

    //Process /leave
    else if(this.com.equals("leave")){
      if(cid.state == "inside") return "ok_leave";
      else return "error_leave";
    }

    //Process /bye
    else if(this.com.equals("bye")){
      if(cid.state != "inside") return "ok_bye";
      else return "leave_bye";
    }

    //Process /priv
    else if(this.com.equals("priv")){
      if(cid.state != "init") return "ok_priv";
      else return "error";
    }

    return "notacommand";
  }

  public String toString() {
		String str;
		str = com + " " + body;
		return str;
	}

}

class ClientId{
  ByteBuffer buffer;
	SocketChannel sc;
	SelectionKey key;

	String sala;
	String nick;	
	String state; // init, inside, outside

  ClientId(SelectionKey key, SocketChannel sc) throws Exception{
    this.sc = sc;
		this.key = key;
		this.state = "init";

    buffer = ByteBuffer.allocate(16384);
  }

  void disconnect(){
		try{
			if(key != null) key.cancel();
			if(sc == null) return;
			sc.close();
		}catch(Throwable t){
			System.out.println("ERROR: in disconnect client: " + t.toString());
		}
	}
}