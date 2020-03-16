package cs223.Common;

public enum DatabaseType {
    MySQL("mysql"),
    Postgres("postgresql");

    private String db;

    public String getDatabase()
    {
        return this.db;
    }

    DatabaseType(String db)
    {
        this.db = db;
    }
}
