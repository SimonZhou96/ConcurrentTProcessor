package cs223.Common;

import javafx.util.Pair;
import java.io.IOException;
import java.sql.Timestamp;

public interface LogScanner {
    void writeLog(LogType type, int gid);
    LogType checkStatus(int gid);
    int getCurrentID();
    void saveFile();
}

