import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final int PORT = 9090;

    private static final List<FileEntry> fileRegistry = Collections.synchronizedList(new ArrayList<>());
    private static final List<PeerInfo> activePeers = Collections.synchronizedList(new ArrayList<>());
    private static final AccountService accountService = new AccountService("users.csv");

    // Map helps specifically for forced disconnection.
    private static final Map<String, ClientHandler> activeHandlers = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("Napster-style Server is running on port " + PORT);
        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                pool.execute(new ClientHandler(listener.accept(), fileRegistry, activePeers, accountService, activeHandlers));
            }
        }
    }
}