package cs223.Common;

//TRANSACTION_NONE = 0
//TRANSACTION_READ_UNCOMMITTED = 1
//TRANSACTION_READ_COMMITTED = 2
//TRANSACTION_REPEATABLE_READ = 4
//TRANSACTION_SERIALIZABLE = 8


//Throughput = 671.3117057364792 tps
//Avg response time for queries = 4.476574386859584 s
//total time = 1261.40806538 s

public class Constants {

    public static final String dataRootPath = "D:\\project1";

    public static final DatabaseType dbType = DatabaseType.Postgres;

    public static final String url = "cs223";

    public static final String params = "";

    public static final String username = "cs223";

    public static final String password = "123";

    public static final int isolationLevel = 1;

    public static final int maxThreads = 1;

    public static final int minInsertions = 1;

    public static final int maxInsertions = 10;

    public static final String benchmarkType = "low_concurrency";

    public static final long transactionInterval = 1000;  //1000ms


}
