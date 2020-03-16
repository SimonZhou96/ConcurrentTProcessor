package cs223.Engine;


import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;


public class Observer extends AbstractBehavior<Observer.Command> {

    private int numWorkers;
    private int numReportReceived = 0;
    private long numTransactions = 0;
    private long numNanoseconds = 0;
    private long numQueries = 0;
    private long numNanosecondsForQueries = 0;
    private final StashBuffer<Command> buffer;

    public static Behavior<Command> create(int numWorkers) {
        return Behaviors.withStash(10,s -> Behaviors.setup(c -> new Observer(c, numWorkers, s)));
    }

    interface Command{}

    public enum GracefulShutdown implements Command {
        INSTANCE
    }

    public static final class FinalReport implements Command {

        public final ActorRef<Reply> replyTo;
        public FinalReport(ActorRef<Reply> replyTo) {
            this.replyTo = replyTo;
        }
    }


    public static final class Report implements Command {

        public final long numTransactions;
        public final long numNanoseconds;
        public final long numQueries;
        public final long numNanosecondsForQueries;

        public Report(long numTransactions,long numNanoseconds,long numQueries, long numNanosecondsForQueries) {
            this.numNanoseconds = numNanoseconds;
            this.numTransactions = numTransactions;
            this.numQueries = numQueries;
            this.numNanosecondsForQueries = numNanosecondsForQueries;
        }
    }

    interface Reply {}
    public static final class Ack implements Reply {
        public Ack() {

        }
    }

    private Behavior<Command> onGracefulShutdown() {
        return Behaviors.stopped();
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(GracefulShutdown.class, this::stashOtherCommand)
                .onMessage(FinalReport.class, this::stashOtherCommand)
                .onMessage(Report.class, this::onReport)
                .build();
    }

    private Behavior<Command> stashOtherCommand(Command message) {
        // stash all other messages for later processing
        buffer.stash(message);
        return Behaviors.same();
    }

    private Behavior<Command> onReport(Report report){

        System.out.println("report "+report);
        numReportReceived++;
        numNanoseconds += report.numNanoseconds;
        numNanosecondsForQueries += report.numNanosecondsForQueries;
        numTransactions += report.numTransactions;
        numQueries += report.numQueries;
        if(numReportReceived == numWorkers){
            double avgTime = (double)numNanoseconds/numWorkers;
            numNanosecondsForQueries/=numWorkers;

            numReportReceived = 0;
            
            System.out.println(
                    "Throughput = "+numTransactions/(avgTime/1000000000)+" tps\n"+
                    "Avg response time for queries = "+numQueries/((double)numNanosecondsForQueries/1000000000)+" s\n"+
                    "total time = "+avgTime/1000000000 + " s");
            return buffer.unstashAll(
                    newReceiveBuilder()
                    .onMessage(GracefulShutdown.class, msg -> onGracefulShutdown())
                    .onMessage(FinalReport.class, this::onFinalReport)
                    .onMessage(Report.class, this::onReport)
                    .build());
        }
        return Behaviors.same();
    }


    private Behavior<Command> onFinalReport(FinalReport report){
        report.replyTo.tell(new Ack());
        return Behaviors.same();
    }


    private Observer(ActorContext<Command> context, int numWorkers, StashBuffer<Command> buffer) {
        super(context);
        this.numWorkers = numWorkers;
        this.buffer = buffer;
    }

}
