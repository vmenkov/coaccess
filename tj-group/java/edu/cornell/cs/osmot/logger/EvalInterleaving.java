package edu.cornell.cs.osmot.logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * This class provides all the functionality needed to evaluate the interleaving of two
 * rankings A and B. It will retrieve the queries along the with their clicks from the
 * database and count the number of clicks that each ranking got.
 * 
 * @author Tobias Schnabel
 * @version 1.0, May 2012
 */
public class EvalInterleaving {
	
	/**
	 * Returns a sorted TreeMap of the evaluation results with keys representing days.
	 * The evaluation will be query based, so if ranking A has more clicks than ranking B with a query,
	 * it'll count as one win for A.
	 *
	 * @return evaluation results sorted by day 
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException 
	 */

	//private static String dateTimeToStartEval = "2013-01-14 20:20:00";
	//private static String dateTimeToStartEval = "2013-02-06 16:58:00";
	private static String dateTimeToStartEval = "2013-03-05 20:18:00";

	public static TreeMap<String, ArrayList<Integer>> getEvaluation() throws IOException, SQLException {
		HashMap<String, ArrayList<Integer>> days = new HashMap<String, ArrayList<Integer>>();
		try {
			ResultSet queries = getQueryData();
	
			int winsA = 0;
			int winsB = 0;
			int winsBoth = 0;
			
			// As long as there are queries
			while (queries.next()) {	
				String queryId = queries.getString(1);
				String results = queries.getString(2);
				String clicks = queries.getString(3);
				String date = queries.getString(4).split(" ")[0];
				
				int[] pref = getPreferredRanking(queryId, results, clicks);
				int clicksA = pref[0];
				int clicksB = pref[1];
				
				if (!days.containsKey(date)) {
					ArrayList<Integer> k = new ArrayList<Integer>();
					k.add(0);
					k.add(0);
					k.add(0);
					days.put(date, k);
				}
				winsA = days.get(date).get(0);
				winsB = days.get(date).get(1);
				winsBoth = days.get(date).get(2);
				// A ranking wins if it got more clicks than the other one
				winsA += ((clicksA > clicksB) ? 1 : 0);
				winsB += ((clicksB > clicksA) ? 1 : 0);
				winsBoth += ((clicksB == clicksA) ? 1 : 0);
				days.get(date).set(0, winsA);
				days.get(date).set(1, winsB);
				days.get(date).set(2, winsBoth);
			}
		} catch (NullPointerException e) {
		}
		
		// Build table sorted by date
		TreeMap<String, ArrayList<Integer>> sortedMap = new TreeMap<String, ArrayList<Integer>>(days);
		return sortedMap;
	}

	
	
	/**
	 * Returns a sorted TreeMap of the evaluation results with keys representing days.
	 * The evaluation will be click-based, so we will just count all clicks that a ranking got.
	 *
	 * @return evaluation results sorted by day 
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException
	 */
	public static TreeMap<String, ArrayList<Integer>> getEvaluationClicks() throws IOException, SQLException {
		HashMap<String, ArrayList<Integer>> days = new HashMap<String, ArrayList<Integer>>();
		try {
			ResultSet queries = getQueryData();
			
			int winsA = 0;
			int winsB = 0;
			int winsBoth = 0;
			
			// As long as there are queries
			while (queries.next()) {	
				String queryId = queries.getString(1);
				String results = queries.getString(2);
				String clicks = queries.getString(3);
				String date = queries.getString(4).split(" ")[0];
				
				int[] pref = getPreferredRanking(queryId, results, clicks);
				int clicksA = pref[0];
				int clicksB = pref[1];
				int clicksBoth = pref[2];
				
				if (!days.containsKey(date)) {
					ArrayList<Integer> k = new ArrayList<Integer>();
					k.add(0);
					k.add(0);
					k.add(0);
					days.put(date, k);
				}
				winsA = days.get(date).get(0);
				winsB = days.get(date).get(1);
				winsBoth = days.get(date).get(2);
				// Just add all clicks
				winsA += clicksA - clicksBoth;
				winsB += clicksB - clicksBoth;
				winsBoth += clicksBoth;
				days.get(date).set(0, winsA);
				days.get(date).set(1, winsB);
				days.get(date).set(2, winsBoth);
			}
		} catch (NullPointerException e) {
		}
		// Build table sorted by date
		TreeMap<String, ArrayList<Integer>> sortedMap = new TreeMap<String, ArrayList<Integer>>(days);
		return sortedMap;
	}
	
