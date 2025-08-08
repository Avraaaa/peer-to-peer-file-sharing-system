import java.io.*;
import java.net.*;
import java.nio.file.Path;

public class ChunkedDownload implements DownloadStrategy {
    private final int chunkSize;
    private final FileHandler fileHandler;
    private final Path sharedDirectory;

    public ChunkedDownload(int chunkSize, FileHandler fileHandler, Path sharedDirectory) {
        this.chunkSize = chunkSize;
        this.fileHandler = fileHandler;
        this.sharedDirectory = sharedDirectory;
    }

    @Override
    public void download(String peerAddress, String fileName) throws IOException {
        String[] parts = peerAddress.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        try (
                Transport transport = new TCPTransport(host, port);
                OutputStream output = fileHandler.getOutputStream(fileName)
        ) {
            transport.sendLine("DOWNLOAD " + fileName);

            byte[] buffer = new byte[chunkSize];
            int bytesRead;

            while ((bytesRead = transport.readBytes(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            System.out.println("Chunked download complete: " + fileName);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
