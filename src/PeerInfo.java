import java.util.Objects;

public class PeerInfo {
    String username;
    String address;

    PeerInfo(String username, String address) {
        this.username = username;
        this.address = address;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        PeerInfo other = (PeerInfo) obj;
        return Objects.equals(this.address, other.address);
        //if two peers have same address they are the same
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }
}