	/**
	 * Returns a table represented by a two nested array array lists with the date, query string, query id and all 
	 * clicks of a query. The clicks will also have 
	 * original rankings.
	 *
	 * @return table with one row for each query
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException
	 */
	public static ArrayList<ArrayList<String>> getEvaluationExplained() throws IOException, SQLException {		
		PreparedStatement cacheQueries_statement = null;
		ResultSet queries = null;
		
		// Retrieve clicks from database
		SQLConnection database = new SQLConnection();

		cacheQueries_statement = database
				.getCon()
				.prepareStatement(
						"SELECT queries.qid, queries.results, GROUP_CONCAT(clicks.paper), queries.date, queries.query "
								+ "FROM queries "
								+ "INNER JOIN clicks USING(qid, mode) "
								+ "WHERE "
								+ "	queries.date > '" + dateTimeToStartEval + "' AND "
								+ "	clicks.date > '" + dateTimeToStartEval + "' AND "
								+ " clicks.format = 'abs' AND "
								+ "	queries.num_results > 0 AND "
								+ "	queries.mode = 'mix' "
								+ "GROUP BY "
								+ "	clicks.qid "
								+ "ORDER BY "
								+ " date DESC");

		
		queries = cacheQueries_statement.executeQuery();
		
		ArrayList<ArrayList<String>> result = new ArrayList<ArrayList<String>>();
		// As long as there are queries
		while (queries.next()) {	
			String queryId = queries.getString(1);
			String results = queries.getString(2);
			String clicks = queries.getString(3);
			String date = queries.getString(4);
			String query = queries.getString(5);
			ArrayList<String> queryEntry = new ArrayList<String>();
			queryEntry.add(date);			
			queryEntry.add(query);
			queryEntry.add(queryId);
			queryEntry.add(getClicksExplained(queryId, results, clicks));
			result.add(queryEntry);
		}
		return result;
	}
	
	
	/**
	 * Gets the queries along with their clicks from the MySQL database.
	 *
	 * @return a MySQL result set holding the data
	 * @throws SQLException
	 */
	public static ResultSet getQueryData() throws SQLException {
		PreparedStatement cacheQueries_statement = null;

		SQLConnection database = new SQLConnection();
		
		// Retrieve clicks from database
		cacheQueries_statement = database
				.getCon()
				.prepareStatement(
						"SELECT queries.qid, queries.results, GROUP_CONCAT(clicks.paper), queries.date "
								+ "FROM queries "
								+ "INNER JOIN clicks USING(qid, mode) "
								+ "WHERE "
								+ "	queries.date > '" + dateTimeToStartEval + "' AND "
								+ "	clicks.date > '" + dateTimeToStartEval + "' AND "
								+ " clicks.format = 'abs' AND "
								+ "	queries.num_results > 0 AND "
								+ "	queries.mode = 'mix' "
								+ "GROUP BY "
								+ "	clicks.qid");

		return cacheQueries_statement.executeQuery();
	}
	
	
	/**
	 * Write the evaluation result to a file. The result will get appended 
	 * if the file already exists.
	 *
	 * @param fileName the file name
	 * @param noUpdates the no updates
	 * @throws SQLException
	 */
	public static synchronized void writeToFile(String fileName, int noUpdates) throws SQLException {
		// Open output file
		BufferedWriter out;
		
		try {
			out = new BufferedWriter(new FileWriter(fileName, true));
			TreeMap<String, ArrayList<Integer>> results = getEvaluation();
			int winsA = 0;
			int winsB = 0;
			int winsBoth = 0;
			for (ArrayList<Integer> result : results.values()) {
				winsA += result.get(0);
				winsB += result.get(1);
				winsBoth += result.get(2);
			}
			out.append(noUpdates + " updates: " + winsA + " wins baseline, " + winsB + " wins learned, " + winsBoth + " ties\n");
			out.close();
		} catch (IOException e) {
 			e.printStackTrace();
		}
	}
	
	
	
