import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

//TEST

public class BookServer {
   static Queue<String> clientQueue = new LinkedList<String>();
   static Semaphore queueLock = new Semaphore(1);
   
   static HashMap<String, Integer> inventory = new HashMap<String, Integer>();
   static Semaphore inventoryLock = new Semaphore(1);
   
   static HashMap<Integer, String> transactions = new HashMap<Integer, String>();
   static Semaphore transactionsLock = new Semaphore(1);
   
   
   static int recordID =1 ;
   static Semaphore recordIDLock = new Semaphore(1);
   
   


  public static void main (String[] args) {
	BookServer bookserver = new BookServer();
    int tcpPort;
    int udpPort;
    if (args.length != 1) {
      System.out.println("ERROR: Provide 1 argument: input file containing initial inventory");
      System.exit(-1);
    }
    String fileName = args[0];
    tcpPort = 7000;
    udpPort = 8000;

    String input = null;
    
    try {
		FileReader fileReader = new FileReader(fileName);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		
		while((input = bufferedReader.readLine()) != null){
			String[] split = input.split("\" ");
			split[0] = split[0].replaceAll("\"", "");
			int split1 = Integer.parseInt(split[1]);
			
			inventory.put(split[0], split1);
		}
		
	} catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    TCPServer tcpserver = new TCPServer();
    Thread tcpThread = new Thread(tcpserver);
    tcpThread.start();
    // parse the inventory file

    // TODO: handle request from clients
    //UDP
    DatagramPacket datapacket;
    try {
		DatagramSocket datasocket = new DatagramSocket(udpPort);
		byte[] buf = new byte[1024];
		while(true){
			datapacket = new DatagramPacket(buf, buf.length);
			datasocket.receive(datapacket);
			String receivedString = new String(datapacket.getData());
			
			queueLock.acquire();
				clientQueue.add(receivedString);
			queueLock.release();
			
			ParseUDP fuck = new ParseUDP(receivedString);
			Thread t = new Thread(fuck);
			t.start();
			
		}
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (InterruptedException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }
  
  

  
static class TCPServer implements Runnable{
	  ServerSocket serverSocket;
	  Socket socket;
	  BufferedReader input;
	  Thread bookshelfInstance;
	  
	 
	  
		@Override
		public void run() {
			try {
				serverSocket = new ServerSocket(7000);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			while(true){
				try {
					socket = serverSocket.accept();
					this.input = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
					String message = input.readLine();
					
					queueLock.acquire();
						clientQueue.add(message);
					queueLock.release();
					
					ParseTCP tcpFuck = new ParseTCP(message);
					Thread t = new Thread();
					t.start();
					
				
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
		}
		
	}
	
	public static void incrementRecordID() throws InterruptedException{
		recordIDLock.acquire();
			recordID ++;
		recordIDLock.release();

	}
	
	public static void addTransaction(int recordID, String book) throws InterruptedException{
		transactionsLock.acquire();
			transactions.put(recordID, book);
			incrementRecordID();
			
		transactionsLock.release();
		
	}

	public static int borrowBook(String book) throws InterruptedException{
		inventoryLock.acquire();
			if(inventory.containsKey(book) == false){
				inventoryLock.release();	

				return -1;
				
			}
			
			else if(inventory.get(book) == 0){
				inventoryLock.release();	

				return -2;
			}
			else{
				
				int num = inventory.get(book);
				num --;
				inventory.put(book, num);
				int currentID = recordID;
				addTransaction(recordID, book);
				inventoryLock.release();	

				return currentID;
			}
			
	}
	
	public static int returnBook(String book) throws InterruptedException{
		inventoryLock.acquire();
			if(inventory.containsKey(book)){
				inventoryLock.release();	
				return -1;
				
			}
						
			else{
				int num = inventory.get(book);
				num ++;
				inventory.put(book, num);
				inventoryLock.release();	
				return 1;
			}
			
	}
  
  
}


class ParseUDP implements Runnable{
	String command;
	Thread t;
	
	public ParseUDP(String cmd){
		command = cmd;
		
	}
	
	public void sendToClient(int port, String message) throws IOException{
		InetAddress IPAddress = InetAddress.getByName("localhost");
		DatagramSocket datasocket = new DatagramSocket(port,IPAddress);		
		DatagramPacket returnPacket = new DatagramPacket(message.getBytes(), message.getBytes().length,IPAddress, port);
		datasocket.send(returnPacket);
		datasocket.close();
		

	}
	  
	@Override
	public void run() {
		
		String[] tokens = command.split(" ");

	
		  if (tokens[0].equals("borrow")) {
			  String[] isolateTitle = command.split("\"");
			  String[] isolatePort = command.split("^");
			  int port = Integer.parseInt(isolatePort[1]);
			  String title = isolateTitle[1];
			  String name = tokens[1];
			  String reply ="";
			  
			  try {
				int status = BookServer.borrowBook(name);
				if(status == -1){
					//send book does not exist UDP
					reply = "Request Failed - We do not have this book";
					sendToClient(port, reply);
					
				}
				else if(status == -2){
					//send book out of stock UDP
					reply=("Request Failed - Book not available");
					sendToClient(port, reply);

				}
				else{
					//send transaction success UDP
					reply=("Your request has been approved, " + status + " " + name + " " + title);
					sendToClient(port, reply);

				}
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			  
			  
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("return")) {
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("inventory")) {
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("list")) {
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("exit")) {
		    // TODO: send appropriate command to the server 
		  } else {
		    System.out.println("ERROR: No such command");
		  }
		
	}
	
}


class ParseTCP implements Runnable{
	String command;
	Thread t;
	public ParseTCP(String cmd){
		command = cmd;
		
	}
	  
	@Override
	public void run() {
		
		String[] tokens = command.split(" ");

	
		  if (tokens[0].equals("borrow")) {
			  String[] isolateTitle = command.split("\"");
			  String title = isolateTitle[1];
			  String name = tokens[1];
			  
			  try {
				int status = BookServer.borrowBook(name);
				if(status == -1){
					//send book does not exist UDP
				}
				else if(status == -2){
					//send book out of stock UDP
				}
				else{
					//send transaction success UDP
				}
				
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			  
			  
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("return")) {
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("inventory")) {
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("list")) {
		    // TODO: send appropriate command to the server and display the
		    // appropriate responses form the server
		  } else if (tokens[0].equals("exit")) {
		    // TODO: send appropriate command to the server 
		  } else {
		    System.out.println("ERROR: No such command");
		  }
		
	}
	
}



