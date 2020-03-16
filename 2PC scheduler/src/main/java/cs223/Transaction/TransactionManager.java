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
import java.io.File;

public class TransactionManager {

    private SQLFileScanner scanner;
    private Pair<Timestamp,String> temp;
    private int minInsertions;
    private int maxInsertions;
    private long transactionInterval;
    private Timestamp current;

    private static long diffScaling(long day){
        return day/(24*60);
    }

    public TransactionManager(String benchmarkType, int minInsertions, int maxInsertions, long transactionInterval) throws IOException {
        File f = new File(Constants.dataRootPath+"\\sorted_data\\"+benchmarkType+"\\observation_"+benchmarkType+".sql");
        if (!f.exists()){
            System.out.println("Pre processing the data...");
            SQLFileScanner s = new SQLDataFileScanner(Constants.dataRootPath+"\\data\\"+benchmarkType+"\\observation_"+benchmarkType+".sql");
            s.sort(Constants.dataRootPath+"\\sorted_data\\"+benchmarkType+"\\observation_"+benchmarkType+".sql");
        }
        scanner = new SQLDataFileScanner(Constants.dataRootPath+"\\sorted_data\\"+benchmarkType+"\\observation_"+benchmarkType+".sql");
        this.minInsertions = minInsertions;
        this.maxInsertions = maxInsertions;
        this.transactionInterval = transactionInterval;
        this.current = new Timestamp(0);
        temp = scanner.next();
    }

    public String[] next() throws IOException {
        ArrayList<String> result = new ArrayList<>(minInsertions);
        for(int i=0;i<maxInsertions;++i){
            if(temp.getValue().contains("SET")){
                temp = scanner.next();
                i--;
                continue;
            }
            long diff = 0;
            if(current.getTime()>0){
                diff = temp.getKey().getTime()-current.getTime();
            }
            current = temp.getKey();
            if(i<minInsertions || diff<=transactionInterval) {
                    result.add(temp.getValue());
                    temp = scanner.next();
            }else{
                break;
            }
        }
        return result.isEmpty()?null:result.toArray(new String[0]);
    }

}
