import java.lang.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class PartialHTTP1Server implements Runnable {

	private Socket csocket;

	PartialHTTP1Server (Socket csocket) {
		this.csocket = csocket;
	}

	//Check validity of port input to make sure it's an integer
	private static int checkPortInput(String input) {
		try {
			return Integer.parseInt(input);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static void main (String[] args) {

		//Check validity of arguments
		if (args.length != 1) {
			System.out.println("Error: Invalid input. Please use java SimpleHHTPServer <port>");
			return;
		}

		int portNum = checkPortInput(args[0]);
		ServerSocket ssock;

		//If port arg is not an integer, return error
		if(portNum == -1)
			System.out.println("Error: Invalid format for port number. Please use an integer.");
		else
		{
			//Otherwise, create server socket and begin listening
			ExecutorService threadPoolExecutor =
					new ThreadPoolExecutor(
							5, 50, 5000, TimeUnit.MILLISECONDS,
							new LinkedBlockingQueue<Runnable>());
			try {
				ssock = new ServerSocket(portNum);
				System.out.println("Listening on Port Number " + portNum);

				while(true) {
					//When a client connects, spawn a new thread to handle it
					Socket sock = ssock.accept();
					System.out.println("Connected!");
					try {
					threadPoolExecutor.execute(new PartialHTTP1Server(sock));
					} catch (RejectedExecutionException rej) {
						try {
						PrintWriter outToClient = new PrintWriter(sock.getOutputStream(), true);
						outToClient.println("503 Service Unavailable");
						outToClient.close();
						ssock.close();
						} catch (IOException f) {
							System.out.println("Error handling client input.");
						}
					}
					//new Thread(new SimpleHTTPServer(sock)).start();
				}
			} catch (IOException e) {
				System.out.println("Error accepting connection.");
			}
		}
	}

	private String parseClientInput(String clientInput) {
		if (clientInput == null)
			return "400 Bad Request";

		String[] tokens = clientInput.split("\\s+");

		//1. Parse format. Only two tokens, and first token is capitalized? Does the second token begin with /?
		if(tokens.length != 2 || !tokens[0].toUpperCase().equals(tokens[0]) || tokens[1].charAt(0) != '/')
			return "400 Bad Request";

		//2. Parse command. Is the first token GET?
		if(!tokens[0].equals("GET"))
			return "501 Not Implemented";

		//3. Parse file. Does the file exist on the server?
		String filePath = "." + tokens[1];

		File f = new File(filePath);
		if (!f.isFile())
			return "404 Not Found";

		BufferedReader br = null;
		FileReader fr = null;

		//4. Otherwise, try to open the file and send response.
		try {

			fr = new FileReader(filePath);
			br = new BufferedReader(fr);

			String currentLine;
			String response = "200 OK\n\n";

			while ((currentLine = br.readLine()) != null) {
				response+=currentLine;
			}

			response+="\n";
			br.close();
			fr.close();

			return response;

		} catch (IOException e) {
			//5. Did anything go wrong with the request?
			return "500 Internal Error";
		}

	}

	public void run() {
		try {
			//Read in input from client, parse input, return proper code
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(csocket.getInputStream()));
			PrintWriter outToClient = new PrintWriter(csocket.getOutputStream(), true);

			try {
				//Set server socket timeout
				csocket.setSoTimeout(3000);
				//Read in message from client
				String messageFromClient = inFromClient.readLine();
				System.out.println("Read from client: " + messageFromClient);
				//Parse input from client
				String response = parseClientInput(messageFromClient);
				System.out.println("Sending response: " + response);
				//Send response code to client
				outToClient.println(response);
			} catch (SocketTimeoutException e){
				//If client does not send data in 3 seconds, send back timeout error
				outToClient.println("408 Request Timeout");
			}
			//Close connections
			inFromClient.close();
			outToClient.close();
			csocket.close();
		} catch (IOException e) {
			System.out.println("Error handling client input.");
		}
	}



}