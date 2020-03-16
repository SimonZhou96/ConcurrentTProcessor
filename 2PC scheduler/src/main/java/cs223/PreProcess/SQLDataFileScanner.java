package cs223.PreProcess;

import javafx.util.Pair;

import java.io.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLDataFileScanner implements SQLFileScanner {

    private BufferedReader reader;
    private Pattern dateFormat = Pattern.compile("'[^,]+'");

    public SQLDataFileScanner(String path) throws FileNotFoundException {
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
            writer.write(content.get(index.getValue()));
            writer.newLine();
        }
        writer.close();
    }

    public Pair<Timestamp,String> next() throws IOException {
        while(true) {
            String line = reader.readLine();
            if(line == null){
                return null;
            }else if(line.startsWith("SET")){
                return new Pair<>(new Timestamp(0),line);
            }else if(line.startsWith("INSERT")){
                Matcher m = dateFormat.matcher(line);
                while(m.find()){
                    String i = m.group();
                    if(i.startsWith("'")){
                        try{
                            return new Pair<>(Timestamp.valueOf(i.substring(1,i.length()-1)),line);
                        }catch(Exception ignored){

                        }
                    }
                }
            }else{
                //System.out.println(line);
            }
        }
    }

}