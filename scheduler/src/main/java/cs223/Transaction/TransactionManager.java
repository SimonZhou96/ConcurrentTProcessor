package cs223.Transaction;


import cs223.Common.Constants;
import cs223.PreProcess.SQLDataFileScanner;
import cs223.PreProcess.SQLFileScanner;
import cs223.PreProcess.SQLQueryFileScanner;
import javafx.util.Pair;
import scala.Tuple3;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class TransactionManager {

    private SQLFileScanner[] scanners = new SQLFileScanner[3];
    private List<Pair<Timestamp,String>> temp = new ArrayList<>(3);
    private int minInsertions;
    private int maxInsertions;
    private long transactionInterval;
    private Timestamp current;

    private static long diffScaling(long day){
        return day/(24*60);
    }



    public TransactionManager(String benchmarkType, int minInsertions, int maxInsertions, long transactionInterval) throws IOException {
        scanners[0] = new SQLDataFileScanner("/Users/mengzhou/Desktop/cs223p1/src/main/java/cs223/sortedData/observation_low_concurrency.sql");
        scanners[1] = new SQLDataFileScanner("/Users/mengzhou/Desktop/cs223p1/src/main/java/cs223/sortedData/semantic_observation_low_concurrency.sql");
        scanners[2] = new SQLQueryFileScanner("/Users/mengzhou/Desktop/cs223p1/src/main/java/cs223/sortedData/queries.txt");
        this.minInsertions = minInsertions;
        this.maxInsertions = maxInsertions;
        this.transactionInterval = transactionInterval;
        this.current = new Timestamp(0);
        for(int i=0;i<3;++i){
            temp.add(scanners[i].next());
            if(temp.get(i).getKey().before(current)){
                current = temp.get(i).getKey();
            }
        }
    }

    public Tuple3<Long,String[],Boolean> next() throws IOException {
        ArrayList<String> result = new ArrayList<>(minInsertions);
        boolean isQuery = false;
        long delay = 0;
        for(int i=0;i<maxInsertions;++i){
            int minIndex = 0;
            for(int j=1;j<3;++j){
                if(temp.get(minIndex) == null || (temp.get(j) != null && temp.get(j).getKey().before(temp.get(minIndex).getKey()))){
                    minIndex = j;
                }
            }
            if(temp.get(minIndex) == null){
                break;
            }
            long diff = 0;
            if(current.getTime()>0){
                diff = temp.get(minIndex).getKey().getTime()-current.getTime();
            }
            current = temp.get(minIndex).getKey();
            delay = diffScaling(diff);
            if(i<minInsertions || diff<=transactionInterval) {
                if(minIndex == 2){
                    if(i!=0){
                        break;
                    }
                    isQuery = true;
                    result.add(temp.get(minIndex).getValue());
                    temp.set(minIndex, scanners[minIndex].next());
                    break;
                }else {
                    result.add(temp.get(minIndex).getValue());
                    temp.set(minIndex, scanners[minIndex].next());
                }
            }else{
                break;
            }
        }
        return new Tuple3<>(delay,result.isEmpty()?null:result.toArray(new String[0]),isQuery);
    }

}
