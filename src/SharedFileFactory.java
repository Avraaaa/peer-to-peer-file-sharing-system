import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class SharedFileFactory {

    public static SharedFile createSharedFile(Path filePath) {
        String fileName = filePath.getFileName().toString();
        long fileSize = 0;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            System.err.println("Could not determine file size for: " + fileName);
        }

        String extension = getFileExtension(fileName).toLowerCase();
        switch (extension) {
            case "zip": case "rar": case "7z": case "tar": case "gz":
                return new ArchivedFile(fileName, fileSize, getZipFileCount(filePath));
            default:
                return createSharedFile(fileName, fileSize);
        }
    }

    public static SharedFile createSharedFile(String fileName, long fileSize) {
        String extension = getFileExtension(fileName).toLowerCase();
        switch (extension) {
            case "mp3": case "wav": case "flac": case "m4a":
                return new AudioFile(fileName, fileSize, "Unknown Artist", "Unknown Album", 180);
            case "mp4": case "mkv": case "avi": case "mov":
                return new VideoFile(fileName, fileSize, "1920x1080", "H.264", 300);
            case "png": case "jpg": case "jpeg": case "gif": case "bmp":
                return new ImageFile(fileName, fileSize, 1920, 1080);
            case "txt": case "md":
                return new DocumentFile(fileName, fileSize, (int) (fileSize / 500) + 1);
            case "pdf": case "doc": case "docx":
                return new DocumentFile(fileName, fileSize, 10);
            case "java":
                return new SourceFile(fileName, fileSize, "Java", 100);
            case "c": case "h":
                return new SourceFile(fileName, fileSize, "C", 100);
            case "cpp": case "hpp":
                return new SourceFile(fileName, fileSize, "C++", 120);
            case "py":
                return new SourceFile(fileName, fileSize, "Python", 80);
            case "js":
                return new SourceFile(fileName, fileSize, "JavaScript", 90);
            case "zip": case "rar": case "7z": case "tar": case "gz":
                return new ArchivedFile(fileName, fileSize, 0);
            default:
                return new VersionedFile(fileName, fileSize);
        }
    }

    public static int getZipFileCount(Path filePath) {
        int fileCount = 0;
        try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    fileCount++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading zip file '" + filePath.getFileName() + "': " + e.getMessage());
            return 0;
        }
        return fileCount;
    }

    private static String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }
}
