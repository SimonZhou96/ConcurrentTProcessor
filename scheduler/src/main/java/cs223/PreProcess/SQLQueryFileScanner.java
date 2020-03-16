package cs223.PreProcess;

import javafx.util.Pair;

import java.io.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SQLQueryFileScanner implements SQLFileScanner {

    private BufferedReader reader;

    public SQLQueryFileScanner(String path) throws FileNotFoundException {
        reader = new BufferedReader(new FileReader(path));
    }

    public void sort(String outputPath) throws IOException {
        List<String> content = new ArrayList<>();
        List<Pair<Timestamp,Integer>> indices = new ArrayList<>();
        Pair<Timestamp,String> t;
        while((t = next())!=null){
            indices.add(new Pair<>(t.getKey(),content.size()));
            content.add(t.getValue());
        }
        indices.sort(Comparator.comparing(Pair::getKey));
        File f = new File(outputPath);
        f.getParentFile().mkdirs();
        f.createNewFile();
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath));
        for (Pair<Timestamp, Integer> index : indices) {
            writer.write(index.getKey().toLocalDateTime()+",\"");
            writer.newLine();
            writer.write(content.get(index.getValue()));
            writer.write("\"\n");
        }
        writer.close();
    }

    public Pair<Timestamp, String> next() throws IOException {
        String line = reader.readLine();
        if(line == null){
            return null;
        }
        if(!line.endsWith(",\"")){
            System.out.println(line);
            throw new IOException();
        }else{
            LocalDateTime dt = LocalDateTime.parse(line.substring(0,line.indexOf(',')));
            Timestamp t = Timestamp.valueOf(dt);
            StringBuilder query = new StringBuilder();
            while(!(line = reader.readLine()).startsWith("\"")){
                query.append(line).append("\n");
            }
            return new Pair<>(t,query.toString());
        }
    }

}
