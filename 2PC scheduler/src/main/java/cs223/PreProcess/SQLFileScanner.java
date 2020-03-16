package cs223.PreProcess;

import javafx.util.Pair;

import java.io.IOException;
import java.sql.Timestamp;

public interface SQLFileScanner {

    void sort(String outputPath) throws IOException;

    Pair<Timestamp,String> next() throws IOException;

}
