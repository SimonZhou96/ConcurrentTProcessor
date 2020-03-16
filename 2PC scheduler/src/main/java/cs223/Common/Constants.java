package cs223.Common;

public class Constants {

    public static final String dataRootPath = "D:\\project1";


    public static DatabaseType dbType = DatabaseType.Postgres;

    public static String url = "cs223";

    public static final String username = "cs223";

    public static final String password = "cs223";

    public static final int minInsertions = 1;

    public static final int maxInsertions = 10;

    public static String benchmarkType = "high_concurrency";

    public static final long transactionInterval = 1000;  //1000ms

    public static int numTransactions = 3;

    public static int numAgents = 3;
    public static int ABORT = -1;

    public static int startPort = 5440;

    public static final String logPath = "./Datalogs";

    public static final long coordinatorTimer = 0;

    // 0: normal case, just do the 2 PC
    // 1:
    public static int RANDOMCASE = 0;

    public static final int NORMAL = 0;

    public static final int DOWNBEFOREYES = 1;

    public static final int DOWNAFTERYES = 2;

    public static final int DOWNAFTERNO = 3;

    public static int COORDINATORRANDOMCASE = 0;

    public static final int COORDINATOR_DOWN_ABORT = 4;

    public static final int COORDINATOR_DOWN_COMMIT = 5;
}
