import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ChunkedDownload implements DownloadStrategy {

    private final int chunkSize;
    private final FileHandler fileHandler;
    private final Path downloadDirectory;

    public ChunkedDownload(int chunkSize, FileHandler fileHandler, Path downloadDirectory) {
        this.chunkSize = chunkSize;
        this.fileHandler = fileHandler;
        this.downloadDirectory = downloadDirectory;
    }

    @Override
    public void download(String peerAddress, String fileName) throws IOException {
        String host;
        int port;

        // ROBUSTNESS FIX: Validate the peerAddress before using it.
        try {
            String[] parts = peerAddress.split(":", 2);
            if (parts.length != 2) {
                throw new IOException("Invalid peer address format: " + peerAddress);
            }
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            throw new IOException("Could not parse peer address: " + peerAddress, e);
        }

        try (
                Socket peerSocket = new Socket(host, port);
                Transport transport = new TCPTransport(peerSocket)
        ) {
            transport.sendLine("DOWNLOAD " + fileName);

            Path destinationPath = downloadDirectory.resolve(fileName);
            long totalBytesRead = 0;

            try (OutputStream fileOut = Files.newOutputStream(destinationPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                byte[] buffer = new byte[chunkSize];
                int bytesRead;

                System.out.println("Downloading...");
                while ((bytesRead = transport.readBytes(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }
                System.out.println("Download stream finished.");
            }

            if (totalBytesRead == 0 && Files.exists(destinationPath)) {
                if (Files.size(destinationPath) == 0) {
                    System.out.println("Warning: Received an empty file or peer did not send data.");
                }
            }
        }
    }
}