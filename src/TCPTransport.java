import java.io.*;
import java.net.Socket;

public class TCPTransport implements Transport {

    private final Socket socket;
    private final PrintWriter out;
    private final BufferedReader in;
    private final InputStream rawIn;
    private final OutputStream rawOut;

    public TCPTransport(String host, int port) throws IOException{
        this(new Socket(host,port));
    }

    public TCPTransport(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new PrintWriter(socket.getOutputStream(),true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.rawIn = socket.getInputStream();
        this.rawOut = socket.getOutputStream();
    }

    @Override
    public void sendLine(String line) {
        out.println(line);
    }

    public String readLine() throws IOException {
        return in.readLine();
    }

    @Override
    public void sendBytes(byte[] data, int length) throws IOException {
        rawOut.write(data, 0, length);
        rawOut.flush();
    }

    @Override
    public int readBytes(byte[] buffer) throws IOException{
        return rawIn.read(buffer);
    }

    @Override
    public void close() throws IOException{
        socket.close();
    }


}
