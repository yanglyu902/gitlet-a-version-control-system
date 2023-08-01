package gitlet;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;

/** Represents a gitlet commit object.
 *  Stores message, time, parent hashcode, and a hashmap(file name, hashcode).
 *  @author Yang Lyu
 */
public class Commit implements Serializable, Dumpable {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /**
     * The message of this Commit.
     */
    private String message;
    private Date timeStamp;
    private String parent; // hashcode for parent commit
    private HashMap<String, String> trackedFiles; // map e.g. hello.txt to HASHCODE in blob folder

    /**
     * Initial commit; only once.
     */
    public Commit() {
        this.message = "initial commit";
        this.timeStamp = new Date(0);
        this.parent = null;
        this.trackedFiles = new HashMap<>();
    }

    /**
     * An ordinary commit
     */
    public Commit(String message, String parent) {
        this.message = message;
        this.timeStamp = new Date();
        this.parent = parent;
        this.trackedFiles = new HashMap<>();
    }

    /**
     * set what files to track
     */
    public void trackNewFile(String name, String hashcode) {
        trackedFiles.put(name, hashcode);
    }

    /**
     * return instance variables
     */
    public HashMap<String, String> getTrackedFiles() {
        return this.trackedFiles;
    }

    /**
     * return instance variables
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * return instance variables
     */
    public Date getTimeStamp() {
        return this.timeStamp;
    }

    /**
     * return instance variables
     */
    public String getParent() {
        return this.parent;
    }

    @Override
    public void dump() {
        System.out.println("Parenet: " + getParent());
        System.out.println("Message: " + getMessage());
        System.out.println("contains files: ");
        for (String key : this.trackedFiles.keySet()) {
            System.out.print(key + ", id: ");
            System.out.println(this.trackedFiles.get(key));
        }
    }
}
