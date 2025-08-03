import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PeerClient {

    private final String serverHost;
    private final int serverPort;
    private final int myListenPort;
    private final FileHandler fileHandler;

  public PeerClient(String serverHost, int serverPort, int myListenPort, FileHandler fileHandler) {
    this.serverHost = serverHost;
    this.serverPort = serverPort;
    this.myListenPort = myListenPort;
    this.fileHandler = fileHandler;
}


    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java PeerClient <serverHost> <serverPort> <myListenPort> <sharedDirectory>");
            return;
        }

        try {
            String serverHost = args[0];
            int serverPort = 9090;
            int myListenPort = Integer.parseInt(args[1]);
            String sharedDir = args[2];

            FileHandler fileHandler = new LocalFileHandler(sharedDir);
PeerClient client = new PeerClient(serverHost, serverPort, myListenPort, fileHandler);
            client.start();

        } catch (NumberFormatException e) {
            System.err.println("Error: Port numbers must be integers.");
        } catch (IOException e) {
            System.err.println("Could not start client: " + e.getMessage());
        }
    }

    public void start() throws IOException {
        new Thread(new DownloadListener(myListenPort, sharedDirectory)).start();

        try (
                Socket serverSocket = new Socket(serverHost, serverPort);
                PrintWriter out = new PrintWriter(serverSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                Scanner console = new Scanner(System.in)
        ) {
            System.out.println("Connected to server. Ready for commands.");

            out.println("REGISTER " + myListenPort);
            shareLocalFiles(out);

            while (true) {
                System.out.println("\n[1] Search Files  [2] List Peers  [3] Exit");
                System.out.print("> ");
                String choice = console.nextLine();

                switch (choice) {
                    case "1":
                        System.out.print("Enter filename to search for: ");
                        String fileName = console.nextLine();
                        searchAndDownload(fileName, out, in, console);
                        break;
                    case "2":
                        listPeers(out, in);
                        break;
                    case "3":
                        out.println("UNREGISTER");
                        System.out.println("Exiting.");
                        return;
                    default:
                        System.out.println("Invalid option.");
                }
            }
        } catch (ConnectException e) {
            System.err.println("FATAL: Could not connect to the server at " + serverHost + ":" + serverPort);
        }
    }

  private void shareLocalFiles(PrintWriter serverOut) {
    System.out.println("Sharing files:");
    List<String> files = fileHandler.listSharedFiles();
    for (String fileName : files) {
        System.out.println("...sharing " + fileName);
        serverOut.println("SHARE " + fileName);
    }
}


    private void listPeers(PrintWriter out, BufferedReader in) throws IOException {
        out.println("LIST_PEERS");
        String response = in.readLine();
        System.out.println("\n--- Online Peers ---");
        if (response == null || response.isEmpty()) {
            System.out.println("No other peers are online.");
        } else {
            Arrays.stream(response.split(",")).forEach(System.out::println);
        }
        System.out.println("--------------------");
    }

    private void searchAndDownload(String fileName, PrintWriter out, BufferedReader in, Scanner console) throws IOException {
        out.println("SEARCH " + fileName);
        String response = in.readLine();

        if (response == null || response.isEmpty()) {
            System.out.println("File '" + fileName + "' not found on any peer.");
            return;
        }

        List<String> peersWithFile = Arrays.stream(response.split(",")).collect(Collectors.toList());
        System.out.println("\n--- File available on ---");
        for (int i = 0; i < peersWithFile.size(); i++) {
            System.out.printf("[%d] %s\n", i + 1, peersWithFile.get(i));
        }
        System.out.println("-------------------------");
        System.out.print("Choose a peer to download from (enter number, or 0 to cancel): ");

        try {
            int choice = Integer.parseInt(console.nextLine());
            if (choice > 0 && choice <= peersWithFile.size()) {
                String peerAddress = peersWithFile.get(choice - 1);
                downloadFile(peerAddress, fileName);
                out.println("SHARE " + fileName);
            } else {
                System.out.println("Download cancelled.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input. Please enter a number.");
        }
    }

    private void downloadFile(String peerAddress, String fileName) {
        System.out.println("Attempting to download '" + fileName + "' from " + peerAddress + "...");
        String[] parts = peerAddress.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        try (
                Socket peerSocket = new Socket(host, port);
                PrintWriter peerOut = new PrintWriter(peerSocket.getOutputStream(), true);
                InputStream peerIn = peerSocket.getInputStream();
                OutputStream fileOut = fileHandler.getOutputStream(fileName);
        ) {
            peerOut.println("DOWNLOAD " + fileName);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = peerIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }
            System.out.println("Download complete: " + fileName);

        } catch (IOException e) {
            System.err.println("Download failed: " + e.getMessage());
        }
    }

    private static class DownloadListener implements Runnable {
        private final int port;
        private final Filehandler fileHandler;

        public DownloadListener(int port, FileHandler fileHandler) {
            this.port = port;
            this.fileHandler= fileHandler;
        }

        @Override
        public void run() {
            try (ServerSocket listenerSocket = new ServerSocket(port)) {
                System.out.println("Download listener started on port " + port);
                while (true) {
                    Socket peerConnection = listenerSocket.accept();
                    new Thread(() -> handleDownloadRequest(peerConnection)).start();
                }
            } catch (IOException e) {
                System.err.println("FATAL: Download listener on port " + port + " failed: " + e.getMessage());
            }
        }

        private void handleDownloadRequest(Socket peerConnection) {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(peerConnection.getInputStream()));
                    OutputStream out = peerConnection.getOutputStream()
            ) {
                String request = in.readLine();
                if (request != null && request.startsWith("DOWNLOAD ")) {
                    String fileName = request.substring(9);
                    
                    if (fileHandler.fileExists(fileName)) {
                            try (InputStream fileIn = fileHandler.getInputStream(fileName)) {
                            byte[] buffer = new byte[8192];
                              int bytesRead;
                            while ((bytesRead = fileIn.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            }
                        }
                    System.out.println("-> Upload finished for " + fileName);
                }
                else {
                        System.err.println("Peer requested a file we don't have: " + fileName);
                    }
                }
            } catch (IOException e) {
                System.err.println("Warning: Error handling download request: " + e.getMessage());
            } finally {
                try {
                    peerConnection.close();
                } catch (IOException e) {
                }
            }
        }
    }
}
