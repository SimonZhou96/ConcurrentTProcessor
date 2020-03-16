package cs223.Common;

import com.mysql.cj.jdbc.MysqlXid;
import javax.transaction.xa.Xid;
import java.io.*;
import java.sql.Timestamp;


public class AgentLog implements LogScanner {
    private BufferedReader reader;
    private int current_id = -1;
    private int agent_id = -1;
    private int tid = -1;
    private boolean isComplete = false;
    private String filename = "";
    private BufferedWriter writer;
    private LogType currentState;
    public AgentLog(String path, int agent_id) {
        this.agent_id = agent_id;
        filename = path + "/agent_"+agent_id+"_log";
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

    public void recordNew(int gid)  {
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

    public boolean checkComplete(){
        try{
            reader = new BufferedReader(new FileReader(filename));
            String lastLine = "";
            String currentLine = "";
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
            if(type == LogType.PREPARE){
                record += ","+gid;
                this.tid = gid;
            }
            currentState = type;
//            System.out.println("current stste: "+currentState.getName());
            writer.write(record);
            writer.newLine();
            writer.flush();
            if(type == LogType.COMPLETE) isComplete = true;
        }catch (IOException e){}
    }

    public int getCurrentID(){
        return this.current_id;
    }

    public Xid getXID(){
        Xid xid = new MysqlXid(intToByteArray(current_id),intToByteArray(agent_id),tid);
        return xid;
    }
    public void saveFile(){
        try {
            writer.close();
        }catch (IOException e){}
    }

    private static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
    }
}
