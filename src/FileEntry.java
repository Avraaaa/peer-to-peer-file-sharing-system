import java.util.ArrayList;
import java.util.List;

public class FileEntry {
    String fileName;
    List<PeerInfo> peers = new ArrayList<>();

    FileEntry(String fileName) {
        this.fileName = fileName;
    }


    void addPeer(PeerInfo peer) {
        if (!peers.contains(peer)) {
            peers.add(peer);
        }
    }

    void removePeer(PeerInfo peer) {
        peers.remove(peer);
    }
}