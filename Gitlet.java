import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

/* ===================================================================
 * ~~~~~~~~~~~~~~~~~~~~~~~~~~ GITLET ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 * ===================================================================
 */

public class Gitlet implements Serializable {
	private HashSet<File> untracking;
	private HashSet<File> staged;
	private HashSet<File> toPropagate; // used only for rebase
	private HashMap<String, ArrayList<Commit>> history;
	private HashMap<String, Commit> branches;
	private HashMap<Integer, Commit> commitIDMap;
	private String currentBranch;
	private int currentID;
	private boolean conflicted; // used only for merge

	/**
	 * Creates a new Gitlet object. Keeps track of many collections involving
	 * files and commits. history and commitIDMap represent two different ways
	 * to access a commit using its info. toPropagate is used in rebase to keep
	 * track of files that still need to be propagated. commitID increments
	 * after each commit to ensure that every commit has a unique ID number.
	 */
	public Gitlet() {
		untracking = new HashSet<File>();
		staged = new HashSet<File>();
		history = new HashMap<String, ArrayList<Commit>>();
		branches = new HashMap<String, Commit>();
		commitIDMap = new HashMap<Integer, Commit>();
		currentBranch = "master";
		currentID = 0;
	}

	/*
	 * ===================================================================
	 * ~~~~~~~~~~~~~~~~~~~~~ COMMAND METHODS ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	 * ===================================================================
	 */

	/**
	 * Initializes the Gitlet system in the current directory. Creates a new
	 * .gitlet folder with a nested staging area folder only if one does not
	 * already exist. Also creates the first branch, called "master", and points
	 * it to the initial commit. Finally, makes a commit_0 folder, for the sake
	 * of consistency.
	 */
	public void initCommand() {
		if (new File(".gitlet").exists()) {
			System.out.println("A gitlet version control system "
					+ "already exists in the current directory.");
		} else {
			new File(".gitlet/staging_area/").mkdirs();
			branches.put("master", new Commit("initial commit"));
			new File(".gitlet/commit_0").mkdir();
		}
	}

	/**
	 * Checks if the file exists in the current directory. If the file is
	 * currently marked for untracking, unmark it. Otherwise, stage the file.
	 * 
	 * @param fileName
	 *            the pathname of the file to be staged.
	 */
	public void addCommand(String fileName) {
		File toBeAdded = new File(fileName);
		if (!toBeAdded.exists()) {
			System.out.print("File does not exist.");
		} else if (untracking.contains(toBeAdded)) {
			untracking.remove(toBeAdded);
		} else {
			copyFiles(fileName, ".gitlet/staging_area/" + fileName);
			staged.add(toBeAdded);
		}
	}

	/**
	 * Checks that the conditions for making a new commit are satisfied. If so,
	 * creates a new commit with the given message, whose parent is the commit
	 * at the head of the current branch. Then, update the current branch and
	 * save the staged backup files in the appropriate location. Ends a
	 * conflicted state, if there is one. Parts of this command are accomplished
	 * in the Commit constructor.
	 * 
	 * @param message
	 *            the message to be associated with the new commit.
	 */
	public void commitCommand(String message) {
		if (message == "") {
			System.out.println("Please enter a commit message.");
		} else if (staged.isEmpty() && untracking.isEmpty()) {
			System.out.println("No changes added to the commit.");
		} else {
			Commit newCommit = new Commit(message, branches.get(currentBranch));
			for (File file : staged) {
				copyFiles(file.getPath(), ".gitlet/commit_" + newCommit.id
						+ "/" + file.getPath());
			}
			clearStage();
			conflicted = false;
		}
	}

	/**
	 * Marks a file for untracking, only if it is being tracked and has not yet
	 * been staged. If it has been staged, unstage it. Untracking takes effect
	 * once the user commits.
	 * 
	 * @param fileName
	 *            pathname of the file to be removed.
	 */
	public void rmCommand(String fileName) {
		File toBeRemoved = new File(fileName);
		if (staged.contains(toBeRemoved)) {
			Path firstFolderName = toBeRemoved.toPath().getName(0);
			File staged_file = new File(".gitlet/staging_area/"
					+ firstFolderName);
			deleteFile(staged_file);
			staged.remove(toBeRemoved);
		} else if (branches.get(currentBranch).tracking
				.containsKey(toBeRemoved)) {
			untracking.add(toBeRemoved);
		} else {
			System.out.println("No reason to remove the file.");
		}
	}

