package edu.cornell.cs.osmot.logger;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.util.GregorianCalendar;
import java.util.Calendar;
import java.util.Date;

import java.io.IOException;


import java.sql.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Cookie;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.searcher.RerankedHits;

/**
 * This class implements logging in the search engine. Most log entries
 * go to a database (queries and clicks) with status stuff to a general
 * log file.
 * 
 * @author Filip Radlinski
 * @version 2.0, October 2006
 */
public class Logger {

	private static BufferedWriter fileWriter;

	private static String filePrefix;

	private static boolean errorReported = false;
	private static boolean debug = false;
	private static int fieldMax = 255;
	
	private static Connection connection = null;
	private static String connectionURL = Options.get("LOG_DB");
	private static String sqlUsername = Options.get("LOG_USER");
	private static String sqlPassword = Options.get("LOG_PWD");
	
	static {
		debug = Options.getBool("DEBUG");
		
		filePrefix = Options.get("LOG_DIRECTORY") + "/"
			+ Options.get("LOG_PREFIX") + ".log";
		fileWriter = null;
				
		try {
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection(connectionURL, sqlUsername, sqlPassword);
		} catch (Exception e) {
			System.err.println("SQL Error: "+e.toString());
		}
		
	}
	private static String truncate(String s, int length) {
		if (s.length() <= length) {
			return s;
		} else {
			return s.substring(0, length);
		}
		
	}
	/** Pad an integer to be at least of the given length. */
	private static String pad(int i, int length) {
		String s = Integer.toString(i);
		while (s.length() < length)
			s = "0"+s;
		return s;
	}
	
	/** 
	 * Clean up input to ensure no code injections occur
	 * 
	 * @param input
	 *            The input string to clean up
	 * @param size
	 *            The maximum size that should be allowed through.
	 */
	private static String sanitizeInput(String input)
	{
		if (input == null) {
			return "null";
		}
		String badchar = "';<>\"\\";
		for(int i=0;i<badchar.length();i++)
		{
			input = input.replace(badchar.charAt(i),' ');
		}
		
		return truncate(input, fieldMax);
	}
	
	/** 
	 * Translate a set of cookies into a single string
	 * 
	 * @param allCookies
	 * 				Array of Cookies obtained from the server request
	 */
	private static String collapseCookies(Cookie[] allCookies)
	{
		// translate all cookies into a single string
		String userCookies = "";
		if(allCookies !=null)
		{
			int size = allCookies.length;
			for(int i=0;i<size;i++)
			{
				userCookies += (allCookies[i].getName() + "=" + allCookies[i].getValue());
				if(i != (size-1))
					userCookies += "&";
			}
		}
		return sanitizeInput(userCookies);
	}
	
