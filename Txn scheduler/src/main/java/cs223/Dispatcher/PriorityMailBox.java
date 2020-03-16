package cs223.Dispatcher;

import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.dispatch.Envelope;
import akka.dispatch.PriorityGenerator;
import akka.dispatch.UnboundedPriorityMailbox;
import com.typesafe.config.Config;

import java.util.Comparator;

public class PriorityMailBox extends UnboundedPriorityMailbox{
        public PriorityMailBox(ActorSystem.Settings settings, Config config) {
            // Create a new PriorityGenerator, lower prio means more important
            super(
                    new PriorityGenerator() {
                        @Override
                        public int gen(Object message) {
                            message = message.toString().split("[$@]")[1];

                            if (message.equals("ReportEveryInterval")) {
                                return 1; // 'highpriority messages should be treated first if possible
                            }
                            if (message.equals("WorkerTask"))
                                return 2;
                            if (message.equals(PoisonPill.getInstance()))
                                return 3;
                            if (message.equals("End")) {
                                return 5;
                            }
                            return 4;
                        }
                    });
        }
}
