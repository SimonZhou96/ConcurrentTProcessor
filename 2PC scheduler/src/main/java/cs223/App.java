package cs223;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import cs223.Common.Constants;
import cs223.Engine.Coordinator;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;


public class App {

    public static void main( String args[] ) throws ClassNotFoundException, InterruptedException, ExecutionException {
        Class.forName("org.postgresql.Driver");

        final ActorSystem<Coordinator.Command> system =
                ActorSystem.create(Coordinator.create(), "engine");
        CompletionStage<Coordinator.Reply> result = AskPattern.ask(system, replyTo -> new Coordinator.RunBenchmark(Constants.benchmarkType,Constants.minInsertions,Constants.maxInsertions,Constants.transactionInterval,replyTo,Constants.numAgents), Duration.ofHours(120),system.scheduler());
        result.toCompletableFuture().get();


        system.terminate();
    }
}
