package cs223.Engine;

import akka.actor.InvalidMessageException;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.mysql.cj.jdbc.MysqlXid;
import cs223.Common.AgentLog;
import cs223.Common.Constants;
import cs223.Common.LogType;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.postgresql.core.BaseConnection;
import org.postgresql.xa.PGXAConnection;


import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.*;
import java.sql.*;
import java.time.Duration;

public class Agent extends AbstractBehavior<Agent.Command> {

    PGXAConnection XAconn;
    ActorRef<Coordinator.Command> coordinator;
    int bid;
    int tid = 0;
    boolean isNowCommitPhase = false;
    Xid currentId;
    int gid;
    AgentLog log;
    public static Behavior<Command> create(ActorRef<Coordinator.Command> coordinator, int port, int id) {
        return Behaviors.setup(s -> Behaviors.setup(c -> new Agent(c, coordinator,port,id)));
    }
    public enum Ack implements Command {
        INSTANCE
    }
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Prepare.class, this::onPrepare)
                .onMessage(Commit.class,this::onCommit)
                .onMessage(Abort.class,this::onAbort)
                .build();
    }

    interface Command{}

    public static final class Prepare implements Command {
        final String[] payload;
        final int gid;
        public Prepare(String[] payload, int gid) {
            this.payload = payload;
            this.gid = gid;
        }
    }

    public static final class Commit implements Command{
        ActorRef<Coordinator.Ack> replyTo;
        public Commit(ActorRef<Coordinator.Ack> replyTo){
            this.replyTo = replyTo;
        }

    }

    public static final class Abort implements Command{
        ActorRef<Coordinator.Ack> replyTo;
        public Abort(ActorRef<Coordinator.Ack> replyTo){
            this.replyTo = replyTo;
        }

    }

    public static final class AgentReply implements Coordinator.Command{
        final int command;
        final Xid id;
        public AgentReply(int command,Xid id){
            this.command = command;
            this.id = id;
        }
    }

    private Behavior<Command> onCommit(Commit commit) {
        if (log.checkStatus(gid) == LogType.COMMIT || log.checkStatus(gid) == LogType.COMPLETE) {
            System.out.println("agent "+ bid+ " has already committed");
            return Behaviors.same();
        }
        commit.replyTo.tell(Coordinator.Ack.INSTANCE);
        if(!isNowCommitPhase)
            return Behaviors.same();
        try {
            System.out.println("agent"+bid+" commit");
            log.writeLog(LogType.COMMIT,tid);
            XAconn.commit(currentId,false);
        } catch (XAException e) {
            e.printStackTrace();
        }
        isNowCommitPhase = false;
        log.writeLog(LogType.COMPLETE,tid);
        return Behaviors.same();
    }


    private Behavior<Command> onAbort(Abort abort){
        if (log.checkStatus(gid) == LogType.ABORT || log.checkStatus(gid) == LogType.COMPLETE) {
            System.out.println("agent "+ bid+ " has already aborted");
            return Behaviors.same();
        }
        abort.replyTo.tell(Coordinator.Ack.INSTANCE);
        if(!isNowCommitPhase)
            return Behaviors.same();
        try {
            System.out.println("agent"+bid+" rollback");
            log.writeLog(LogType.ABORT,tid);
            XAconn.rollback(currentId);
        } catch (XAException e) {
            e.printStackTrace();
        }
        isNowCommitPhase = false;
        log.writeLog(LogType.COMPLETE,tid);
        return Behaviors.same();
    }


    // check the log about the previous state, if the previous state is
    // YES, then ask Coordinator about the states
    // NO, just do the abort.

    private void previousLogStates() {
        try {
            LogType logType = log.checkStatus(gid);
            if (logType == LogType.COMMIT || logType == LogType.ABORT || logType == LogType.COMPLETE) {
            } else if (logType == LogType.YES) {
                // ask coordinator about the states, and then do the following things(COMMIT or ABORT)
                // agent ask coordinator about the states
                try {
                    getContext().ask(String.class, coordinator, Duration.ofSeconds(1), Coordinator.RequestStates::new, (response, throwable) -> {
                        if (response != null) {
                            // if the log type isn't complete, which means we haven't aborted or committed right now.
                            if (response.equals("COMMITTED")) {
                                if (log.checkStatus(gid) != LogType.COMPLETE) {
                                    System.out.println("After shutting down, committed msg from agent " + bid + " was sent");
                                    log.writeLog(LogType.COMMIT, tid);
                                    XAconn.commit(currentId, false);
                                    isNowCommitPhase = false;
                                    log.writeLog(LogType.COMPLETE, tid);
                                    coordinator.tell(Coordinator.Ack.COMMITACK);
                                }
                            } else if (response.equals("ABORTED")) {
                                if (log.checkStatus(gid) != LogType.COMPLETE) {
                                    System.out.println("After shutting down, aborted msg from agent " + bid + " was sent");
                                    log.writeLog(LogType.ABORT, gid);
                                    XAconn.rollback(currentId);
                                    isNowCommitPhase = false;
                                    log.writeLog(LogType.COMPLETE, tid);
                                }
                            }
                        }

                        return Ack.INSTANCE;
                    });
                }catch (InvalidMessageException e) {
                    e.printStackTrace();

                }
            } else if (logType == LogType.NO){
                if (log.checkStatus(gid) != LogType.ABORT) {
                    System.out.println("Suddently Agent " + bid + " shutdown, and after recovering, it decide to abort...");
                    getContext().getSelf().tell(new Abort(null));
                    log.writeLog(LogType.ABORT, tid);
                    XAconn.rollback(currentId);
                    log.writeLog(LogType.COMPLETE, tid);
                }
            }
        } catch (XAException e) {
            e.printStackTrace();
        }
    }
    private Behavior<Command> onPrepare(Prepare task) throws InterruptedException {
        Thread.sleep(2000);
        gid = task.gid;
        currentId = new MysqlXid(intToByteArray(task.gid),intToByteArray(bid),tid);
        tid++;
        try {
            //start of the first phase
            XAconn.start(currentId, XAResource.TMNOFLAGS);
            Statement stmt = XAconn.getConnection().createStatement();
            log.recordNew(task.gid);
            for (String i : task.payload) {
                stmt.addBatch(i);
            }
            stmt.executeBatch();
            XAconn.end(currentId, XAResource.TMSUCCESS);
            int result = XAconn.prepare(currentId);
            //vote YES
            if (bid == 1) {
                System.out.println("agent" + bid + " vote YES on transaction " + task.gid);
                log.writeLog(LogType.YES, tid);
                coordinator.tell(new AgentReply(result, currentId));
            } else {
                System.out.println("agent" + bid + " vote YES on transaction " + task.gid);
                log.writeLog(LogType.YES, tid);
                coordinator.tell(new AgentReply(result, currentId));
            }
            if (Constants.RANDOMCASE == Constants.DOWNAFTERYES || Constants.RANDOMCASE == Constants.DOWNAFTERNO) {
                previousLogStates();
            }


        } catch(Exception e) {
            //vote NO
            System.out.println("agent"+bid+" vote NO on transaction "+task.gid);
            // if the participant fails after sending no, then
            log.writeLog(LogType.NO,tid);
            coordinator.tell(new AgentReply(Constants.ABORT,currentId));

            if (Constants.RANDOMCASE == Constants.DOWNAFTERNO) {
                previousLogStates();
            }
        }


        //start of the second phase
        isNowCommitPhase = true;
        return Behaviors.same();
    }

    public void trapDeadloop(Connection conn) throws SQLException {
        Statement deadloopStm = conn.createStatement();
        deadloopStm.execute("Select gid from pg_prepared_xacts WHERE database=current_database()");

        ResultSet gids = deadloopStm.getResultSet();
        while (gids.next()) {
            Statement temp = conn.createStatement();
            System.out.println(gids.getString("gid"));
            temp.execute("ROLLBACK PREPARED " + "\'"+ gids.getString("gid") +"\'");
            temp.close();
        }

        deadloopStm.close();
    }
    private Agent(ActorContext<Command> context, ActorRef<Coordinator.Command> coordinator, int port, int id) {
        super(context);
        this.bid = id;
        this.coordinator = coordinator;
        this.log = new AgentLog(Constants.logPath,id);
        try {
            //drop tables
            Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:"+port+"/"+Constants.url,Constants.username,Constants.password);
            ScriptRunner sr = new ScriptRunner(conn);
            sr.setLogWriter(null);

            trapDeadloop(conn);
            String dropPath = Constants.dataRootPath+"\\schema\\"+Constants.dbType.getDatabase()+"_drop.sql";
            sr.runScript(new BufferedReader(new FileReader(dropPath)));
            String schemaPath = Constants.dataRootPath + "\\schema\\create.sql";
            sr.runScript(new BufferedReader(new FileReader(schemaPath)));
            String metadataPath = Constants.dataRootPath + "\\data\\" + Constants.benchmarkType + "\\metadata.sql";
            sr.runScript(new BufferedReader(new FileReader(metadataPath)));
            sr.runScript(new BufferedReader(new FileReader(metadataPath)));
            XAconn = new PGXAConnection((BaseConnection)conn);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }
}