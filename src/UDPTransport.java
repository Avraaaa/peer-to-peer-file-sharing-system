import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

public class UDPTransport implements Transport {

    private final DatagramSocket socket;
    private final InetAddress targetAddress;
    private final int targetPort;
    private boolean isConnected;

    public UDPTransport(String host, int port) throws IOException {
        this.socket = new DatagramSocket();
        this.targetAddress = InetAddress.getByName(host);
        this.targetPort = port;
        this.isConnected = true;
    }

    public UDPTransport(DatagramSocket socket, InetAddress address, int port) {
        this.socket = socket;
        this.targetAddress = address;
        this.targetPort = port;
        this.isConnected = true;
    }

    public void setSoTimeout(int timeout) throws SocketException {
        socket.setSoTimeout(timeout);
    }

    @Override
    public void sendLine(String line) throws IOException {
        if (!isConnected) {
            throw new IOException("UDP Transport is closed");
        }

        byte[] data = line.getBytes("UTF-8");
        DatagramPacket packet = new DatagramPacket(data, data.length, targetAddress, targetPort);
        socket.send(packet);
    }

    @Override
    public String readLine() throws IOException {
        if (!isConnected) {
            throw new IOException("UDP Transport is closed");
        }

        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        return new String(packet.getData(), 0, packet.getLength(), "UTF-8");
    }

    @Override
    public void sendBytes(byte[] data, int length) throws IOException {
        if (!isConnected) {
            throw new IOException("UDP Transport is closed");
        }

        DatagramPacket packet = new DatagramPacket(data, length, targetAddress, targetPort);
        socket.send(packet);
    }

    @Override
    public int readBytes(byte[] buffer) throws IOException {
        if (!isConnected) {
            throw new IOException("UDP Transport is closed");
        }

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        return packet.getLength();
    }

    @Override
    public void close() throws IOException {
        isConnected = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