	/**
	 * Starting at the head of the current branch, print the relevant info of
	 * every commit in the starting commit's history. See reportInfo in COMMIT
	 * CLASS.
	 */
	public void logCommand() {
		for (Commit c = branches.get(currentBranch); c != null; c = c.parent) {
			c.reportInfo();
		}
	}

	/**
	 * Similar to log, except print the relevant info of every commit ever made.
	 * No particular order is guaranteed.
	 */
	public void globalLogCommand() {
		for (String commitString : history.keySet()) {
			for (Commit commit : history.get(commitString)) {
				commit.reportInfo();
			}
		}
	}

	/**
	 * Find and print out the ID numbers of all commits with the specified
	 * message. Searches the history of commits for this info.
	 * 
	 * @param message
	 *            the message to search for.
	 */
	public void findCommand(String message) {
		if (history.containsKey(message)) {
			for (Commit commit : history.get(message)) {
				System.out.println("Commit " + commit.id);
			}
		} else {
			System.out.println("Found no commit with that message.");
		}
	}

	/**
	 * Displays a table listing all created branches, all staged files, and all
	 * files that have been marked for untracking.
	 */
	public void statusCommand() {
		System.out.println("=== Branches ===");
		for (String branch : branches.keySet()) {
			if (branch.equals(currentBranch)) {
				System.out.println("*" + branch);
			} else {
				System.out.println(branch);
			}
		}
		System.out.println();
		System.out.println("=== Staged Files ===");
		for (File file : staged) {
			System.out.println(file.getPath());
		}
		System.out.println();
		System.out.println("=== File Marked for Untracking ===");
		for (File untracked : untracking) {
			System.out.println(untracked.getPath());
		}
	}

	/**
	 * Checks out the specified file from the head of the current branch, if it
	 * is being tracked. Uses the checkout helper to accomplish this (see
	 * UTILITY METHODS).
	 * 
	 * @param fileName
	 *            the pathname of the file to checkout.
	 */
	public void checkoutFileCommand(String fileName) {
		File checkedOut = new File(fileName);
		Commit currentCommit = branches.get(currentBranch);
		if (!currentCommit.tracking.containsKey(checkedOut)) {
			System.out.println("File does not exist in the most recent "
					+ "commit, or no such branch exists.");
		} else {
			checkoutHelper(currentCommit, fileName);
		}
	}

	/**
	 * Finds the commit with the specified ID number, then uses the
	 * checkoutHelper to check out the specified file, if it is being tracked.
	 * 
	 * @param commitID
	 *            ID number of the commit from which to check out.
	 * @param fileName
	 *            name of the file to check out.
	 */
	public void checkoutIDCommand(int commitID, String fileName) {
		if (!commitIDMap.containsKey(commitID)) {
			System.out.println("No commit with that id exists.");
		} else {
			Commit desiredCommit = commitIDMap.get(commitID);
			File checkedOut = new File(fileName);
			if (!desiredCommit.tracking.containsKey(checkedOut)) {
				System.out.println("File does not exist in that commit.");
			} else {
				checkoutHelper(desiredCommit, fileName);
			}
		}
	}

	/**
	 * Uses the checkout helper to check out all files from the commit at the
	 * head of the given branch, if the system is not in a conflicted state.
	 * Changes the current branch to the given branch.
	 * 
	 * @param branchName
	 *            name of the branch whose head contains the commit to be
	 *            checked out.
	 */
	public void checkoutBranchCommand(String branchName) {
		if (conflicted) {
			System.out
					.println("Cannot do this command until the merge conflict has been resolved.");
		} else if (branchName.equals(currentBranch)) {
			System.out.println("No need to checkout the current branch.");
		} else {
			Commit desiredCommit = branches.get(branchName);
			for (File file : desiredCommit.tracking.keySet()) {
				checkoutHelper(desiredCommit, file.getPath());
			}
			currentBranch = branchName;
		}
	}

