import java.io.IOException;

public interface DownloadStrategy {
    void download(String peerAddress, String fileName) throws IOException;
}


