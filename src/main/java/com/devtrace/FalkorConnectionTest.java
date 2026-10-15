package com.devtrace;

import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;

public class FalkorConnectionTest {

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 6379;
        String username = "default";
        String password = System.getenv("FALKORDB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "Set the FALKORDB_PASSWORD environment variable first."
            );
        }

        try (Driver driver = FalkorDB.builder()
                .host(host)
                .port(port)
                .credentials(username, password)
                .build()) {

            Graph graph = driver.graph("devtrace");

            ResultSet result = graph.query(
                "MATCH (a:Service)-[:DEPENDS_ON]->(b:Service) " +
                "RETURN a.name AS source, b.name AS target"
            );

            for (Record record : result) {
                System.out.println(
                    record.getString("source") + " -> " +
                    record.getString("target")
                );
            }

            System.out.println("Java-to-FalkorDB query succeeded.");

        } catch (Exception e) {
            System.err.println("Java-to-FalkorDB test failed:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}