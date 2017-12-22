DESIGN DOCUMENT
Classes:
1. Gitlet
2. CommitTree (private, nested inside Gitlet)
3. Commit (private, nested inside Gitlet)
4. Branch (private, nested inside Gitlet)
Gitlet
  -Instance variables:
  1. CommitTree myCommitTree
  2. HashSet<File> untracking
  3. HashSet<File> staged
  4. Int currentID
  5. Boolean conflicted
  -Methods
  (feel the need to clean this up):
  1. clearFiles – clear files from untracking and staged after a commit
  2. initCommand – makes the .gitlet folder with the staging_area folder inside
  3. addCommand – adds to staging area or unmarks from untracking
  4. rmCommand – removes from staging or adds to the “untracking” list
  5. logCommand – calls the reportInfo method of the headCommit, which invokes the
  -reportInfo command recursively on its parentCommit
  6. findCommand – prints the ID of all commits sharing the specified message
  7. statusCommand – iterate through myCommitTree.branches, staged, and untracking
  instance variables
  8. checkoutCommand – uses the myCommitTree headCommit version of the file to
  overwrite the file in the current directory. OR uses the commit with the given commit id to find
  the version of the file used to overwrite. OR uses the commit at the head of the given branch to
  overwrite the files.
  9. branchCommand – makes a new branch in branches; points at the same commit
  that the current branch is pointing to.
  10. rmbranchCommand – remove a branch from the branches list
  11. mergeCommand – very complicated. Need to know how to recognize a split
  point.Requires checking the contents of the files at the current branch and the given branch to
  see if they are different. If the current branch has not been modified but the given branch has,
  then use the given branch’s files. If the other way, leave the current branch’s files as they are. If
  they are both modified, then make .conflicted files.
  12. rebaseCommand – requires being able to find a split point. Replay a branch, making
  copies of the commits in the current branch and “sticking” them onto the given branch.
  Propagate changes as well.
  -Other
  1. No argument constructor: instantiates myCommitTree CommitTree
CommitTree - likely to remove and move all the instance variables to gitlet
  -Instance variables:
  1. Commit headCommit
  2. HashSet<Branch> branches
  3. HashSet<Commit> history
  -Methods
  1. addCommit
  -Other
  1. No argument constructor: instantiates headCommit, branches, and history
Commit
  -Instance variables:
  1. int id
  2. String message
  3. String time
  4. Commit parent
  5. HashSet<File> tracking
  -Methods
  1. reportInfo – reports all its relevant info for the sake of log and globallog
  2. hashCode – for quicker access in history, for the sake of find
Branch - likely to remove altogether
  -instance variables:
  1. String myName
  2. Commit myCommit
  -Methods:
  1. changeCommit – changes myCommit to the commit in the argument
