package client3.src;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import chunkification.src.FileChunkObject;

public class Client3 {

	private static final String dir = System.getProperty("user.dir");
	static String chunksLocation = dir + "/src/client3/chunks/";
	ServerSocket receiveSocket;
	Socket connectionSocket;
	Socket clientSocket;
	ObjectInputStream inStream;
	ObjectOutputStream outStream;
	Set<Integer> chunkList;
	Set<Integer> myChunkList;
	private int fileOwnerServerPort;
	private int uploadPort;
	private int downloadPort;

	public static void main(String[] args) {
		Client3 c = new Client3();

		File outFolder = new File(chunksLocation);


		if (outFolder.mkdirs())
			System.out.println("Creating a folder to keep chunks");
		else
			System.out.println("Folder already present");

		int totalFilesToRecv;
		try {
			
			c.readPortValues();
			
			System.out.println("Waiting for file server...");
			
			c.TCPConnectAsAClient(c.fileOwnerServerPort);
			totalFilesToRecv = (int) c.inStream.readObject();
			c.chunkList = Collections.synchronizedSet(new LinkedHashSet<Integer>());
			c.myChunkList = Collections.synchronizedSet(new LinkedHashSet<Integer>());

			for (int i = 1; i <= totalFilesToRecv; i++)
				c.chunkList.add(i);

			int filesToReceive = (int) c.inStream.readObject();
			while (filesToReceive > 0) {
				FileChunkObject rChunkObj = c.receiveChunk(Boolean.TRUE, c.fileOwnerServerPort);
				if (rChunkObj != null)
					c.createChunkFile(chunksLocation, rChunkObj);
				else
					System.out.println("No chunks available");
				filesToReceive--;
			}
			c.TCPDisconnectAsAClient(Boolean.TRUE, c.fileOwnerServerPort);

			Thread thread = new Thread(new Runnable() {
				public void run() {
					c.TCPConnectAsAServer(c.uploadPort, c);

				}
			});
			thread.start();

			c.TCPConnectAsAClient(c.downloadPort);

			while (true) {
				
				if (!c.chunkList.isEmpty()) {
					Integer[] a = c.chunkList.toArray(new Integer[c.chunkList.size()]);

					for (int i = 0; i < a.length; i++) {
					
														
						c.outStream.writeObject(a[i]);
						c.outStream.flush();
						FileChunkObject rChunkObj = c.receiveChunk(Boolean.FALSE, c.downloadPort);
						if (rChunkObj != null)
							c.createChunkFile(chunksLocation, rChunkObj);
					}
				} else {
					c.outStream.writeObject(-1);
					c.outStream.flush();
					break;
				}
				Thread.sleep(2000);
			}
			c.TCPDisconnectAsAClient(Boolean.FALSE, c.downloadPort);
			c.combineChunks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void TCPConnectAsAServer(int port, Client3 c) {
		try {
			int neighbourCount = 1;
			receiveSocket = new ServerSocket(port);
			System.out.println("Client 3 - Server socket created, waiting for connection...");
			while (true) {
				if (neighbourCount > 0) {
					neighbourCount--;
					connectionSocket = receiveSocket.accept();
					System.out.println("Recieved connection from Client 2 at port number - " + port);
					new UploadThread(connectionSocket, chunksLocation, c).start();

				} else {
					System.out.println("Currently connected");
					break;
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void TCPConnectAsAClient(int port) throws InterruptedException {
		boolean b = true;
		while (b) {
			try {

				b = false;
				clientSocket = new Socket("127.0.0.1", port);
				System.out.println("Client 3 connected to download neighbor at port number - " + port);
				
				inStream = new ObjectInputStream(clientSocket.getInputStream());
				outStream = new ObjectOutputStream(clientSocket.getOutputStream());
			} catch (ConnectException e) {

				System.out.println("Unable to connect to socket at port number - " + port + "... Attempting to re-connect...");
				Thread.sleep(5000);
				b = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public FileChunkObject receiveChunk(Boolean ownerOrNeighbour, int port) {
		FileChunkObject chunkObj = null;
		try {
			
			chunkObj = (FileChunkObject) inStream.readObject();
			if(chunkObj != null)
			{
				if( ownerOrNeighbour)
					System.out.println("Client 3 received chunk number - " + chunkObj.getFileNum() + " from file server at port number - " + port);
				else
				{
					System.out.println("Requesting list of available chunks from Client 4");
					
					String downloadNeighborChunkList = (String) inStream.readObject();
					
					System.out.println("List of available chunks from Client 4 - "+ downloadNeighborChunkList);
					
					System.out.println("Client 3 received chunk number - " + chunkObj.getFileNum() + " from download neighbour at port number - " + port);

					}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return chunkObj;
	}

	public void TCPDisconnectAsAClient(Boolean ownerOrNeighbour, int port) {
		try {
			inStream.close();
			clientSocket.close();
			if( ownerOrNeighbour)
				System.out.println("Closing connection with file server at port number - " + port);
			else
				System.out.println("Closing connection with download neighbour at port number - " + port);
			} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void createChunkFile(String chunksLocation, FileChunkObject rChunkObj) {
		try {
			FileOutputStream fileOutStream = new FileOutputStream(new File(chunksLocation, rChunkObj.getFileName()));
			BufferedOutputStream bufferOutStream = new BufferedOutputStream(fileOutStream);
			bufferOutStream.write(rChunkObj.getFileData(), 0, rChunkObj.getChunksize());

			chunkList.remove(rChunkObj.getFileNum());
			myChunkList.add(rChunkObj.getFileNum());

			bufferOutStream.flush();
			bufferOutStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void readPortValues() throws FileNotFoundException {
		String str = null;

		BufferedReader br = new BufferedReader(new FileReader("configuration.txt"));
		try {
			str = br.readLine();
			String[] tokens = str.split(" ");
			fileOwnerServerPort = Integer.parseInt(tokens[1]);
			for (int i = 1; i <= 3; i++) {
				str = br.readLine();
			}
			tokens = str.split(" ");
			uploadPort = Integer.parseInt(tokens[1]);
			int clientNeighbor = Integer.parseInt(tokens[2]);

			br.close();

			BufferedReader br1 = new BufferedReader(new FileReader("configuration.txt"));
			for (int i = 0; i <= clientNeighbor; i++) {
				str = br1.readLine();
			}
			tokens = str.split(" ");
			downloadPort = Integer.parseInt(tokens[1]);
			br1.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void combineChunks() {

		String chunkLocation = chunksLocation;
		File[] files = new File(chunkLocation).listFiles();
											
		try {
			
			int fileNameLength = files[0].getName().length() - 5;
			String newFileName = files[0].getName().substring(0, fileNameLength);

			FileOutputStream fileOutStream = new FileOutputStream(new File(chunkLocation + "/" + newFileName));
			for (File f : files) {
				
				Files.copy(f.toPath(), fileOutStream);

			}
			fileOutStream.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

class UploadThread extends Thread {

	private Socket socket;
	ObjectOutputStream outStream;
	ObjectInputStream inStream;
	String chunkLoc;
	Client3 c;

	UploadThread(Socket s, String chunkLoc, Client3 client) {
		this.socket = s;
		this.chunkLoc = chunkLoc;
		this.c = client;
	}

	public void run() {
		try {

			outStream = new ObjectOutputStream(socket.getOutputStream());
			inStream = new ObjectInputStream(socket.getInputStream());

			while (true) {
												
				int ChunkNum = (int) inStream.readObject();
				if (ChunkNum < 0)
					break;

				File[] files = new File(chunkLoc).listFiles();
				String[] s;
				File currentFile = null;
				boolean haveFile = false;
				for (int i = 0; i < files.length; i++) {
					currentFile = files[i];

					s = files[i].getName().split("_");

					if (ChunkNum == Integer.parseInt(s[1])) {
						haveFile = true;
						break;
					}
				}
				FileChunkObject sChunkObj;
				if (haveFile) {
					sChunkObj = constructChuckFileObject(currentFile, ChunkNum);
				} else {
					sChunkObj = constructChuckFileObject(null, -1);
				}

				sendChunkObject(sChunkObj, c);

			}
			TCPDisconnectAsAServer();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized FileChunkObject constructChuckFileObject(File file, int chunkNum) throws IOException {
		FileChunkObject chunkObj = null;
		if (chunkNum > 0) {
			byte[] chunk = new byte[102400]; 
			chunkObj = new FileChunkObject();

			chunkObj.setFileNum(chunkNum);

			chunkObj.setFileName(file.getName());
			FileInputStream fileInStream = new FileInputStream(file);

			BufferedInputStream bufferInStream = new BufferedInputStream(fileInStream);

			int bytesRead = bufferInStream.read(chunk);

			chunkObj.setChunksize(bytesRead);

			chunkObj.setFileData(chunk);

			bufferInStream.close();
			fileInStream.close();
		}
		return chunkObj;
	}

	public void sendChunkObject(FileChunkObject sChunkObj, Client3 c) {
		try {
			
			outStream.writeObject(sChunkObj);
			
			if(sChunkObj!= null)
			{			
				outStream.writeObject(c.myChunkList.toString());
				System.out.println("Sending chunk number - "+ sChunkObj.getFileNum() + " to Client 2");
			}

			
			outStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void TCPDisconnectAsAServer() {
		try {
			outStream.close();
			socket.close();
			System.out.println("Client 3 - Server socket closed");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
