package cs223.Common;

public enum LogType {
    START("START"),
    PREPARE("PREPARE"),
    COMMIT("COMMIT"),
    ABORT("ABORT"),
    COMPLETE("COMPLETE"),
    NOTFOUND("NOTFOUND"),
    YES("YES"),
    NO("NO");

    private String name;
    LogType(String name){
        this.name = name;
    }
    public String getName(){
        return this.name;
    }
}
