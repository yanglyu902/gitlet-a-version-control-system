package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Yang Lyu
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        String firstArg = args[0];
        switch (firstArg) {
            case "init":
                if (args.length != 1) {
                    System.out.println("Incorrect operands.");
                    System.exit(0);
                }
                Repository repo = new Repository();
                break;
            case "add":
                alert(args.length, 2, "Incorrect operands.");
                Repository.add(args[1]);
                break;
            case "commit":
                alert(args.length, 2, "Please enter a commit message.");
                if (args[1].equals("")) {
                    System.out.println("Please enter a commit message.");
                    break;
                }
                Repository.commit(args[1]);
                break;
            case "rm":
                alert(args.length, 2, "Incorrect operands.");
                Repository.rm(args[1]);
                break;
            case "log":
                alert(args.length, 1, "Incorrect operands.");
                Repository.log();
                break;
            case "global-log":
                alert(args.length, 1, "Incorrect operands.");
                Repository.logGlobal();
                break;
            case "find":
                alert(args.length, 2, "Incorrect operands.");
                Repository.find(args[1]);
                break;
            case "status":
                alert(args.length, 1, "Incorrect operands.");
                Repository.status();
                break;
            case "checkout":
                if (args.length == 3) {
                    Repository.checkoutFile(args[2]);
                } else if (args.length == 4) {
                    if (!args[2].equals("--")) {
                        System.out.println("Incorrect operands.");
                        break;
                    }
                    Repository.checkoutFromCommit(args[1], args[3]);
                } else if (args.length == 2) {
                    Repository.checkoutBranch(args[1]);
                }
                break;
            case "branch":
                alert(args.length, 2, "Incorrect operands.");
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                alert(args.length, 2, "Incorrect operands.");
                Repository.removeBranch(args[1]);
                break;
            case "reset":
                alert(args.length, 2, "Incorrect operands.");
                Repository.reset(args[1]);
                break;
            case "merge":
                alert(args.length, 2, "Incorrect operands.");
                Repository.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
        }
    }

    public static void alert(int arglen, int n, String message) {

        if (!Utils.join(Repository.CWD, ".gitlet").exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }

        if (arglen != n) {
            System.out.println(message);
            System.exit(0);
        }
    }
}
