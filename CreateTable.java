import java.io.*;
import java.sql.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * File: CreateTable.java
 * Author: Gabe Venegas
 * Course: CSC 460
 * Desc: CSV scrubber for highwayrail data, inserted to db via JDBC.
 * We expect a certain format of each line (tuple) in CSV:
 * 
 * railroad_code                VARCHAR2(30)
 * incident_number              VARCHAR2(30)
 * grade_crossing_id            VARCHAR2(30)
 * date_time                    TIMESTAMP     <-- date,time values combined
 * state_name                   VARCHAR2(30)
 * highway_user                 VARCHAR2(30)
 * temperature                  INT
 * visibility                   VARCHAR2(30)
 * weather_condition            VARCHAR2(30)
 * number_of_locomotive_units   INT
 * number_of_cars               INT
 */
public class CreateTable {

    private static String csvToSql(String csvRow) {

        DateTimeFormatter formatTimeCSV = DateTimeFormatter.ofPattern("hh:mm a");
        DateTimeFormatter formatTimeSQL = DateTimeFormatter.ofPattern("HH:mm:ss");

        // Split line for parse
        String[] parts = csvRow.split(",");

        // Validate & transform each field
        for (int i = 0; i < parts.length; i++) {

            // if empty, set NULL
            if (parts[i].isEmpty() && !(i == 3 || i == 4)) {
                parts[i] = "NULL";
            }
            // (timestamp) date & time -> date_time
            else if (i == 3) {
                // good case
                if (!parts[3].isEmpty() && !parts[4].isEmpty()) {
                    parts[3] = "TIMESTAMP '" + LocalDate.parse(parts[3]).toString() + " "
                            + LocalTime.parse(parts[4], formatTimeCSV).format(formatTimeSQL) + "'";
                    parts[4] = "";
                }
                // if either are bad, we can't store timestamp
                else {
                    parts[3] = "NULL";
                    parts[4] = "";
                }
                i++; // skip over time, was merged into date
            }
            // (int) tempurature, num locomotive units, num cars
            else if (Arrays.asList(7, 10, 11).contains(i)) {
                parts[i] = Integer.toString(Integer.parseInt(parts[i]));
            }
            // (string) everything else
            else {
                parts[i] = "'" + parts[i] + "'";
            }
        }

        // Assemble & return tuple
        return "(" + String.join(", ", parts).replace(", ,", ",") + ")";
    }

    public static void initTable(String schemaName, String tableName, Connection dbconn) throws SQLException {

        // Ensure schema exists
        // try (Statement stmt = dbconn.createStatement()) {
        //     stmt.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        // }
        // System.out.println("Success \"" + schemaName + "\" schema creation!");
        // ^^^ not apparently needed for Oracle

        // Ensure table exists
        try (Statement stmt = dbconn.createStatement()) {
            // H2
            // String createSql = "CREATE TABLE IF NOT EXISTS " + schemaName + ".\"" + tableName + "\" "
            // Oracle
            String createSql = "CREATE TABLE " + schemaName + ".\"" + tableName + "\" "
                    + "("
                    + "railroad_code VARCHAR2(30),"
                    + "incident_number VARCHAR2(30),"
                    + "grade_crossing_id VARCHAR2(30),"
                    + "date_time TIMESTAMP,"
                    + "state_name VARCHAR2(30),"
                    + "highway_user VARCHAR2(30),"
                    + "temperature INT,"
                    + "visibility VARCHAR2(30),"
                    + "weather_condition VARCHAR2(30),"
                    + "number_of_locomotive_units INT,"
                    + "number_of_cars INT"
                    + ")";
            stmt.execute(createSql);
        }
        System.out.println("Success " + schemaName + ".\"" + tableName + "\" table creation!");

        // For Oracle, allow everyone to read tables
        try (Statement stmt = dbconn.createStatement()) {
            stmt.execute("GRANT SELECT ON " + schemaName + ".\"" + tableName + "\" TO PUBLIC");
        }
        System.out.println("Success " + schemaName + ".\"" + tableName + "\" table is publically accessible!");
    }

    public static void populateTable(String schemaName, String tableName, String csvpath, Connection dbconn)
            throws SQLException {

        final String insertSql = "INSERT INTO " + schemaName + ".\"" + tableName + "\" "
                + "(railroad_code, incident_number, grade_crossing_id, date_time, state_name, highway_user, temperature, visibility, weather_condition, number_of_locomotive_units, number_of_cars)"
                + " VALUES ";

        String line = "";
        int lineCount = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(csvpath));
                Statement stmt = dbconn.createStatement()) {

            dbconn.setAutoCommit(false);

            br.readLine(); // skip headers
            
            System.out.println("Committing CSV rows to SQL...");
            while ((line = br.readLine()) != null) {

                stmt.addBatch(insertSql + CreateTable.csvToSql(line));
                lineCount++;

                // batch insertions every 200 lines
                if (lineCount % 200 == 0) {
                    stmt.executeBatch();
                    dbconn.commit();
                }
            }

            // commit last batch
            stmt.executeBatch();
            dbconn.commit();
            System.out.println("Successfully commit " + lineCount + " CSV rows to " + schemaName + ".\"" + tableName + "\"");

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            dbconn.setAutoCommit(true);
        }
    }

    public static void main(String[] args) {

        if (args.length != 4) {
            System.out.println("\nUsage: java CreateTable <schemaName> <csvpath> <driverClass> <dbURL>\n\n"
                    + "\t<schemaName>  : Name of schema which tables are created\n"
                    + "\t                (e.g. \"YOUR_USERNAME\")\n\n"
                    + "\t<csvpath>     : A csv matching highwayrail*.csv\n"
                    + "\t                (e.g. \"highwayrail2025.csv\")\n\n"
                    + "\t<driverClass> : JDBC driver classname\n"
                    + "\t                (e.g. \"oracle.jdbc.OracleDriver\")\n\n"
                    + "\t<dbURL>       : Database URL\n"
                    + "\t                (e.g. \"jdbc:oracle:thin:YOUR_USERNAME/YOUR_PASSWORD@HOST:PORT:oracle\")\n");
            System.exit(-1);
        }

        // Get user args
        String schemaName = args[0];
        String csvpath = args[1];
        String driverClass = args[2];
        String dbURL = args[3];

        // Load the JDBC driver by init its base class
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            System.err.println("*** ClassNotFoundException:  "
                    + "Error loading JDBC driver \"" + driverClass + "\"\n"
                    + "\tPerhaps the driver is not on the Classpath?");
            System.exit(-1);
        }

        // Check CSV valid
        if (!csvpath.matches("^highwayrail.{4}\\.csv$") || !new File(csvpath).exists()) {
            System.out.println(
                    "Invalid CSV file \"" + csvpath + "\". Should be valid file of format: \"highwayrail????.csv\"");
            System.exit(-1);
        }

        // Get year (tablename)
        String tableName = csvpath.substring(csvpath.length() - 8, csvpath.length() - 4);

        // Perform insertion of rows
        try (Connection dbconn = DriverManager.getConnection(dbURL)) {

            CreateTable.initTable(schemaName, tableName, dbconn);
            CreateTable.populateTable(schemaName, tableName, csvpath, dbconn);

        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("*** SQLException:");
            System.err.println("\tMessage:   " + e.getMessage());
            System.err.println("\tSQLState:  " + e.getSQLState());
            System.err.println("\tErrorCode: " + e.getErrorCode());
            System.exit(-1);
        }

    }
}


