public class DownloadStats implements Stats{

    private long downloadedFiles;
    private long downloadedBytes;

    @Override
    public void addFile(){
        downloadedFiles++;
    }

    @Override
    public void addBytes(long bytes){
        downloadedBytes +=bytes;
    }

    @Override
    public long getFileCount(){
        return downloadedFiles;
    }

    @Override
    public long getTotalBytes(){
        return downloadedBytes;
    }

    @Override
    public String toCsvString(){
        return downloadedFiles + "," + downloadedBytes;
    }

    @Override
    public void fromCsvString(String csv){
        if(csv==null||csv.isEmpty()) return;
        String[] parts = csv.split(",");
        if(parts.length == 2){
            this.downloadedFiles = Long.parseLong(parts[0]);
            this.downloadedBytes = Long.parseLong(parts[1]);
        }
    }

}
