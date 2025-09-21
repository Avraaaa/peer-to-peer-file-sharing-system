import java.io.IOException;
import java.util.Map;

interface UserRepository {
    void loadUsers();
    void saveUsers() throws IOException;
    void saveUserStats(User user) throws IOException;
    Map<String, User> getUsers();
}