	/**
	 * Creates a new branch with the specified name, if not conflicted and if
	 * the branch does not already exist. The new branch will point to the
	 * current head commit.
	 * 
	 * @param name
	 *            name of the branch to be created.
	 */
	public void branchCommand(String name) {
		if (conflicted) {
			System.out
					.println("Cannot do this command until the merge conflict has been resolved.");
		} else if (branches.containsKey(name)) {
			System.out.println("A branch with that name already exists.");
		} else {
			branches.put(name, branches.get(currentBranch));
		}
	}

	/**
	 * Simply removes the branch from the collection of branches, if not
	 * conflicted. No commits are removed.
	 * 
	 * @param name
	 *            name of the branch to be removed.
	 */
	public void rmBranchCommand(String name) {
		if (conflicted) {
			System.out
					.println("Cannot do this command until the merge confliect has been resolved.");
		} else if (!branches.containsKey(name)) {
			System.out.println("A branch with that name does not exist.");
		} else if (name.equals(currentBranch)) {
			System.out.println("Cannot remove the current branch.");
		} else {
			branches.remove(name);
		}
	}

	/**
	 * Resets the current branch to the commit with the specified ID number, if
	 * not conflicted. Also uses the checkout helper to checkout the files from
	 * that commit.
	 * 
	 * @param commitID
	 *            ID number of the commit to checkout and reset to.
	 */
	public void resetCommand(int commitID) {
		// checkout the files from the given commit
		if (conflicted) {
			System.out
					.println("Cannot do this command until the merge conflict has been resolved.");
		} else if (!commitIDMap.containsKey(commitID)) {
			System.out.print("No commit with that id exists.");
		} else {
			Commit desiredCommit = commitIDMap.get(commitID);
			for (File file : desiredCommit.tracking.keySet()) {
				checkoutHelper(desiredCommit, file.getPath());
			}
			branches.put(currentBranch, desiredCommit);
		}
	}

	/**
	 * Only works if not conflicted. Finds the split point of the current branch
	 * and the given branch. Finds the files that the current branch, the given
	 * branch, and the split point are all tracking, and uses the merge helper
	 * to checkout and stage the appropriate files. Finally, this method
	 * automatically makes a commit if the merge did not create a conflicted
	 * state. (see findSplitPoint, bothTrackingFile, and mergeHelper, under
	 * UTILITY METHODS)
	 * 
	 * @param branchName
	 *            name of the branch to merge with the current branch.
	 */
	public void mergeCommand(String branchName) {
		if (conflicted) {
			System.out
					.println("Cannot do this command until the merge conflict has been resolved.");
		} else if (!branches.containsKey(branchName)) {
			System.out.println("A branch with that name does not exist.");
		} else if (currentBranch.equals(branchName)) {
			System.out.println("Cannot merge a branch with itself.");
		} else {
			Commit splitPoint = findSplitPoint(branchName);
			Commit currentCommit = branches.get(currentBranch);
			Commit branchCommit = branches.get(branchName);
			for (File mergedFile : branchCommit.tracking.keySet()) {
				mergeHelper(splitPoint, currentCommit, branchCommit,
						mergedFile.getPath());
			}
			if (!conflicted) {
				commitCommand("Merged " + currentBranch + " with " + branchName
						+ ".");
			} else {
				System.out.println("Encountered a merge conflict.");
			}
		}
	}

