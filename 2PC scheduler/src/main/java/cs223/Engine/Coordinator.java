package cs223.Engine;

import akka.actor.Actor;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import cs223.Common.Constants;
import cs223.Common.CoordinatorLog;
import cs223.Common.LogType;
import cs223.Transaction.TransactionManager;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Coordinator extends AbstractBehavior<Coordinator.Command> {

    private ActorRef<Reply> replyTo;
    ArrayList<ActorRef<Agent.Command>> agents;
    ArrayList<ArrayList<String>> partitions;
    TransactionManager tm;
    int transactionCounter = 0;
    int numVoteReceived = 0;
    AtomicInteger tempNumberRequestState = new AtomicInteger(0);
    boolean voteToAbort = false;
    ArrayList<ActorRef<Agent.Command>> agentsInCurrentTransaction;
    private CoordinatorLog log;
    long prepareTimeStart;
    public static String stateResponse = "NOTFOUND";
    private int numCommitACKs;
    public interface Command{}
    Random random;
    public static class RunBenchmark implements Command {
        String benchmarkType;
        int minInsertions;
        int maxInsertions;
        long transactionInterval;
        ActorRef<Reply> replyTo;
        int numAgents;

        public RunBenchmark(String benchmarkType, int minInsertions, int maxInsertions, long transactionInterval, ActorRef<Reply> replyTo, int numAgents) {
            this.benchmarkType = benchmarkType;
            this.minInsertions = minInsertions;
            this.maxInsertions = maxInsertions;
            this.transactionInterval = transactionInterval;
            this.replyTo = replyTo;
            this.numAgents = numAgents;

        }
    }

    public interface Reply{}

    public enum BenchmarkCompleted implements Reply {
        INSTANCE
    }
    public enum Ack implements Command {
        INSTANCE,
        ABORTACK,
        COMMITACK
    }

    public static final class RequestStates implements Command {
        ActorRef<String> replyTo;
        public RequestStates(ActorRef<String> replyTo) {
            this.replyTo = replyTo;
        }
    }


    public static final class NextTransaction implements Command{
        public NextTransaction(){
        }
    }

    public static final class TimeOut implements Command{
        public TimeOut() {
        }

    }

    public static Behavior<Command> create() {
        return Behaviors.setup(Coordinator::new);
    }

    private Coordinator(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(RunBenchmark.class, this::onRunBenchmark)
                .onMessage(NextTransaction.class,this::onNextTransaction)
                .onMessage(Agent.AgentReply.class,this::onReceiveReply)
                .onMessage(Ack.class,this::onAck)
                .onMessage(RequestStates.class, this::onRequestState)
                .onMessage(TimeOut.class, this::onTimeOut)
                .build();
    }


    private Behavior<Command> onAck(Ack ack){
        if (ack == Ack.COMMITACK) {
            numCommitACKs++;
            if (numCommitACKs == agentsInCurrentTransaction.size()) {
                int currentTransaction = transactionCounter;
                log.writeLog(LogType.COMMIT, currentTransaction);
                System.out.println("Coordinator commit transaction " + transactionCounter);
                log.writeLog(LogType.COMPLETE, transactionCounter);
                System.out.println("Done");
                numCommitACKs = 0;
                voteToAbort = false;
                if (tempNumberRequestState.get() == Constants.numAgents) {
                    tempNumberRequestState.set(0);
                    stateResponse = "NOTFOUND";
                }
                getContext().getSelf().tell(new NextTransaction());
            }
        }

        return Behaviors.same();
    }

    private Behavior<Command> onRequestState(RequestStates requestStates) {
//        System.out.println(stateResponse);
        // tell participants
        requestStates.replyTo.tell(stateResponse);
        tempNumberRequestState.getAndIncrement();

        return Behaviors.same();
    }
    private Behavior<Command> onReceiveReply(Agent.AgentReply reply) throws InterruptedException {
        numVoteReceived++;
        if(reply.command == Constants.ABORT)
            voteToAbort = true;
        int currentTransaction = transactionCounter;
        if(numVoteReceived == agentsInCurrentTransaction.size()){
            if(voteToAbort){
                if (log.checkStatus(currentTransaction) != LogType.ABORT) {
                    stateResponse = "ABORTED";
                    System.out.println("Coordinator aborts the transaction " + transactionCounter);
                    for (ActorRef<Agent.Command> agent : agentsInCurrentTransaction) {
                        getContext().ask(Ack.class, agent, Duration.ofSeconds(30), Agent.Abort::new, (response, throwable) -> {
                            if (response == null) {
                                System.out.println("Transaction " + currentTransaction + " didn't successfully abort");
                            }
                            return Ack.INSTANCE;
                        });
                    }
                    log.writeLog(LogType.ABORT, currentTransaction);
                }
            }else{
                if (log.checkStatus(currentTransaction) != LogType.COMMIT) {
                    stateResponse = "COMMITTED";
                    if (Constants.RANDOMCASE != Constants.DOWNAFTERYES) {
                        // to simulate the case where coordinator could not send commit request to agent3 because of the failure
                        for (ActorRef<Agent.Command> agent : agentsInCurrentTransaction) {
                            getContext().ask(Ack.class, agent, Duration.ofSeconds(30), Agent.Commit::new, (response, throwable) -> {

                                if (response == null) {
                                    System.out.println("Transaction " + currentTransaction + " didn't successfully commit");
                                }
                                return Ack.INSTANCE;
                            });
                        }
                        log.writeLog(LogType.COMMIT, currentTransaction);
                    }

                }
            }
            /**If the shut down down happens here, then the coordinator just check the log file, and then write the completion**/
            if (Constants.COORDINATORRANDOMCASE == Constants.COORDINATOR_DOWN_COMMIT) {
                System.out.println("Suddenly the coordinator shutdown after sending the commit command, resend the commit command");
                for (ActorRef<Agent.Command> agent : agentsInCurrentTransaction) {
                    getContext().ask(Ack.class, agent, Duration.ofSeconds(30), Agent.Commit::new, (response, throwable) -> {
                        if (response == null) {
                            System.out.println("Transaction " + currentTransaction + " didn't successfully commit");
                        }
                        return Ack.INSTANCE;
                    });
                }

            }

            if (Constants.COORDINATORRANDOMCASE == Constants.COORDINATOR_DOWN_ABORT) {
                System.out.println("Suddenly the coordinator shutdown after sending the abort command");
                for (ActorRef<Agent.Command> agent : agentsInCurrentTransaction) {
                    getContext().ask(Ack.class, agent, Duration.ofSeconds(30), Agent.Abort::new, (response, throwable) -> {
                        if (response == null) {
                            System.out.println("Transaction " + currentTransaction + " didn't successfully abort");
                        }
                        return Ack.INSTANCE;
                    });
                }
            }

            numVoteReceived = 0;
            voteToAbort = false;
            if (Constants.RANDOMCASE != Constants.DOWNAFTERYES) {
                log.writeLog(LogType.COMPLETE,currentTransaction);
                System.out.println("Write completion, Done");
                getContext().getSelf().tell(new NextTransaction());
            }
        }
        return Behaviors.same();
    }
    private Behavior<Command> onTimeOut(TimeOut timeOut) {
        LogType previousType = log.checkStatus(transactionCounter);
        if (previousType == LogType.ABORT || previousType == LogType.COMPLETE) return Behaviors.same();
        long prepareTimeEnd = System.currentTimeMillis();
        long difference = prepareTimeEnd - prepareTimeStart;
        prepareTimeStart = System.currentTimeMillis();
        if (difference > Constants.coordinatorTimer && numVoteReceived != agentsInCurrentTransaction.size()) {
            System.out.println("time out, send the abort message to every agent");
            voteToAbort = true;
            log.writeLog(LogType.ABORT, transactionCounter);
            stateResponse = "ABORTED";
            for (ActorRef<Agent.Command> agent : agentsInCurrentTransaction) {
                getContext().ask(Ack.class, agent, Duration.ofSeconds(30), Agent.Abort::new, (response, throwable) -> {
                    if (response == null) {
                        System.out.println("Transaction " + transactionCounter + " didn't successfully abort");
                    }
                    return Ack.INSTANCE;
                });
            }
        }
        return Behaviors.same();
    }

    private Behavior<Command> onNextTransaction(NextTransaction nt) throws IOException {
        Constants.COORDINATORRANDOMCASE = Constants.NORMAL;
        Constants.RANDOMCASE = Constants.DOWNBEFOREYES;
        if(transactionCounter < Constants.numTransactions){
            partitions.forEach(ArrayList::clear);
            agentsInCurrentTransaction = new ArrayList<>();
            String[] res = tm.next();
            if(res == null){
                replyTo.tell(BenchmarkCompleted.INSTANCE);
                return Behaviors.same();
            }
            for(String i: res){
                Matcher m = Pattern.compile("\'.*?\'").matcher(i);
                ArrayList<String> results = new ArrayList<>();
                while(m.find()){
                    results.add(m.group());
                }
                int hash = ((results.get(1)+results.get(2)).hashCode()%partitions.size()+partitions.size())%partitions.size();
                partitions.get(hash).add(i);
            }
            log.recordNew(transactionCounter + 1);
            prepareTimeStart = System.currentTimeMillis();

            System.out.println();
            System.out.println("Transaction " + (transactionCounter + 1));
            for(int i=0;i<agents.size();++i){
                if(!partitions.get(i).isEmpty()){
                    agents.get(i).tell(new Agent.Prepare(partitions.get(i).toArray(new String[0]),transactionCounter + 1));
                    agentsInCurrentTransaction.add(agents.get(i));
                }
            }
            transactionCounter++;
        }
        else {
            replyTo.tell(BenchmarkCompleted.INSTANCE);
            log.saveFile();
        }
        return Behaviors.same();
    }

    private Behavior<Command> onRunBenchmark(RunBenchmark runBenchmark) throws IOException {
        replyTo = runBenchmark.replyTo;
        agents = new ArrayList<>(runBenchmark.numAgents);
        partitions = new ArrayList<>(runBenchmark.numAgents);
        random = new Random();
        for(int i=0;i<runBenchmark.numAgents;++i){
            partitions.add(new ArrayList<>());
            agents.add(getContext().spawn(Agent.create(getContext().getSelf(),Constants.startPort+i,i+1),"agent"+i+1));
        }
        log = new CoordinatorLog(Constants.logPath);
        tm = new TransactionManager(runBenchmark.benchmarkType,runBenchmark.minInsertions,runBenchmark.maxInsertions,runBenchmark.transactionInterval);
        getContext().getSelf().tell(new NextTransaction());

        Runnable task = () -> getContext().getSelf().tell(new TimeOut());
//        // send the time out message to itself after 2 second.
        getContext().getSystem().scheduler().scheduleAtFixedRate(Duration.ofSeconds(6), Duration.ofSeconds(2), task,getContext().getExecutionContext());
        return Behaviors.same();
    }
}