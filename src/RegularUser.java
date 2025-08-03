public class RegularUser extends User {

    public RegularUser(String username, String passwordHash, String sharedDirectory){
        super(username, passwordHash, sharedDirectory);
    }

    @Override
    public  boolean isAdmin(){
        return false;
    }



}