	/**
	 * Only works if not conflicted. Finds the split point of the given branch
	 * and the current branch. Then replays the current branch on top of the
	 * given branch, propagating any necessary files. Tracks the files that need
	 * to be propagated in toPropagate, and makes use of hasBeenModified and
	 * makeReplayedBranch (see UTILITY METHODS).
	 * 
	 * @param branchName
	 *            name of the branch to which the current branch will be
	 *            replayed.
	 */
	public void rebaseCommand(String branchName) {
		if (conflicted) {
			System.out
					.println("Cannot do this command until the merge conflict has been resolved.");
		} else if (!branches.containsKey(branchName)) {
			System.out.println("A branch with that name does not exist.");
		} else if (branchName.equals(currentBranch)) {
			System.out.println("Cannot rebase a branch onto itself.");
		} else {
			Commit current = branches.get(currentBranch);
			Commit splitPoint = findSplitPoint(branchName);
			Commit branchCommit = branches.get(branchName);
			if (splitPoint.equals(branchCommit)) {
				System.out.println("Already up-to-date.");
			} else if (splitPoint.equals(current)) {
				branches.put(currentBranch, branchCommit);
				return;
			} else {
				toPropagate = new HashSet<File>();
				for (File file : branchCommit.tracking.keySet()) {
					toPropagate.add(file);
				}
				branches.put(currentBranch,
						makeReplayedBranch(current, splitPoint, branchCommit));
			}
		}
	}

	/*
	 * ===================================================================
	 * ~~~~~~~~~~~~~~~~~~~~~ COMMIT CLASS ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	 * ===================================================================
	 */

	private class Commit implements Serializable {
		private int id;
		private String message;
		private String time;
		private Commit parent;
		private HashMap<File, Commit> tracking;

		/**
		 * Constructs a new commit object. Adds the commit's message, if it does
		 * not already exist, to the history of all commits. Points the current
		 * branch to this commit.
		 * 
		 * @param msg
		 *            the message given to the newly created commit.
		 */
		public Commit(String msg) {
			id = currentID;
			currentID++;
			message = msg;
			time = tellTime();
			tracking = new HashMap<File, Commit>();
			branches.put(currentBranch, this);
			commitIDMap.put(id, this);
			if (!history.containsKey(message)) {
				history.put(message, new ArrayList<Commit>());
			}
			history.get(message).add(this);
		}

		/**
		 * Uses the one-argument constructor to initialize most of the parts.
		 * Sets the parent commit, from which the new commit will "inherit" its
		 * tracked files. Then adjusts its tracked files based on the files that
		 * have been staged and that have been marked for untracking, prior to
		 * this commit.
		 * 
		 * A special note about the tracking collection: it associates all the
		 * files that are being tracked, with the most recent ancestor commit
		 * that has updated that file (including itself). This allows
		 * maintenance of the commit's "snapshot" of files without storing
		 * redundant copies.
		 * 
		 * @param msg
		 *            the message given to the newly created commit.
		 * @param parentCommit
		 *            the parent of this commit.
		 */
		public Commit(String msg, Commit parentCommit) {
			this(msg);
			parent = parentCommit;
			tracking.putAll(parent.tracking);
			for (File file : staged) {
				tracking.put(file, this);
			}
			for (File file : untracking) {
				tracking.remove(file);
			}
		}

		/**
		 * Used for the purpose of log and global-log. Displays all relevant
		 * info about a commit.
		 */
		public void reportInfo() {
			System.out.println("===");
			System.out.println("Commit " + id);
			System.out.println(time);
			System.out.println(message);
			System.out.println();
		}
	}

	/*
	 * ===================================================================
	 * ~~~~~~~~~~~~~~~~~~~~~ UTILITY METHODS ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	 * ===================================================================
	 */

	/**
	 * Used in the Commit constructor, marking the time of creation.
	 * 
	 * @return a string representation of the current date and time.
	 */
	private static String tellTime() {
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		Date date = new Date();
		return dateFormat.format(date);
	}

