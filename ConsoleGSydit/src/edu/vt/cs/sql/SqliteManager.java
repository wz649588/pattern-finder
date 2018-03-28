package edu.vt.cs.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class SqliteManager {
	public static Connection getConnection() {
		Connection connection = null;
		try {
			connection = DriverManager.getConnection("jdbc:sqlite:/Users/Vito/Documents/VT/2016fall/SE/patternData.sqlite");
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return connection;
	}
}
