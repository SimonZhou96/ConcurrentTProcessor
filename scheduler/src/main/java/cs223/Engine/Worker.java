package cs223.Engine;


import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import cs223.Common.Constants;
import cs223.Common.DatabaseType;
import org.apache.ibatis.jdbc.SQL;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

public class Worker extends AbstractActor {

    Connection conn;
    ActorRef<Observer.Command> observer;
    long aggregatedExecutionTime = 0;
    long numTransactions = 0;
    long numQueries = 0;
    long aggregatedQueryTime = 0;

    static Props props(DatabaseType dbType, String url, String params, String username, String password, int isolationLevel, ActorRef<Observer.Command> observer) {
        return Props.create(Worker.class, () -> new Worker(dbType,url,params,username,password,isolationLevel,observer)).withDispatcher("prio-dispatcher");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(
                        WorkerTask.class,
                        this::onReceiveTask)
                .match(
                        End.class,
                        this::onEnd)
                .match(
                        ReportEveryInterval.class,
                        this::onReportEveryInterval
                )
                .build();
    }

    interface Command{}

    public static final class ReportEveryInterval implements Observer.Command {
        public ReportEveryInterval() {

        }
    }

    public static final class WorkerTask implements Command {
        final String[] payload;
        final boolean isQuery;

        public WorkerTask(String[] payload,boolean isQuery) {
            this.payload = payload;
            this.isQuery = isQuery;
        }
    }

    public static final class End implements Command {

        public End() {

        }
    }

    private void onReceiveDistributedTask(WorkerTask task) throws SQLException {
        long start;
        long interval;

        if (!task.isQuery) {
            Statement stmt = conn.createStatement();
            for (String i: task.payload) {
                stmt.addBatch(i);
            }

            start = System.nanoTime();
            stmt.executeBatch();

            stmt.close();
            conn.commit();
            interval = System.nanoTime() - start;
        }
    }
    private void onReceiveTask(WorkerTask task) throws SQLException {
        long start;
        long interval;
        if(!task.isQuery){
            Statement stmt = conn.createStatement();
            for(String i:task.payload){
//                System.out.println(i);
                stmt.addBatch(i);
            }
            start = System.nanoTime();
            stmt.executeBatch();
            stmt.close();
            conn.commit();
            interval = System.nanoTime() - start;
        }else{
            assert(task.payload.length == 1);
            Statement stmt = conn.createStatement();
            start = System.nanoTime();
            stmt.execute(task.payload[0]);
            stmt.close();
            conn.commit();
            interval = System.nanoTime() - start;
            aggregatedQueryTime += interval;
            numQueries++;
        }
        aggregatedExecutionTime += interval;
        numTransactions++;
    }

    private void onEnd(End msg){
        observer.tell(new Observer.Report(numTransactions,aggregatedExecutionTime,numQueries,aggregatedQueryTime));
        self().tell(PoisonPill.getInstance(),null);
    }


    private void onReportEveryInterval(ReportEveryInterval reportEveryInterval) {
        observer.tell(new Observer.Report(numTransactions,aggregatedExecutionTime,numQueries,aggregatedQueryTime));
    }

    private Worker(int workerID, ActorRef<Observer.Command> observer) {

    }
    private Worker(DatabaseType dbType, String url, String params, String username, String password, int isolationLevel, ActorRef<Observer.Command> observer) throws SQLException {
        this.observer = observer;

        String path = "jdbc:postgresql://localhost:5432/cs223";
        conn = DriverManager.getConnection(path, Constants.username,Constants.password);
        conn.setTransactionIsolation(isolationLevel);
        conn.setAutoCommit(false);

        Runnable task = () -> {
            self().tell(new ReportEveryInterval(), null);
        };

        getContext().getSystem().scheduler().scheduleAtFixedRate(
                Duration.ZERO,Duration.ofSeconds(5),task, getContext().getSystem().dispatcher());
    }

}
