import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LocalFileHandler implements FileHandler {
    private final Path sharedDirectory;

    public LocalFileHandler(String sharedDirPath) throws IOException {
        this.sharedDirectory = Paths.get(sharedDirPath);
        if (!Files.exists(sharedDirectory)) {
            Files.createDirectories(sharedDirectory);
        }
    }

    @Override
    public List<String> listSharedFiles() {
        List<String> fileNames = new ArrayList<>();

        try {
            DirectoryStream<Path> files = Files.newDirectoryStream(sharedDirectory);
            for (Path p : files) {
                if (Files.isRegularFile(p)) {
                    fileNames.add(p.getFileName().toString());
                }
            }
        } catch (IOException e) {
            //return empty list if there's an error
        }

        return fileNames;
    }


    @Override
    public OutputStream getOutputStream(String fileName) throws IOException {
        Path fullPath = sharedDirectory.resolve(fileName);

        // Convert the Path to a File
        File file = fullPath.toFile();

        // return an OutputStream to write to that file
        OutputStream out = new FileOutputStream(file);
        return out;
    }

    @Override
    public InputStream getInputStream(String fileName) throws IOException {
        Path filePath = sharedDirectory.resolve(fileName);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found in shared directory: " + fileName);
        }
        InputStream in = Files.newInputStream(filePath);
        return in;
    }

    @Override
    public byte[] readFileChunk(String fileName, int chunkSize) throws IOException {
        Path filePath = sharedDirectory.resolve(fileName);
        InputStream inputStream = Files.newInputStream(filePath);
        byte[] buffer = new byte[chunkSize];
        int bytesRead = inputStream.read(buffer);
        inputStream.close();
        // If nothing was read (end of file), return an empty array
        if (bytesRead == -1) {
            return new byte[0];
        }
        //  Return the filled buffer (contains data from the file)
        return buffer;
    }


    @Override
    public void saveFileChunk(String fileName, byte[] data, int length) throws IOException {
        Path path = sharedDirectory.resolve(fileName);
        File file = path.toFile();
        FileOutputStream fos = new FileOutputStream(file, true);
        BufferedOutputStream out = new BufferedOutputStream(fos);
        // Write only the given number of bytes into the file
        out.write(data, 0, length);
        out.close();
    }


    public Path getSharedDirectory() {
        return sharedDirectory;
    }

    @Override
    public boolean fileExists(String fileName) {
        return Files.exists(sharedDirectory.resolve(fileName));
    }
}