	/**
	 * Loads the previously saved Gitlet object. Allows maintenance of object
	 * state across multiple program runs.
	 * 
	 * @param readFileLocation
	 *            the file from which to deserialize the Gitlet object.
	 * @return the saved Gitlet object, serialized to readFileLocation.
	 */
	private static Gitlet readFromFile(String readFileLocation) {
		try {
			FileInputStream fis = new FileInputStream(readFileLocation);
			ObjectInputStream ois = new ObjectInputStream(fis);
			Gitlet g = (Gitlet) ois.readObject();
			ois.close();
			return g;
		} catch (IOException e) {
			return new Gitlet();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Saves the current state of the Gitlet object to writeFileLocation.
	 * 
	 * @param writeFileLocation
	 *            the file to which the Gitlet object will be serialized.
	 */
	private void writeToFile(String writeFileLocation) {
		try {
			FileOutputStream fos = new FileOutputStream(writeFileLocation);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * A recursive delete function that deletes the specified file. If the
	 * fileName refers to directory, recursively delete all files within the
	 * directory, then delete the directory.
	 * 
	 * SHOULD ONLY BE USED FOR UNSTAGING.
	 * 
	 * @param deleted
	 *            the file to be deleted.
	 */
	private static void deleteFile(File deleted) {
		if (deleted.isDirectory()) {
			File[] files = deleted.listFiles();
			if (files.length > 0) {
				for (File f : files) {
					if (f.isDirectory()) {
						deleteFile(f);
					} else {
						f.delete();
					}
				}
			}
		}
		deleted.delete();
	}

	/**
	 * First, deletes all files/directories inside the staging area. Then, clear
	 * the Gitlet object's list of files that have been staged and/or marked for
	 * untracking.
	 */
	private void clearStage() {
		File[] staged_files = new File(".gitlet/staging_area").listFiles();
		try {
			for (File file : staged_files) {
				deleteFile(file);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		untracking.clear();
		staged.clear();
	}

	/**
	 * Copies files from the location from, to the location to. Makes any
	 * directories that are needed, and replaces the file version if the file
	 * already exists.
	 * 
	 * @param from
	 *            the pathname of the file source.
	 * @param to
	 *            the pathname of the file destination.
	 */
	private static void copyFiles(String from, String to) {
		Path fromPath = new File(from).toPath();
		File toFile = new File(to);
		Path toPath = toFile.toPath();
		try {
			if (!new File(".gitlet").exists()) {
				throw new IllegalStateException(
						"Gitlet has not been initialized.");
			}
			if (!toFile.exists()) {
				new File(to).mkdirs();
			}
			Files.copy(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Used by several of the command methods, including checkout and reset.
	 * Uses the method findFileVersion to find the location of the most recent
	 * version of a commit's tracked file, with the name fileName. Then copies
	 * that file to the working directory.
	 * 
	 * @param checkedOutCommit
	 *            the commit from which the specified file will be checked out.
	 * @param fileName
	 *            the pathname string of the file to be checked out.
	 */
	private void checkoutHelper(Commit checkedOutCommit, String fileName) {
		File relevantVersion = findFileVersion(checkedOutCommit, fileName);
		copyFiles(relevantVersion.getPath(), fileName);
	}

	/**
	 * Checks the relationship between files of the current commit, of the given
	 * branch, and of the split point between the two. The file "merged" is
	 * guaranteed to be tracked by the given branch commit. If the current
	 * commit is not tracking "merged", then the new commit will start tracking
	 * "merged". Otherwise, mergeHelper uses the method hasBeenModified to
	 * compare the contents of the various "merged" file versions.
	 * 
	 * If "merged" has been modified both in the current commit and the branch
	 * commit since the split point, then a conflicted state results, and a
	 * .conflicted file is created. Otherwise, if "merged" has been modified in
	 * the given branch but not in the current branch, then checkout the file
	 * and stage it.
	 * 
	 * @param split
	 *            the commit representing the split point between the current
	 *            commit and the given branch
	 * @param curr
	 *            the commit at the head of the current branch
	 * @param branch
	 *            the commit at the head of the branch that is being merged with
	 *            the current branch
	 * @param merged
	 *            the file whose versions are being compared
	 */
	private void mergeHelper(Commit split, Commit curr, Commit branch,
			String merged) {
		File mergedFile = new File(merged);
		/*
		 * if (!curr.tracking.containsKey(mergedFile)) {
		 * checkoutIDCommand(branch.id, merged); addCommand(merged); } else
		 */if (hasBeenModified(mergedFile, split, branch)
				&& !hasBeenModified(mergedFile, split, curr)) {
			checkoutIDCommand(branch.id, merged);
			addCommand(merged);
		} else if (hasBeenModified(mergedFile, split, branch)
				&& hasBeenModified(mergedFile, split, curr)) {
			File branchVersion = findFileVersion(branch, merged);
			conflicted = true;
			copyFiles(branchVersion.getPath(), merged + ".conflicted");
		}
	}

	/**
	 * Finds the split point, the most recent common ancestor, of the current
	 * commit and the commit at the head of the given branch.
	 * 
	 * @param branchName
	 *            the name of the branch
	 * @return the "split point" commit of the current and the given branch.
	 */
	private Commit findSplitPoint(String branchName) {
		HashSet<Commit> currentNodeHistory = new HashSet<Commit>();
		Commit current;
		Commit given;
		for (current = branches.get(currentBranch); current != null; current = current.parent) {
			currentNodeHistory.add(current);
		}
		for (given = branches.get(branchName); given != null; given = given.parent) {
			if (currentNodeHistory.contains(given)) {
				return given;
			}
		}
		return current;
	}

	/**
	 * Finds the most recently updated version of the specified file being
	 * tracked by the given commit.
	 * 
	 * @param commit
	 *            the commit for which we are searching the most recent version
	 *            of the given file
	 * @param fileName
	 *            the name of the specific file being searched for
	 * @return a File object representing the most recently updated version
	 */
	private static File findFileVersion(Commit commit, String fileName) {
		File neededFile = new File(fileName);
		Commit neededCommit = commit.tracking.get(neededFile);
		return new File(".gitlet/commit_" + neededCommit.id + "/" + fileName);
	}

	/**
	 * Checks whether the contents of file1 are equal to those of file2, by
	 * reading and comparing byte info.
	 * 
	 * @param file1
	 *            the first file to be compared
	 * @param file2
	 *            the second file to be compared
	 * @return a boolean representing whether the contents of file1 and file2
	 *         are equal
	 */
	private static boolean filesEqual(File file1, File file2) {
		try {
			byte[] f1 = Files.readAllBytes(file1.toPath());
			byte[] f2 = Files.readAllBytes(file2.toPath());
			return Arrays.equals(f1, f2);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Returns whether a specified file, tracked by a given commit, has been
	 * modified since the commit's "split point" with a different commit. A file
	 * is modified if it is being tracked by the compared commit but not by the
	 * split point commit. It is also considered to be modified if both commits
	 * are tracking the file, but the file contents are different. Uses
	 * findFileVersion and filesEqual to check this.
	 * 
	 * @param suspect
	 *            the file that we suspect has been modified since the split
	 *            point.
	 * @param splitPoint
	 *            the split point between the compared commit and another
	 *            commit.
	 * @param compared
	 *            the commit whose files are being compared to the files of the
	 *            split point.
	 * @return a boolean representing whether the specified file has been
	 *         modified since the split point.
	 */
	private static boolean hasBeenModified(File suspect, Commit splitPoint,
			Commit compared) {
		if (!splitPoint.tracking.containsKey(suspect)) {
			return true;
		} else if (!compared.tracking.containsKey(suspect)) {
			return true;
		} else {
			File splitPointVersion = findFileVersion(splitPoint,
					suspect.getPath());
			File comparedVersion = findFileVersion(compared, suspect.getPath());
			return !filesEqual(splitPointVersion, comparedVersion);
		}
	}

	/**
	 * A recursive method that replays the current branch of commits and
	 * "attaches" the branch to the end of the given branch. Then initiates the
	 * file propagation process, which occurs on our way back up the recursion.
	 * See propagate.
	 * 
	 * @param current
	 *            the current branch being replayed
	 * @param splitPoint
	 *            the split point between the current branch and the given
	 *            branch
	 * @param branchCommit
	 *            the branch which will serve as the new base of the replayed
	 *            branch
	 * @return the commit at the head of the replayed branch
	 */
	private Commit makeReplayedBranch(Commit current, Commit splitPoint,
			Commit branchCommit) {
		Commit replayedCommit;
		if (current.parent.equals(splitPoint)) {
			replayedCommit = new Commit(current.message, branchCommit);
		} else {
			replayedCommit = new Commit(current.message, makeReplayedBranch(
					current.parent, splitPoint, branchCommit));
		}
		replayedCommit.tracking = new HashMap<File, Commit>();
		replayedCommit.tracking.putAll(current.tracking);
		for (File propagated : toPropagate) {
			propagate(branchCommit, replayedCommit, splitPoint, propagated);
		}
		return replayedCommit;
	}

	/**
	 * A method similar to mergeHelper. For each file in toPropagate, which
	 * keeps track of the files that are still propagating, this method first
	 * checks whether the specified replayed commit is currently tracking that
	 * file. If not, it will start tracking the file. If so, this method checks
	 * whether the version being tracked by the replayed commit has been
	 * modified since the split point. If it has, the file is left alone, and
	 * the file stops propagating. If not, then the version of the file from the
	 * given branch commit will be tracked by the replayed commit only if the
	 * branch commit version has been modified since the split point. If it has
	 * not been modified, then it does not propagate.
	 * 
	 * @param branch
	 *            the commit at the head of the branch serving as the base for
	 *            the replayed branch
	 * @param replayed
	 *            the particular commit within the replayed branch that is
	 *            currently involved in propagation
	 * @param splitPoint
	 *            the split point between the given branch and the former
	 *            current branch being replayed
	 * @param propagated
	 *            the file being propagated through the replayed branch
	 */
	private void propagate(Commit branch, Commit replayed, Commit splitPoint,
			File propagated) {
		Commit lastUpdated = branch.tracking.get(propagated);
		/*
		 * if (!replayed.tracking.containsKey(propagated)) {
		 * replayed.tracking.put(propagated, lastUpdated); } else
		 */if (!hasBeenModified(propagated, splitPoint, replayed)
				&& hasBeenModified(propagated, splitPoint, branch)) {
			replayed.tracking.put(propagated, lastUpdated);
		} else {
			toPropagate.remove(propagated);
		}
	}

	/*
	 * ===================================================================
	 * ~~~~~~~~~~~~~~~~~~~~~~~~ MAIN METHOD ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	 * ===================================================================
	 */

	public static void main(String[] args) {
		Gitlet l = readFromFile(".gitlet/gitlet.ser");
		String command = args[0];
		if (command.equals("init")) {
			l.initCommand();
		} else if (command.equals("add")) {
			l.addCommand(args[1]);
		} else if (command.equals("commit")) {
			String message = "";
			for (int i = 1; i < args.length; i++) {
				if (i == args.length - 1) {
					message += args[i];
				} else {
					message += args[i] + " ";
				}
			}
			l.commitCommand(message);
		} else if (command.equals("rm")) {
			l.rmCommand(args[1]);
		} else if (command.equals("log")) {
			l.logCommand();
		} else if (command.equals("global-log")) {
			l.globalLogCommand();
		} else if (command.equals("find")) {
			String message = "";
			for (int i = 1; i < args.length; i++) {
				if (i == args.length - 1) {
					message += args[i];
				} else {
					message += args[i] + " ";
				}
			}
			l.findCommand(message);
		} else if (command.equals("status")) {
			l.statusCommand();
		} else if (command.equals("checkout")) {
			if (args.length == 3) {
				l.checkoutIDCommand(Integer.parseInt(args[1]), args[2]);
			} else if (l.branches.containsKey(args[1])) {
				l.checkoutBranchCommand(args[1]);
			} else {
				l.checkoutFileCommand(args[1]);
			}
		} else if (command.equals("branch")) {
			l.branchCommand(args[1]);
		} else if (command.equals("rm-branch")) {
			l.rmBranchCommand(args[1]);
		} else if (command.equals("reset")) {
			l.resetCommand(Integer.parseInt(args[1]));
		} else if (command.equals("merge")) {
			l.mergeCommand(args[1]);
		} else if (command.equals("rebase")) {
			l.rebaseCommand(args[1]);
		}
		l.writeToFile(".gitlet/gitlet.ser");
	}
}
