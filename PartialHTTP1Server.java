import java.lang.*;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class PartialHTTP1Server implements Runnable {

	private Socket csocket;
	private byte[] fileBytes;

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

	private String contentType(String extension) {

		String contentType = "";

		switch(extension) {

		case "html":
			contentType = "text/html";
			break;
		case "c":
		case "c++":
		case "cc":
		case "com":
		case "conf":
		case "cxx":
		case "def":
		case "f":
		case "f90":
		case "for":
		case "g":
		case "h":
		case "hh":
		case "idc":
		case "jav":
		case "java":
		case "list":
		case "log":
		case "lst":
		case "m":
		case "mar":
		case "pl":
		case "sdml":
		case "text":
		case "txt":
			contentType = "text/plain";
			break;
		case "gif":
			contentType = "image/gif";
			break;
		case "jfif":
		case "jfif-tbnl":
		case "jpe":
		case "jpeg":
		case "jpg":
			contentType = "image/jpeg";
			break;
		case "png":
		case "x-png":
			contentType = "image/png";
			break;
		case "pdf":
			contentType = "application/pdf";
			break;
		case "gz":
		case "gzip":
			contentType = "application/x-gzip";
			break;
		case "zip":
			contentType = "application/zip";
			break;
		default:
			contentType = "application/octet-stream";
			break;

		}

		return contentType;

	}

	private String buildHeader (File file, String fileName) {
		String header = "";

		String extension = "";

		if (fileName.indexOf('.') != -1)
			extension = fileName.substring(fileName.indexOf('.')+1);
		header += "Content-Type: " + contentType(extension) + '\r' + '\n';
		header += "Content-Length: " + file.length() + '\r' + '\n';

		SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss zzz");

		header += "Last-Modified: " + sdf.format(file.lastModified()) + '\r' + '\n';
		header += "Content-Encoding: identity" + '\r' + '\n';
		header += "Allow: GET, POST, HEAD" + '\r' + '\n';

		Calendar now = Calendar.getInstance();
		now.add(Calendar.HOUR, 24);
		header += "Expires: " + sdf.format(now.getTime()) + '\r' + '\n';



		return header;


	}

	private String parseClientInput(String clientInput) {
		if (clientInput == null)
			return "HTTP/1.0 400 Bad Request";

		String[] tokens = clientInput.split("\\s+");

		float versionNum;

		//1. Parse format. Only three tokens, and first token is capitalized? Does the second token begin with /? Does the third token begin with HTTP/?
		if(tokens.length != 3 || !tokens[0].toUpperCase().equals(tokens[0]) || tokens[1].charAt(0) != '/' || !tokens[2].substring(0,5).equals("HTTP/") || tokens[2].substring(5) == null)
			return "HTTP/1.0 400 Bad Request";

		try {
			versionNum = Float.parseFloat(tokens[2].substring(5));
		} catch (NumberFormatException num) {
			return "HTTP/1.0 400 Bad Request";
		}

		if (versionNum > 1.0 || versionNum < 0.0)
			return tokens[2] + " 505 HTTP Version Not Supported";

		//2. Parse command. Is the first token GET?
		if(!tokens[0].equals("GET"))
			return tokens[2] + " 501 Not Implemented";

		//3. Parse file. Does the file exist on the server?
		String filePath = "." + tokens[1];

		File f = new File(filePath);
		if (!f.isFile())
			return tokens[2] + "404 Not Found";

		//BufferedReader br = null;
		//FileReader fr = null;
		Path path = Paths.get(filePath);

		//4. Otherwise, try to open the file and send response.
		try {

			//fr = new FileReader(filePath);
			//br = new BufferedReader(fr);
			fileBytes = Files.readAllBytes(path);


			//String currentLine;
			String response = tokens[2] + " 200 OK" + '\r' + '\n';

			response += buildHeader(f, tokens[1]);


			//while ((currentLine = br.readLine()) != null) {
			//	response+=currentLine;
			//}

			response+='\n';
			//br.close();
			//fr.close();

			return response;

		} catch (IOException e) {
			//5. Did anything go wrong with the request?
			return tokens[2] + "403 Forbidden";
		}

	}

	public void run() {
		try {
			//Read in input from client, parse input, return proper code
			BufferedReader inFromClient = new BufferedReader(new InputStreamReader(csocket.getInputStream()));
			PrintWriter outToClient = new PrintWriter(csocket.getOutputStream(), true);
			String command = "";
			String modified = "";
			int numLines = 0;

			try {
				//Set server socket timeout
				//csocket.setSoTimeout(3000);
				//Read in message from client
				while (numLines != 2) {
					if (numLines == 0)
						command = inFromClient.readLine();
					else if (numLines == 1)
						modified = inFromClient.readLine();
					numLines++;
				}
				//String messageFromClient = inFromClient.readLine();
				//System.out.println("Read from client: " + messageFromClient);
				System.out.println("Command: " + command + "; Modified: " + modified);
				System.out.println("Num Lines: " + numLines);
				//Parse input from client
				String response = parseClientInput(command);
				System.out.println("Sending response: " + response);
				//Send response code to client
				outToClient.println(response);
			} catch (SocketTimeoutException e){
				//If client does not send data in 3 seconds, send back timeout error
				outToClient.println("HTTP/1.0 408 Request Timeout");
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