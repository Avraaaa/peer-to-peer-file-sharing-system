import java.io.Closeable;
import java.io.IOException;

public interface Transport extends Closeable {
    void sendLine(String line) throws IOException;
    String readLine() throws IOException;

    void sendBytes(byte[] data, int length) throws IOException;
    int readBytes(byte[] buffer) throws IOException;
}