	/**
	 * Compares two rankings by counting the clicks that each ranking got.
	 * The returned int array will contain three entries representing the number of clicks
	 * that just ranking A or B received, as well as the number of clicks that happened
	 * on documents that which in both rankings.
	 *
	 * @param queryId the query id
	 * @param resultDocsOfQuery the documents returned by the query
	 * @param clicksOfQuery the clicks of the query
	 * @return the number of clicks that each ranking got
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static int[] getPreferredRanking(String queryId, String resultDocsOfQuery, String clicksOfQuery) throws IOException  {
		int clicksA = 0;
		int clicksB = 0;
		int clicksBoth = 0;
		int maxClickIndex = 0;
		ArrayList<ResultEntry> results = new ArrayList<ResultEntry>();
		
		// Find out which results were clicked on
		StringTokenizer st = new StringTokenizer(resultDocsOfQuery, ",");
		while (st.hasMoreTokens()) {
			String[] doc = st.nextToken().split("\\*");
			int rankInA = Integer.parseInt(doc[0]);
			int rankInB = Integer.parseInt(doc[1]);
			boolean clickedOn = isResultClicked(doc[2], clicksOfQuery);
			results.add(new ResultEntry(rankInA, rankInB, clickedOn));
			if (clickedOn) {
				maxClickIndex = Math.min(rankInA, rankInB);
				//System.out.println("click on: " + doc[2]);
			}
		}
		
		// Now count how many clicks each ranking received
		for (ResultEntry r : results) {
			if (r.isClickedOn()) {
				if (r.getRankInA() <= maxClickIndex) {
					clicksA++;
				}
				if (r.getRankInB() <= maxClickIndex) {
					clicksB++;
				}
				if ((r.getRankInA() <= maxClickIndex) && (r.getRankInB() <= maxClickIndex)) {
					clicksBoth++;
				}
			}
		}
		
		// Return the number of clicks that each ranking got
		int[] clicks = {clicksA, clicksB, clicksBoth};
		//System.out.println("query: " + queryId + ", clicksA: " + clicksA + ", clicksB: " + clicksB);
		return clicks;
	}
	
	
	/**
	 * Get a list of clicks for a query along with additional information indicating
	 * the rankings the clicked document was in, i.e. doc-id-1 (A), doc-id-2 (B) ...
	 * 
	 *
	 * @param queryId the query id
	 * @param resultDocsOfQuery the documents returned by the query
	 * @param clicksOfQuery the clicks of the query
	 * @return a string containing a list of clicks along with some explanation
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	private static String getClicksExplained(String queryId, String resultDocsOfQuery, String clicksOfQuery) throws IOException  {
		String result = "";
		int maxClickIndex = 0;
		ArrayList<ResultEntry> results = new ArrayList<ResultEntry>();
		
		// Find out which results were clicked on
		StringTokenizer st = new StringTokenizer(resultDocsOfQuery, ",");
		while (st.hasMoreTokens()) {
			String[] doc = st.nextToken().split("\\*");
			int rankInA = Integer.parseInt(doc[0]);
			int rankInB = Integer.parseInt(doc[1]);
			boolean clickedOn = isResultClicked(doc[2], clicksOfQuery);
			results.add(new ResultEntry(rankInA, rankInB, clickedOn, doc[2]));
			if (clickedOn) {
				maxClickIndex = Math.min(rankInA, rankInB);
				//System.out.println("click on: " + doc[2]);
			}
		}
		
		// Now count how many clicks each ranking received
		for (ResultEntry r : results) {
			if (r.isClickedOn()) {
				if (!result.isEmpty())
					result += ", ";
				if ((r.getRankInA() <= maxClickIndex) && (r.getRankInB() <= maxClickIndex)) {
					result += r.getId() + " (Both)";
				} 
				else if (r.getRankInA() <= maxClickIndex) {
					result += r.getId() + " (A)";
				}
				else if (r.getRankInB() <= maxClickIndex) {
					result += r.getId() + " (B)";
				}
				
			}
		}
		return result;
	}

	/**
	 * Checks if a result was clicked on.
	 *
	 * @param docId the doc id of the result
	 * @param clicksOfQuery the clicks of the query
	 * @return true, if result was clicked on
	 */
	private static boolean isResultClicked(String docId, String clicksOfQuery) {
		StringTokenizer st = new StringTokenizer(clicksOfQuery, ",");

		// For all relevant clicks
		while (st.hasMoreTokens()) {
			String clickId = st.nextToken();
			// See whether we find this document in the list of clicks
			if (clickId.startsWith(docId)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * The main method, only used for testing.
	 *
	 * @param args not used
	 * @throws IOException Signals that an I/O exception has occurred.
	 * @throws SQLException the sQL exception
	 */
	public static void main(String[] args) throws IOException, SQLException {
		TreeMap<String, ArrayList<Integer>> table = EvalInterleaving.getEvaluation();
		for (Map.Entry<String, ArrayList<Integer>> entry : table.entrySet()) {
		    String key = entry.getKey();
		    ArrayList<Integer> value = entry.getValue();
		    System.out.println(key + " A: " + value.get(0) + " B: "  + value.get(1) + " Both: " + value.get(2));
		}
	}

}