	/** 
	 * Create a user agent string that includes a hash
	 * 
	 * @param userAgent
	 * 				UserAgent obtained from the server request
	 */
	private static String generateAgent(String userAgent)
	{
		String output = "";
		MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.update(userAgent.getBytes(),0,userAgent.length());
			BigInteger hash = new BigInteger(1,messageDigest.digest());
			output = hash.toString(16)+ ' ' + userAgent;
		} catch (NoSuchAlgorithmException e) {
			output = userAgent;
		}
		return sanitizeInput(output);
	}
	
	/** 
	 * Flush all the log files to disk
	 *
	 */
	public static void flushAll() throws IOException {

		if (fileWriter != null)
				fileWriter.flush();
	}
	
	/**
	 * Log the given message in the given mode.
	 * 
	 * @param msg
	 *            The message to log
	 * @param mode
	 *            The mode of the message.
	 */
	public static void log(String msg) {

		//long time = new Date().getTime();
		long time;
		String sTime;
		GregorianCalendar c = new GregorianCalendar();
		sTime = pad(c.get(Calendar.YEAR),4)+pad(c.get(Calendar.MONTH)+1,2)
			+ pad(c.get(Calendar.DAY_OF_MONTH),2)+pad(c.get(Calendar.HOUR_OF_DAY),2)
			+ pad(c.get(Calendar.MINUTE),2)+pad(c.get(Calendar.SECOND),2);
		time = Long.parseLong(sTime);
		
		if (debug || Options.getBool("LOG_STDERROR")) {
			System.err.println(time+" "+msg);
		}

		try {
			if (fileWriter == null) {
				try {
					String filename = filePrefix;
					fileWriter = new BufferedWriter(
							new OutputStreamWriter(new FileOutputStream(filename, true)));
				} catch (Exception e) {
					if (errorReported == false) {
						System.err.println("LOG: Exception creating log: " + e);
						errorReported = true;
					}
				}
			}

			if (fileWriter != null) {
				fileWriter.write(time + " " + msg);
				fileWriter.newLine();
				
				// Speed up logging by letting Java flush only when it needs to
				// unless we are debugging.
				if (Options.getBool("DEBUG"))
					fileWriter.flush();
			} else {
				// If log file not found, print to standard error.
				System.err.println("LOG-ALL: " + time + " " + msg);
				System.err.flush();
			}

		} catch (IOException e) {
			System.err.println(e);
		}

	}

	/**
	 * Logs a query to the database.
	 * 
	 * @param request
	 *            The request sent, allows us to get the referer and ip.
	 * @param query
	 *            The query.
	 * @param mode
	 *            The mode of the query.
	 * @param results
	 *            The results from the query.
	 * @param qid
	 *            The unique id of the query.
	 * @param session
	 *            The session id of the search session.
	 */
	public static void logQuery(HttpServletRequest request, String query, String mode,
			RerankedHits results, String qid, String session, boolean moreResults) throws IOException {

		String ip = request.getRemoteAddr();
		String referer = request.getHeader("referer");
		String userAgent = generateAgent(request.getHeader("user-agent"));
		Cookie[] allCookies = request.getCookies();
		int port = request.getServerPort();
		
		String resultsStr = "";
		
		int i = 0;

		int modeNumber = -1;
		try {
			modeNumber = Integer.parseInt(mode.substring(0,1));
            if(modeNumber == 1)
                modeNumber = Integer.parseInt(mode.substring(0,2));
		} catch (Exception e) {
			// For example, mode is "dt"
		}
		
		if (results.length() > 0) {
			if (modeNumber == 2 || mode.equals("dt") || (modeNumber == 9 && mode.length() == 2)) {
				
				resultsStr += results.doc(0).getUniqId();
				try {
					for (i = 1; i < 200; i++) {
						resultsStr += "*" + results.doc(i).getUniqId();
					} 
				} catch (Exception e) {
					// We have no way of knowing when we'll run out of results,
					// so we just wait for the exception
				}
				
			} else if (modeNumber == 1 || modeNumber == 3 || 
				   modeNumber == 4 ||  
				   modeNumber == 6 || mode.equals("mix")){

				int ranks[] = results.doc(0).getRanks();
				resultsStr += ranks[0] + "*" + ranks[1] + "*"
					+ results.doc(0).getUniqId();
				try {
					for (i = 1; i < 200 ; i++) {
						ranks = results.doc(i).getRanks();
						resultsStr += ","
							+ ranks[0]
							+ "*"
							+ ranks[1]
							+ "*"
							+ results.doc(i).getUniqId();
					}
				} catch (Exception e) {
					// We have no way of knowing when we'll run out of results,
					// so we just wait for the exception					
				}
			} else if (modeNumber == 5 || modeNumber == 7 || modeNumber == 8 || (modeNumber == 9 && mode.length() > 2) || ((mode.substring(0,1).equals("A") || mode.substring(0,1).equals("B")) && mode.length() > 2)) {
			
				int ranks[] = results.doc(0).getRanks();
				resultsStr += ranks[0] + "*" + ranks[1] + "*"
					+ results.doc(0).getUniqId() + "*"
					+ results.getSourceBit(0);
				try {
					for (i = 1; i < 200 ; i++) {
						ranks = results.doc(i).getRanks();
						resultsStr += ","
							+ ranks[0]
							+ "*"
							+ ranks[1]
							+ "*"
							+ results.doc(i).getUniqId() 
							+ "*"
							+ results.getSourceBit(i);

                        if(debug) Logger.log("SourceBit: " + results.getSourceBit(i));
					}
				} catch (Exception e) {
					// We have no way of knowing when we'll run out of results,
					// so we just wait for the exception					
				}
			
			} else {
				Logger.log("ERROR: Bad mode when logging query in Logger.logQuery: "+mode);
			}
		}

		int numResults = i;
		
        if (debug) Logger.log("Opening DB Connection.");
		try {
			if (connection.isClosed()) {
				Class.forName("com.mysql.jdbc.Driver");
				connection = DriverManager.getConnection(connectionURL, sqlUsername, sqlPassword);
			}
		} catch (Exception e) {
			System.err.println("SQL Error: "+e.toString());
		}

        if (debug) Logger.log("DB Connection Opened.");

		String sqlQuery = "";
		try {
			Statement statement = null;
			statement = connection.createStatement();
			sqlQuery = 
				"INSERT INTO " 
					+"queries "
						+"(" 
							+"date, "
							+"query, "
							+"ip, "
							+"referer, "
							+"mode, "
							+"num_results, "
							+"results, "
							+"qid, "
							+"session, "
							+"port, " 
							+"useragent, "
							+"usercookies "
						+") "
					+"VALUES "
						+"("
							+"now(),'"
							+sanitizeInput(query)+"','"
							+ip+"','"
							+sanitizeInput(referer)+"','"
							+mode+"',"
							+numResults+",'"
							+resultsStr+"','"
							+qid+"','"
							+session+"',"
							+port+",'"
							+userAgent+"','"
							+collapseCookies(allCookies)
							+"'"
						+")";
            if (debug) Logger.log("Executing SQL Query: " + sqlQuery);
			statement.executeUpdate(sqlQuery);
            if (debug) Logger.log("SQL Query done." );
		} catch (Exception e) {
			Logger.log("ERROR: SQL Exception when logging query " + sqlQuery + ": " + e.toString());
		}
	}
	
	/** Logs a click on a result 
	 * @param ip
	 *            The IP address of the user.
	 * @param session
	 *            The session id of the search session.
	 * @param qid
	 *            The unique id of the query that returned this result
	 * @param format
	 *            The format of the document requested (eg pdf, ps, txt)
	 * @param doc
	 *            The unique identifier of the document
	 * @return The log entry for a click that will go directly to the document.
	 */
	public static void logClick(HttpServletRequest request, String mode, String session, String qid, String paper, String format) {

		String ip = request.getRemoteAddr();
		int port = request.getServerPort();
		String userAgent = generateAgent(request.getHeader("user-agent"));
		Cookie[] allCookies = request.getCookies();
		
		// Mode is in the qid, so we use that one. Note that a user might have let their
		// session lapse before clicking, getting a new session and hence new mode for the click.
		// But the click should count toward the original query irrespective of this new "mode"
		// (which is only relevant for queries, not clicks anyway)
		String newMode = qidMode(qid);
        if(debug) Logger.log("Old mode: " + mode + " New mode: " + newMode);
		if (newMode != null)
			mode = newMode;
				
        if(debug) Logger.log("Logging Click " + " Mode: " + mode + " Qid: " + qid + " Paper: " + paper + " Format: " + format);

		String sqlQuery = "INSERT INTO "
					+"clicks "
						+"( "
							+"date, "
							+"mode, "
							+"ip, "
							+"session, "
							+"qid, "
							+"paper, "
							+"format, "
							+"port, "
							+"useragent, "
							+"usercookies "
						+") "
					+"VALUES "
						+"( "
							+"now(),'"
							+mode+"','"
							+ip+"','"
							+sanitizeInput(session)+"','"
							+sanitizeInput(qid)+"','"
							+paper+"','"
							+format+"',"
							+port+",'"
							+userAgent+"','"
							+collapseCookies(allCookies)
							+"'"
						+")";
		try {
			Statement statement = null;
			statement = connection.createStatement();
            if (debug) Logger.log("Executing SQL Query: " + sqlQuery);
			statement.executeUpdate(sqlQuery);
            if(debug) Logger.log("Logging done.");
			
		} catch (Exception e) {
			log("ERROR: SQL Exception when logging click: "+sqlQuery+": "+e.toString());
		}
			
		//ResultSet rs = null;
		//rs = statement.executeQuery()
		//rs.close();		
	}
	
	/**
	 * Logs a request for a page. This is recorded in the general log file.
	 * 
	 * @param request
	 *            The request.
	 * @param mode
	 *            The mode of the search that will be done.
	 */
	public static void logRequest(HttpServletRequest request, String mode) {
		String query = request.getQueryString();
		if (query == null)
			query = "null";
		String logStr = new Date().toString() + " " + mode + " "
				+ request.getRequestURL() + " " + query + " "
				+ request.getRemoteAddr() + " ";
		String headers[] = { "Referer", "User-Agent" };
		for (int i = 0; i < headers.length; i++) {
			String output = request.getHeader(headers[i]);
			if (output == null || output.length() < 2) 
				output = "null"; 
			logStr += output + " ";
		}
		log(logStr);
	}
	
	/** Logs addition to the paper database in the main database. */
	
	public static void logIndexUpdate(String paper, int success) {
		
		String sqlQuery = "INSERT INTO additions (paper, date, success) VALUES ( "
							+"'"+paper+"', "
							+"now(), "
							+success
						+")";
		try {
			Statement statement = null;
			statement = connection.createStatement();
			statement.executeUpdate(sqlQuery);
			
		} catch (Exception e) {
			log("ERROR: SQL Exception when logging click: "+sqlQuery+": "+e.toString());
		}

	}
	
	/** 
	 * Get the mode that is encoded in this qid
	 */
	public static String qidMode(String qid) {
		// The new way (mode after the time)
		if (qid.length() > 15) {
            int mode_start = qid.indexOf('_');
            if(mode_start < 0) return null;
			String newMode = qid.substring(13,mode_start);
            return newMode;

            /*
			if (newMode.charAt(0) >= '1' && newMode.charAt(0) <= '6' &&
					newMode.charAt(1) >= 'a' && newMode.charAt(0) <= 'd')
				return newMode;
			if (newMode.equals("dt"))
				return newMode;			
                */
		}
		
        /*
		// TODO: Check if I need to fix this to work when models change.
		// The old way (mode after the identifiers, so position changes)
		if (qid.length() > 25) {
			String newMode = qid.substring(23,25);
			if (newMode.charAt(0) >= '1' && newMode.charAt(0) <= '6' &&
					newMode.charAt(1) >= 'a' && newMode.charAt(0) <= 'd')
				return newMode;
			if (newMode.equals("dt"))
				return newMode;
		}
				
        */
		return null;
	}

	
}
