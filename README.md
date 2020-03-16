# concurrent transaction simulator

Department: Informatics and Computer Science

Author: Shengquan Ni, Meng Zhou, Xiangde Zeng

Platform needed:
* Java: JDK 13 or above
Extra Library: 
* Javafx (The latest JDK no long includes javafx package)
* MySQL driver (https://dev.mysql.com/downloads/connector/j/)
* Maven

## Introduction
The first step of the project is to build a concurrent transaction simulator that mimics real life transaction workload of an IoT based system on a database system. The simulator should be able to create and process multithreaded transactions on multiple database systems.

## Way to Run Txn scheduler!
To setup, go to /cs223/Common/Constants.java to change the related configuration. 
* dataRootPath is the path where you place the data and queries needed. 
* dbType is set to indicate which database you are using. It could be either DatabaseType.MySQL or DatabaseType.Postgres.
* url is the address of the database. e.g. "//localhost:3306/cs223"
* username and passwork: used to access the database
* isolationLevel: There are five options of isolation level:
	-TRANSACTION_NONE = 0
	-TRANSACTION_READ_UNCOMMITTED = 1
	-TRANSACTION_READ_COMMITTED = 2
	-TRANSACTION_REPEATABLE_READ = 4
	-TRANSACTION_SERIALIZABLE = 8
* maxthread is the maximum number of threads that allow to access the database. (In other words, the maximum number of worker)
* minInsertions is the minmum number of insertion queries in one transaction when handling the data insertion.
* maxInsertions is the maximum number of insertion queries in one transaction when handling the data insertion.
* benchmarkType indicates the type of benchmark. It could be low_concurrency or high_concurrency

- Run the program
Use Intellij (Recommended) or other Java IDE to run the program: After import the project, run App.java directly.

## Way to implement the measurement of the workload of data when your PC is really bad!!
Priority Mailbox allows you to set the sampling be the highest priority. So set the time rate to sample the number of transactions before executing the sql sentences is possible. In this case, we sampled the number of transactions per MINUTE.
you can set whatever the time interval you like.

## Way to Run 2PC scheduler!
-Configuration Instruction:
To setup, go to /cs223/Common/Constants.java to change the related configuration. 
* dataRootPath is the path where you place the data and queries needed. 
* url is the address of the database. e.g. "/cs223". (Please note that we initially set the address to localhost)
* username and password: used to access the database
* minInsertions is the minmum number of insertion queries in one transaction when handling the data insertion.
* maxInsertions is the maximum number of insertion queries in one transaction when handling the data insertion.
* benchmarkType indicates the type of benchmark. It could be low_concurrency or high_concurrency
* startPort: In this project, we assume the port numbers of each database server are consecutive. e.g. three database server's port would be: 5432, 5433, 5434
* logPath is the path where you want to store the log file.
* Before starting the server, head to postgresql.conf and set the max_prepared_transactions to a non-zero value. (2 is enough).
* System failure can be simulated by changing the value of DOWNBEFOREYES OR COORDINATORRANDOMCASE. The corresponding status code can be found in the Constant.java. You can change both of the values in the Coordinator.onNextTransaction(). Since we do not make an experiment of both node shutdown situation, please DO NOT set both the coordinator and agent fail at the same time. (In other word, at lease one of these variances should be 0.)

- Run the program
Use Intellij (Recommended) or other Java IDE to run the program: After import the project, run App.java directly.
