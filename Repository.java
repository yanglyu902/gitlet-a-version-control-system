package gitlet;
import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.text.SimpleDateFormat;

import static gitlet.Utils.*;

/** Represents a gitlet repository.
 *  It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author Yang Lyu
 */
public class Repository {
    /**
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The staging area */
    public static final File STAGING_DIR = join(GITLET_DIR, "staging");
    /** The removal area */
    public static final File REMOVAL_DIR = join(GITLET_DIR, "removal");
    /** folder of all commits */
    public static final File COMMIT_DIR = join(GITLET_DIR, "commits");
    /** folder of all Blobs */
    public static final File BLOB_DIR = join(GITLET_DIR, "blobs");
    /** folder of all pointers */
    public static final File POINTER_DIR = join(GITLET_DIR, "pointers");


    /**
     * Initialize repo. Create all directories.
     */
    public Repository() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system "
                    + "already exists in the current directory.");
        } else {
            // initialize directories
            GITLET_DIR.mkdir();
            COMMIT_DIR.mkdir();
            STAGING_DIR.mkdir();
            REMOVAL_DIR.mkdir();
            BLOB_DIR.mkdir();
            POINTER_DIR.mkdir();

            // make first empty commit. Serialize, get hash, and store using hash name.
            Commit firstCommit = new Commit();
            String hashSerializedFirstCommit = getHashBySerializingCommit(firstCommit);
            File firstCommitFile = join(COMMIT_DIR, hashSerializedFirstCommit);
            Utils.writeObject(firstCommitFile, firstCommit);

            // create and store pointer object (contains hashmap of branches)
            Pointer tmp = new Pointer();
            Utils.writeObject(Utils.join(POINTER_DIR, "pointer"), tmp);

            // Set master branch. head is pointing at master initially.
            setPointer("master", hashSerializedFirstCommit);
            setPointer("head", "master");
        }
    }

    /**
     * Add file to staging area
     */
    public static void add(String filename) {
        // in removal? move back.
        File inRemoval = join(REMOVAL_DIR, filename);
        if (inRemoval.exists()) {
            copyPaste(inRemoval, Utils.join(CWD, filename));
            inRemoval.delete();
            return;
        }

        // file exist in CWD?
        File addFile = join(CWD, filename);
        if (!addFile.exists()) {
            System.out.println("File does not exist.");
            return;
        }

        // find currently tracking files in newest commit
        HashMap<String, String> trackedFiles = getTrackedFilesFromCommitPtr("head");
        String hashOfTrackedFile = trackedFiles.get(filename);

        // find hash of this to-be-added file
        String hashOfAddFile = getHashBySerializingFile(addFile);

        // if nothing changed from newest commit, do not stage, and remove if it's in staging area.
        // could file already be in staging area?
        File fileInStaging = Utils.join(STAGING_DIR, filename);
        if (hashOfTrackedFile != null && hashOfTrackedFile.equals(hashOfAddFile)) {
            if (fileInStaging.exists()) {
                fileInStaging.delete();
            }
        } else { // file either changed or DNE in newest commit. overwrite staging area.
            copyPaste(addFile, fileInStaging);
        }
    }

    /**
     * Remove files either from staging are or current commit.
     */
    public static void rm(String filename) {
        boolean foundInStaging = false;
        boolean foundInCommit = false;

        // if file exist in staging area, delete.
        File inStaging = join(STAGING_DIR, filename);
        if (inStaging.exists()) {
            inStaging.delete();
            foundInStaging = true;
        }

        // if exist in newest commit, ready for removal. Delete file in CWD.
        HashMap<String, String> trackedFiles = getTrackedFilesFromCommitPtr("head");
        for (String key : trackedFiles.keySet()) {
            if (key.equals(filename)) {
                // copy blob to removal area
                String hash = trackedFiles.get(key);
                copyPaste(Utils.join(BLOB_DIR, hash), Utils.join(REMOVAL_DIR, filename));
                // delete from CWD
                File inCWD = join(CWD, filename);
                if (inCWD.exists()) {
                    Utils.restrictedDelete(inCWD);
                }
                foundInCommit = true;
                break;
            }
        }

        if (!foundInStaging && !foundInCommit) {
            System.out.println("No reason to remove the file.");
        }
    }

    /**
     * Make commit
     */
    public static void commit(String message) {
        // create new commit; parent is previous head.
        String parentCommitHash = getCommitIDFromPtr("head");
        Commit newCommit = new Commit(message, parentCommitHash);

        // anything in staging area?
        List<String> stagedFiles = Utils.plainFilenamesIn(STAGING_DIR); // staging area
        List<String> removalFiles = Utils.plainFilenamesIn(REMOVAL_DIR); // removal area
        if (stagedFiles.size() == 0 && removalFiles.size() == 0) { // nothing to add/remove.
            System.out.println("No changes added to the commit.");
            return;
        }

        // copy tracked files ((keys, val) hashmap pairs) from old commit to new one.
        HashMap<String, String> trackedFiles = getTrackedFilesFromCommitPtr("head");
        for (String key : trackedFiles.keySet()) {
            // if the tracked file is in removal area, untrack. Delete it from removal area.
            if (removalFiles.contains(key)) {
                File tmp = Utils.join(REMOVAL_DIR, key);
                tmp.delete();
                continue;
            }
            newCommit.trackNewFile(key, trackedFiles.get(key));
        }

        // save staged files to blob, add to new commit
        for (String f : stagedFiles) {
            File toAdd = Utils.join(STAGING_DIR, f);
            String hash = getHashBySerializingFile(toAdd);
            File toBlob = Utils.join(BLOB_DIR, hash);

            copyPaste(toAdd, toBlob); // copy from staging to blob
            newCommit.trackNewFile(f, hash); // track this new file
            toAdd.delete(); // clear staging area
        }

        // store new commit
        String hashSerializedNewCommit = getHashBySerializingCommit(newCommit);
        File pathCommit = Utils.join(COMMIT_DIR, hashSerializedNewCommit);
        Utils.writeObject(pathCommit, newCommit);

        // advance current branch point to new commit. Also advance head.
        String currBranch = getCurrentBranchName();
        setPointer(currBranch, hashSerializedNewCommit);
        setPointer("head", currBranch);
    }

    /**
     * checkout -- [file name]
     * checkout file from HEAD
     */
    public static void checkoutFile(String filename) {
        HashMap<String, String> trackedFiles = getTrackedFilesFromCommitPtr("head");
        String hash = trackedFiles.get(filename);
        checkoutHelper(filename, hash);
    }

    /**
     * checkout [commit id] -- [file name]
     */
    public static void checkoutFromCommit(String commitID, String filename) {
        Commit commit = getCommitFromID(commitID);

        if (commit == null) {
            System.out.println("No commit with that id exists.");
            return;
        }

        HashMap<String, String> trackedFiles = commit.getTrackedFiles();
        String hash = trackedFiles.get(filename);
        checkoutHelper(filename, hash);
    }

    /**
     * Helper for copying file with hash from blob to cwd (renamed).
     */
    private static void checkoutHelper(String filename, String hash) {
        // if found file in commit of corresponding hashcode, copy to CWD.
        if (hash != null) {
            File orig = Utils.join(BLOB_DIR, hash);
            File dest = Utils.join(CWD, filename);
            copyPaste(orig, dest); // overwrite!
        } else {
            System.out.println("File does not exist in that commit.");
        }
    }

    /**
     * checkout [branch name]
     */
    public static void checkoutBranch(String branch) {

        HashMap<String, String> p = getPointer();
        if (!p.containsKey(branch)) {
            System.out.println("No such branch exists.");
            return;
        }

        if (getCurrentBranchName().equals(branch)) {
            System.out.println("No need to checkout the current branch.");
            return;
        }


        HashMap<String, String> newTrackedFiles = getTrackedFilesFromCommitPtr(branch);

        checkoutBranchHelper(newTrackedFiles);

        setPointer("head", branch);
    }

    /**
     * is the file f in CWD being tracked in staging area?
     */
    private static boolean isTrackingInStaging(String f) {
        List<String> inStaging = Utils.plainFilenamesIn(STAGING_DIR);
        if (inStaging.size() == 0) { // empty staging area
            return false;
        } else if (!inStaging.contains(f)) { // staging area has files but not f
            return false;
        } else { // staging area has f, but content has changed
            File inCWD = Utils.join(CWD, f);
            File inSTAGING = Utils.join(STAGING_DIR, f);
            String hashCWD = getHashBySerializingFile(inCWD);
            String hashSTAGING = getHashBySerializingFile(inSTAGING);
            if (!hashCWD.equals(hashSTAGING)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checkout branch helper: update CWD!
     */
    public static boolean checkoutBranchHelper(HashMap<String, String> newTrackedFiles) {
        HashMap<String, String> oldTrackedFiles = getTrackedFilesFromCommitPtr("head");

        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);

        // if untracked file exist:
        if (cwdFiles.size() != 0) {
            for (String f : cwdFiles) {

                String oldCommitHash = oldTrackedFiles.get(f);
                String targetCommitHash = newTrackedFiles.get(f);
                String cwdHash = getHashBySerializingFile(Utils.join(CWD, f)); // never null

                if (targetCommitHash != null) { // part of target commit
                    if (oldCommitHash == null) { // not part of current commit
                        if (!targetCommitHash.equals(cwdHash)) { // will overwrite
                            if (!isTrackingInStaging(f)) { // not "tracked" in staging area
                                System.out.println("There is an untracked file in the way;"
                                        + " delete it, or add and commit it first.");
                                System.exit(0);
                            }
                        }
                    }
                }
            }
        }

        // old: A, B. new: A', C. Should: update A, delete B (alert!), add C.
        for (String key : oldTrackedFiles.keySet()) {
            if (newTrackedFiles.containsKey(key)) { // A -> A', remove B
                String oldCommitHash = oldTrackedFiles.get(key);
                String cwdHash = newTrackedFiles.get(key);
                if (!oldCommitHash.equals(cwdHash)) { // should update
                    copyPaste(Utils.join(BLOB_DIR, cwdHash), Utils.join(CWD, key));
                }
            } else { // delete B
                Utils.join(CWD, key).delete();
            }
        }

        for (String key : newTrackedFiles.keySet()) {
            if (!oldTrackedFiles.containsKey(key)) { // C -> C
                String cwdHash = newTrackedFiles.get(key);
                copyPaste(Utils.join(BLOB_DIR, cwdHash), Utils.join(CWD, key));
            }
        }

        // clean up staging and removal area.
        cleanDir(STAGING_DIR);
        cleanDir(REMOVAL_DIR);
        return true;
    }

    /**
     * Basically do checkout-branch on a commit. Update all files and pointers.
     */
    public static void reset(String commitID) {
        File commitFile = Utils.join(COMMIT_DIR, commitID);
        if (!commitFile.exists()) {
            System.out.println("No commit with that id exists.");
            return;
        }

        Commit commit = getCommitFromID(commitID);
        HashMap<String, String> newTrackedFiles = commit.getTrackedFiles();

        checkoutBranchHelper(newTrackedFiles);

        // move both branch and head pointer back. // TODO? or not?
        setPointer(getCurrentBranchName(), commitID);
        setPointer("head", getCurrentBranchName());
//        setPointer("head", commitID);
    }


    /**
     * log helper: print!
     */
    public static void logHelper(Commit commit, String currCommitHash) {
        Date time = commit.getTimeStamp();

        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd hh:mm:ss yyyy");
        sdf.setTimeZone(TimeZone.getTimeZone("PST"));
        String date = sdf.format(time);

        System.out.println("===");
        System.out.println("commit " + currCommitHash);

        String message = commit.getMessage();
        if (message.contains("Merged ")) {
            String[] words = message.split(" ");
            String head = words[1];
            String other = words[3].split("\\.")[0];
            String headHash = getShortCommitID(getCommitIDFromPtr(head), 7);
            String otherHash = getShortCommitID(getCommitIDFromPtr(other), 7);
            System.out.println("Merge: " + headHash + " " + otherHash);
        }

        System.out.println("Date: " + date + " -0800");
        System.out.println(commit.getMessage());
        System.out.println();
    }

    /**
     * make log
     */
    public static void log() {
        String currCommitHash = getCommitIDFromPtr("head");

        // start from HEAD, move back until initial commit and print messages along the way.
        while (currCommitHash != null) {
            Commit commit = getCommitFromID(currCommitHash);
            logHelper(commit, currCommitHash);
            currCommitHash = commit.getParent();
        }
    }

    /**
     * make global log of all commits
     */
    public static void logGlobal() {

        List<String> allCommits = Utils.plainFilenamesIn(COMMIT_DIR); // commit area

        for (String id : allCommits) {
            Commit commit = getCommitFromID(id);
            logHelper(commit, id);
        }
    }

    /**
     * Find all commits with given messagae
     */
    public static void find(String target) {
        boolean found = false;
        List<String> allCommits = Utils.plainFilenamesIn(COMMIT_DIR); // commit area

        for (String id : allCommits) {
            Commit commit = getCommitFromID(id);
            String msg = commit.getMessage();

            if (msg.equals(target)) {
                System.out.println(id);
                found = true;
            }
        }

        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    /**
     * status! what's the branch? what are staged, for removal, and changed?
     */
    public static void status() {

        List<String> allRemoval = Utils.plainFilenamesIn(REMOVAL_DIR);
        List<String> allStaged = Utils.plainFilenamesIn(STAGING_DIR);
        HashMap<String, String> p = getPointer();
        ArrayList<String> allBranches = new ArrayList<>();
        for (String branch : p.keySet()) {
            if (!branch.equals("head")) {
                allBranches.add(branch);
            }
        }
        Collections.sort(allBranches);

        System.out.println("=== Branches ===");
        String currBranch = getCurrentBranchName();
        for (String branch : allBranches) {
            if (branch.equals(currBranch)) {
                System.out.print("*");
            }
            System.out.println(branch);
        }
        System.out.println();

        System.out.println("=== Staged Files ===");
        if (allStaged.size() > 0) {
            Collections.sort(allStaged);
            for (String i : allStaged) {
                System.out.println(i);
            }
        }
        System.out.println();

        System.out.println("=== Removed Files ===");
        if (allRemoval.size() > 0) {
            Collections.sort(allRemoval);
            for (String i : allRemoval) {
                System.out.println(i);
            }
        }
        System.out.println();

        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println();
        System.out.println("=== Untracked Files === ");
        System.out.println();
    }


    /**
     * Build branch. Simply create pointer -> current head commit
     */
    public static void branch(String name) {
        HashMap<String, String> map = getPointer();
        for (String key : map.keySet()) {
            if (name.equals(key)) {
                System.out.println("A branch with that name already exists.");
                return;
            }
        }

        String headCommit = getCommitIDFromPtr("head");
        setPointer(name, headCommit);
    }

    /**
     * Delete branch.
     */
    public static void removeBranch(String branch) {
        if (branch.equals(getCurrentBranchName())) {
            System.out.println("Cannot remove the current branch.");
            return;
        }

        Pointer p = Utils.readObject(Utils.join(POINTER_DIR, "pointer"), Pointer.class);
        for (String key : p.pointers.keySet()) {
            if (key.equals(branch)) {
                p.pointers.remove(key);
                Utils.writeObject(Utils.join(POINTER_DIR, "pointer"), p);
                System.exit(0);
            }
        }

        System.out.println("A branch with that name does not exist.");
    }

    /**
     * Merge alerts
     */
    private static void mergeAlerts(String branch) {
        List<String> stagingArea = Utils.plainFilenamesIn(STAGING_DIR);
        List<String> removalArea = Utils.plainFilenamesIn(REMOVAL_DIR);
        if (stagingArea.size() != 0 || removalArea.size() != 0) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }

        HashMap<String, String> pointers = getPointer();
        if (!pointers.containsKey(branch)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }

        if (getCurrentBranchName().equals(branch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }

        HashMap<String, String> oldTrackedFiles = getTrackedFilesFromCommitPtr("head");
        HashMap<String, String> newTrackedFiles = getTrackedFilesFromCommitPtr(branch);

        List<String> cwdFiles = Utils.plainFilenamesIn(CWD);

        // if untracked file exist:
        if (cwdFiles.size() != 0) {
            for (String f : cwdFiles) {

                String oldCommitHash = oldTrackedFiles.get(f);
                String targetCommitHash = newTrackedFiles.get(f);
                String cwdHash = getHashBySerializingFile(Utils.join(CWD, f)); // never null

                if (targetCommitHash != null) { // part of target commit
                    if (oldCommitHash == null) { // not part of current commit
                        if (!targetCommitHash.equals(cwdHash)) { // will overwrite
                            if (!isTrackingInStaging(f)) { // not "tracked" in staging area
                                System.out.println("There is an untracked file in the way;"
                                        + " delete it, or add and commit it first.");
                                System.exit(0);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Merge branch.
     */
    public static void merge(String branch) {
        mergeAlerts(branch); // first check errors!

        String splitPoint = findSplitPointBFS(branch);

        if (splitPoint.equals(getCommitIDFromPtr(branch))) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }
        if (splitPoint.equals(getCommitIDFromPtr("head"))) {
            checkoutBranch(branch);
            System.out.println("Current branch fast-forwarded.");
            System.exit(0);
        }

        HashMap<String, String> splitTrackedFiles = getCommitFromID(splitPoint).getTrackedFiles();
        HashMap<String, String> headTrackedFiles = getTrackedFilesFromCommitPtr("head");
        HashMap<String, String> branchTrackedFiles = getTrackedFilesFromCommitPtr(branch);

        HashSet<String> allFiles = new HashSet<>();
        allFiles.addAll(splitTrackedFiles.keySet());
        allFiles.addAll(headTrackedFiles.keySet());
        allFiles.addAll(branchTrackedFiles.keySet());

        boolean conflicted = false;
        for (String f : allFiles) {

            // get files from split point, branch, and head. Null if DNE.
            File fileAtSplit = null;
            File fileAtHead = null;
            File fileAtBranch = null;
            if (splitTrackedFiles.get(f) != null) {
                fileAtSplit = Utils.join(BLOB_DIR, splitTrackedFiles.get(f));
            }
            if (headTrackedFiles.get(f) != null) {
                fileAtHead = Utils.join(BLOB_DIR, headTrackedFiles.get(f));
            }
            if (branchTrackedFiles.get(f) != null) {
                fileAtBranch = Utils.join(BLOB_DIR, branchTrackedFiles.get(f));
            }

            // wraps several conditions into one helper method
            if (mergeConditionHelper(fileAtSplit, fileAtHead, fileAtBranch, f)) {
                continue;
            }

            // deal with conflict!
            if (fileAtSplit != null
                    && !compareFileToFile(fileAtHead, fileAtSplit)
                    && !compareFileToFile(fileAtBranch, fileAtSplit)
                    && !compareFileToFile(fileAtHead, fileAtBranch)) { // conflict!
                String contentAtHead, contentAtBranch;
                if (fileAtHead == null) {
                    contentAtHead = "";
                } else {
                    contentAtHead = readContentsAsString(fileAtHead);
                }
                if (fileAtBranch == null) {
                    contentAtBranch = "";
                } else {
                    contentAtBranch = readContentsAsString(fileAtBranch);
                }

                String toSave = "<<<<<<< HEAD\n";
                toSave += contentAtHead;
                toSave += "=======\n";
                toSave += contentAtBranch;
                toSave += ">>>>>>>\n";
                Utils.writeContents(Utils.join(CWD, f), toSave);
                add(f);
                conflicted = true;
            }
        }

        commit("Merged " + branch + " into " + getCurrentBranchName() + ".");

        if (conflicted) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /**
     * handle different conditions 
     */
    private static boolean mergeConditionHelper(File fileAtSplit,
                                        File fileAtHead, File fileAtBranch, String f) {

        // splitPoint file exists
        if (fileAtSplit != null && fileAtHead != null && fileAtBranch != null
                && compareFileToFile(fileAtHead, fileAtSplit)
                && !compareFileToFile(fileAtBranch, fileAtSplit)) {
            copyPaste(fileAtBranch, join(CWD, f));
            add(f);
            return true;
        }
        if (fileAtSplit != null
                && !compareFileToFile(fileAtHead, fileAtSplit)
                && compareFileToFile(fileAtBranch, fileAtSplit)) {
            return true;
        }
        if (fileAtSplit != null
                && !compareFileToFile(fileAtHead, fileAtSplit)
                && !compareFileToFile(fileAtBranch, fileAtSplit)
                && compareFileToFile(fileAtHead, fileAtBranch)) { // both modified in same way
            return true;
        }
        if (fileAtSplit != null
                && compareFileToFile(fileAtSplit, fileAtHead)
                && fileAtBranch == null) {
            rm(f);
            return true;
        }
        if (fileAtSplit != null
                && fileAtHead == null
                && compareFileToFile(fileAtSplit, fileAtBranch)) {
            return true;
        }

        // split is null
        if (fileAtSplit == null && fileAtHead == null && fileAtBranch != null) {
            copyPaste(fileAtBranch, join(CWD, f));
            add(f);
            return true;
        }
        if (fileAtSplit == null && fileAtHead != null && fileAtBranch == null) {
            return true;
        }

        return false;
    }

    /**
     * Find latest split point for (head, branch).
     */
    private static String findSplitPointBFS(String branch) {

        ArrayList<String> listA = doBFS("head");
        ArrayList<String> listB = doBFS(branch);

        for (String i : listA) {
            if (listB.contains(i)) {
                return i;
            }
        }

        return null;
    }

    /**
     * do BFS and store all parents in list
     */
    private static ArrayList<String> doBFS(String branch) {
        ArrayList<String> L = new ArrayList<>();
        Queue<Commit> queue = new LinkedList<>();

        L.add(getCommitIDFromPtr(branch));
        queue.add(getCommitFromPtr(branch));

        while (!queue.isEmpty()) {
            Commit first = queue.remove();
            if (hasTwoParents(first)) {
                ArrayList<String> bothParents = getBothParents(first);
                String a = bothParents.get(0);
                String b = bothParents.get(1);
                if (!L.contains(a)) {
                    L.add(a);
                    queue.add(getCommitFromID(a));
                }
                if (!L.contains(b)) {
                    L.add(b);
                    queue.add(getCommitFromID(b));
                }
            } else {
                String p = first.getParent();
                if (p != null && !L.contains(p)) {
                    L.add(p);
                    queue.add(getCommitFromID(p));
                }
            }
        }
        return L;
    }

    // ============================= HELPER METHODS ============================= //

    /**
     * Implement pointer HashMap that stores HEAD, MASTER, NEWBRANCH, etc.
     */
    private static class Pointer implements Serializable {
        /** folder of all Pointers */
        HashMap<String, String> pointers;
        Pointer() {
            pointers = new HashMap<>();
        }
    }

    /**
     * Helper: get commit hashcode of HEAD, MASTER, etc from serialized pointers
     */
    private static HashMap<String, String> getPointer() {
        File pointerDir = Utils.join(POINTER_DIR, "pointer");
        return readObject(pointerDir, Pointer.class).pointers;
    }

    /**
     * Set hashcode of commits that pointer point to. Head should point to branch name.
     */
    private static void setPointer(String branch, String hashcode) {

        File pointerDir = Utils.join(POINTER_DIR, "pointer");
        Pointer p = Utils.readObject(pointerDir, Pointer.class);

        p.pointers.put(branch, hashcode); // if branch exist, overwrite
        Utils.writeObject(Utils.join(POINTER_DIR, "pointer"), p);
    }

    /**
     * What is the branch that coincide with HEAD?
     */
    private static String getCurrentBranchName() {
        File pointerDir = Utils.join(POINTER_DIR, "pointer");
        Pointer p = Utils.readObject(pointerDir, Pointer.class);
        return p.pointers.get("head");
    }

    /**
     * Helper: get commit hashcode of HEAD, MASTER, etc from serialized pointers
     */
    private static String getCommitIDFromPtr(String ptr) {
        File pointerDir = Utils.join(POINTER_DIR, "pointer");
        Pointer p = Utils.readObject(pointerDir, Pointer.class);
        if (ptr.equals("head")) { // head is pointing at branch name (master, etc)!
            String currBranch = p.pointers.get("head");
            return p.pointers.get(currBranch);
        } else {
            return p.pointers.get(ptr);
        }
    }

    /**
     * Return the pointed (HEAD, MASTER) commit.
     */
    private static Commit getCommitFromPtr(String ptr) {
        String position = getCommitIDFromPtr(ptr);
        return Utils.readObject(Utils.join(COMMIT_DIR, position), Commit.class);
    }

    /**
     * get commit from a commit hashcode (id). Need to handle short uid case.
     */
    private static Commit getCommitFromID(String commitID) {
        int L = 40;
        if (commitID.length() < 40) { // short id!
            L = commitID.length();
        }

        List<String> allCommits = Utils.plainFilenamesIn(COMMIT_DIR);
        for (String name : allCommits) {
            String shortID = getShortCommitID(name, L);
            if (commitID.equals(shortID)) {
                return Utils.readObject(Utils.join(COMMIT_DIR, name), Commit.class);
            }
        }

        return null;
    }

    /**
     * Return hashmap of tracked files for the pointed (HEAD, MASTER) commit.
     */
    private static HashMap<String, String> getTrackedFilesFromCommitPtr(String ptr) {
        Commit newestCommit = getCommitFromPtr(ptr);
        return newestCommit.getTrackedFiles();
    }

    /**
     * Get hashcode of a commit
     */
    private static String getHashBySerializingCommit(Serializable f) {
        byte[] serialized = Utils.serialize(f);
        String hashcode = Utils.sha1(serialized);
        return hashcode;
    }

    /**
     * Get hashcode of a file (to be committed)
     */
    private static String getHashBySerializingFile(File f) {
        byte[] serialized = Utils.readContents(f);
        String hashcode = Utils.sha1(serialized);
        return hashcode;
    }

    /**
     * convert long commit id to short
     */
    private static String getShortCommitID(String commitID, int length) {
        return commitID.substring(0, length);
    }

    /**
     * Copy and paste files from origin to destination.
     */
    private static void copyPaste(File origin, File destination) {
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(origin));
            OutputStream out = new BufferedOutputStream(new FileOutputStream(destination));

            byte[] buffer = new byte[1024];
            int lengthRead;
            while ((lengthRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, lengthRead);
                out.flush();
            }
        } catch (IOException e) {
            System.out.println("I/O copy error occurred.");
            e.printStackTrace();
        }
    }

    /**
     * Helper: clean a directory.
     */
    private static void cleanDir(File f) {
        List<String> toClear = Utils.plainFilenamesIn(f);
        if (toClear.size() != 0) {
            for (String i : toClear) {
                Utils.join(f, i).delete();
            }
        }
    }

    /**
     * Compare if two files are different. null==null.
     */
    private static boolean compareFileToFile(File a, File b) {
        if (a == null && b == null) {
            return true;
        }

        if ((a == null && b != null) || (a != null && b == null)) {
            return false;
        }

        String hashA = getHashBySerializingFile(a);
        String hashB = getHashBySerializingFile(b);
        return hashA.equals(hashB);
    }

    /**
     * get both parent hash from commit
     */
    private static ArrayList<String> getBothParents(Commit commit) {
        ArrayList<String> L = new ArrayList<>();
        if (hasTwoParents(commit)) {
            String message = commit.getMessage();
            String[] words = message.split(" ");
            String one = words[1]; // head
            String other = words[3].split("\\.")[0];
            String oneHash = getCommitIDFromPtr(one);
            String otherHash = getCommitIDFromPtr(other);
            L.add(oneHash); // head
            L.add(otherHash);
        }
        return L;
    }

    /**
     * check if commit has two parents
     */
    private static boolean hasTwoParents(Commit commit) {
        String message = commit.getMessage();
        return (message.contains("Merged "));
    }

}
