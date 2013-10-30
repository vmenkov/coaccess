package edu.cornell.cs.osmot.logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import edu.cornell.cs.osmot.options.Options;

/**
 * This class wraps the MySQL connection class and will automatically connect to the database
 * specified in the config file when instantiated.
 * 
 * @author Tobias Schnabel
 * @version 1.0, May 2012
 */
public class SQLConnection {
	
	/** The connection object */
	private  Connection con = null;
	
	/** The url to the databse */
	final String url = Options.get("LOG_DB");
	
	/** The username. */
	final String user = Options.get("LOG_USER");
	
	/** The password. */
	final String password = Options.get("LOG_PWD");
	
	/**
	 * Instantiates a new SQL connection.
	 */
	public SQLConnection() {
		try   {
			con = DriverManager.getConnection(url, user, password);
		} catch (SQLException ex) {
			Logger.log("ERR: SQL Exception " + ex.getMessage());
		}
	}

	/**
	 * Gets the current SQL connection
	 *
	 * @return the connection object
	 */
	public Connection getCon() {
		return con;
	}



}