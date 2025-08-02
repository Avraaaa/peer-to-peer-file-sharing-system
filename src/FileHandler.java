import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface FileHandler {
    List<String> listSharedFiles();
    OutputStream getOutputStream(String fileName)
            throws Exception;
    InputStream getInputStream(String fileName)
            throws IOException;
    byte[] readFileChunk(String fileName, int chunkSize)
            throws Exception;
    void saveFileChunk(String fileName, byte[] data, int length)
            throws Exception;
    boolean fileExists(String fileName);
}