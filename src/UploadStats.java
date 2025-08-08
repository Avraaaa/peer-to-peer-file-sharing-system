public class UploadStats implements Stats{
    private long uploadedFiles;
    private long uploadedBytes;


    @Override
    public void addFile() {
        uploadedFiles++;
    }

    @Override
    public void addBytes(long bytes) {
        uploadedBytes += bytes;
    }

    @Override
    public long getFileCount() {
        return uploadedFiles;
    }


    @Override
    public long  getTotalBytes(){
        return uploadedBytes;
    }

    @Override
    public String toCsvString(){
        return uploadedFiles + "," + uploadedBytes;
    }

    @Override
    public void fromCsvString(String csv){
        if(csv == null || csv.isEmpty()) return;
        String[] parts = csv.split(",");
        if(parts.length == 2){
            this.uploadedFiles = Long.parseLong(parts[0]);
            this.uploadedBytes = Long.parseLong(parts[1]);
        }
    }


}
