package cs223.Common;

import java.io.*;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.HashMap;

import javafx.util.Pair;

public class CoordinatorLog implements LogScanner {
    private BufferedReader reader;
    private int current_id = -1;
    private boolean isComplete = false;
    private String filename;
    private BufferedWriter writer;
    private LogType currentState;

    public CoordinatorLog(String path) {
        filename =  path + "/coordinator_log";
        File f = new File(filename);
        try{
            if (f.exists()){
                f.delete();
            }
            f.getParentFile().mkdirs();
            f.createNewFile();
            writer = new BufferedWriter(new FileWriter(filename,true));
        }catch (IOException e){}
    }

    public void recordNew(int gid) {
    try {
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String record = LogType.START+","+ts.toLocalDateTime() + "," + gid;
        writer.write(record);
        writer.newLine();
        writer.flush();
        isComplete = false;
        current_id = gid;
        currentState = LogType.START;
    }catch (IOException e){}

    }

    public boolean checkComplete() {
        String lastLine = "";
        String currentLine = "";

        try{
            reader = new BufferedReader(new FileReader(filename));
            while(true){
                lastLine = currentLine;
                currentLine = reader.readLine();
                if(currentLine == null) break;
                if(currentLine.contains("START"))
                    this.current_id = Integer.valueOf(currentLine.split(",")[2]);
            }
            isComplete = lastLine.contains("COMPLETE");
        }catch (IOException e){}
        return isComplete;
    }

    public LogType checkStatus(int gid){
        if(gid == current_id) return currentState;
        boolean found = false;
        String lastLine = "";
        String currentLine = "";
        try{
            reader = new BufferedReader(new FileReader(filename));
            while(true){
                lastLine = currentLine;
                currentLine = reader.readLine();
                if(currentLine == null) break;
                if(currentLine.contains("START")){
                    int id = Integer.valueOf(currentLine.split(",")[2]);
                    if(id == gid) found = true;
                    continue;
                }
                if(currentLine.contains("START") && found){
                    String status = lastLine.split(",")[0];
                    return LogType.valueOf(status);
                }
            }
        } catch (IOException e){}
        if(!found) return LogType.NOTFOUND;
        String status = lastLine.split(",")[0];
        return LogType.valueOf(status);
    }
    public void writeLog(LogType type,int gid){
        try{
            Timestamp ts = new Timestamp(System.currentTimeMillis());
            String record = type.getName()+","+ts.toLocalDateTime() + "," + current_id;
            writer.write(record);
            writer.newLine();
            writer.flush();
            if(type == LogType.COMPLETE) isComplete = true;
            currentState = type;
        }catch (IOException e){}
    }

    public int getCurrentID(){
        return this.current_id;
    }

    public void saveFile(){
        try {
            writer.close();
        }catch (IOException e){}
    }
}
