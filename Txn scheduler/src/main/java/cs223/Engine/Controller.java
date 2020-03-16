package cs223.Engine;

import akka.actor.Actor;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.routing.Broadcast;
import akka.routing.SmallestMailboxPool;
import cs223.Common.Constants;
import cs223.Common.DatabaseType;
import akka.actor.typed.javadsl.Adapter;
import cs223.Transaction.TransactionManager;
import javafx.util.Pair;
import org.apache.ibatis.jdbc.ScriptRunner;
import scala.Tuple2;
import scala.Tuple3;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public class Controller extends AbstractBehavior<Controller.Command> {

    private int maxThreads;
    private int isolationLevel;
    private String url;
    private String params;
    private String username;
    private String password;
    private DatabaseType dbType;

    public interface Command{}

    public static class Setup implements Command {
        int maxThreads;
        int isolationLevel;
        String url;
        String params;
        String username;
        String password;
        DatabaseType dbType;

        public Setup(int maxThreads, DatabaseType dbType, String url, String params, String username, String password, int isolationLevel) {
            this.url = url;
            this.maxThreads = maxThreads;
            this.dbType = dbType;
            this.params = params;
            this.username = username;
            this.password = password;
            this.isolationLevel = isolationLevel;
        }
    }


    public static class RunBenchmark implements Command {
        String benchmarkType;
        int minInsertions;
        int maxInsertions;
        long transactionInterval;
        ActorRef<Reply> replyTo;

        public RunBenchmark(String benchmarkType, int minInsertions, int maxInsertions, long transactionInterval,ActorRef<Reply> replyTo) {
            this.benchmarkType = benchmarkType;
            this.minInsertions = minInsertions;
            this.maxInsertions = maxInsertions;
            this.transactionInterval = transactionInterval;
            this.replyTo = replyTo;
        }
    }

    public interface Reply{}

    public enum BenchmarkCompleted implements Reply {
        INSTANCE
    }



    public static Behavior<Command> create() {
        return Behaviors.setup(Controller::new);
    }

    private Controller(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RunBenchmark.class, this::onRunBenchmark)
                .onMessage(Setup.class, m -> {
                    this.dbType = m.dbType;
                    this.isolationLevel = m.isolationLevel;
                    this.maxThreads = m.maxThreads;
                    this.params = (m.params==null || m.params.trim().length()==0?"":"?"+m.params);
                    this.password = m.password;
                    this.url = m.url;
                    this.username = m.username;
                    return Behaviors.same();
                })
                .build();
    }

    /**
     * We abstract one controller as coordinator, which is:
     * 1.iterate the transaction
     * 2.doing the hash code
     * 3.tell the corresponding worker to execute it (PREPARE)
     * 4.get the response from observer (PREPARED)
     * 5.send COMMIT to worker
     * 6.receive COMMITTED from observer and write into log
     * **/
    private Behavior<Command> onDistributedBenchMark(RunBenchmark runBenchmark) throws SQLException, IOException, InterruptedException, ExecutionException {
        String[] urls = {"jdbc:postgresql://localhost:5432/cs223","jdbc:postgresql://localhost:5432/cs223","jdbc:postgresql://localhost:5432/cs223"};
        String[] usernames = {"","",""};
        String[] passwords = {"","",""};
        Connection[] connections = new Connection[urls.length];

        int numberOfServers = 3;
        for (int i = 0; i < url.length(); i++)
            connections[i] = DriverManager.getConnection(urls[i], usernames[i], passwords[i]);


        ActorRef<Observer.Command> observer = getContext().spawn(Observer.create(maxThreads), "observer");

        ActorRef<Worker.Command> executors = Adapter.toTyped(Adapter.actorOf(getContext(),new SmallestMailboxPool(maxThreads).props(Worker.props(dbType,url,params,username,password,isolationLevel, observer))));

        TransactionManager tm = new TransactionManager(runBenchmark.benchmarkType,runBenchmark.minInsertions,runBenchmark.maxInsertions,runBenchmark.transactionInterval);

        int iteration = 0;
        while (iteration++ < 50000){
            Tuple3<Long, String[], Boolean> res = tm.next();

            String sensor = "sensor1";

            Timestamp timestamp = null;

            Tuple2<String, Timestamp> tuple2 = new Tuple2<>(sensor, timestamp);

            int code = tuple2.hashCode() % numberOfServers;

            executors.tell();

        }

    }
    private Behavior<Command> onRunBenchmark(RunBenchmark runBenchmark) throws SQLException, IOException, InterruptedException, ExecutionException {

        String path = "jdbc:postgresql://localhost:5432/cs223";
        //run startup sql scripts
        Connection conn = DriverManager.getConnection(path,username,password);
        ScriptRunner sr = new ScriptRunner(conn);
        sr.setLogWriter(null);
        String schemaPath = "/Users/mengzhou/Desktop/ConcurrentTProcessor/schema/create.sql";
        sr.runScript(new BufferedReader(new FileReader(schemaPath)));
        String metadataPath = "/Users/mengzhou/Desktop/ConcurrentTProcessor/low_concurrency/metadata.sql";
        sr.runScript(new BufferedReader(new FileReader(metadataPath)));
        sr.closeConnection();
        conn.close();

        //create observer
        ActorRef<Observer.Command> observer = getContext().spawn(Observer.create(maxThreads), "observer");

        //create actor pool
        ActorRef<Worker.Command> executors = Adapter.toTyped(Adapter.actorOf(getContext(),new SmallestMailboxPool(maxThreads).props(Worker.props(dbType,url,params,username,password,isolationLevel, observer))));

        //execute sql statements
        TransactionManager tm = new TransactionManager(runBenchmark.benchmarkType,runBenchmark.minInsertions,runBenchmark.maxInsertions,runBenchmark.transactionInterval);

        int cur=0;
        while(cur<50000){
            Tuple3<Long,String[],Boolean> res = tm.next();
            if(res._2() == null){
                break;
            }
//            System.out.println("---------------message head----------------");
//            for(String i:res._2()){
//                System.out.println(i);
//            }
//            System.out.println("---------------message tail[ sleep "+res._1()+" ms]----------------\n");
            executors.tell(new Worker.WorkerTask(res._2(),res._3()));
            cur++;
            if(res._1()>0){
                Thread.sleep(res._1());
            }

        }

        //send to all worker that no more work to do
        ActorRef classicExecutors = executors.narrow();
        classicExecutors.tell(new Broadcast(new Worker.End()));

        //print statistics
        CompletionStage<Observer.Reply> result = AskPattern.ask(observer, Observer.FinalReport::new,Duration.ofSeconds(120),getContext().getSystem().scheduler());
        result.toCompletableFuture().get();


        //drop tables
        conn = DriverManager.getConnection(path, username,password);
        sr = new ScriptRunner(conn);
        sr.setLogWriter(null);
        String dropPath = "/Users/mengzhou/Desktop/ConcurrentTProcessor/schema/drop.sql";
        sr.runScript(new BufferedReader(new FileReader(dropPath)));
        sr.closeConnection();
        conn.close();
        observer.tell(Observer.GracefulShutdown.INSTANCE);
        runBenchmark.replyTo.tell(BenchmarkCompleted.INSTANCE);
        return Behaviors.same();
    }
}