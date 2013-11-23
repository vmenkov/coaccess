package edu.cornell.cs.osmot.searcher;

import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.util.Version;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;

import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.searcher.Searcher;
import edu.cornell.cs.osmot.searcher.WeightedLuceneSearcher;
import edu.cornell.cs.osmot.searcher.Snippeter;
import edu.cornell.cs.osmot.logger.Logger;

/**
 * Allows sorting by decreasing date.
 */
class DateComparator implements Comparator<ScoredDocument> {
	public int compare(ScoredDocument o1, ScoredDocument o2) {
		Date d1 = o1.getDate();
		Date d2 = o2.getDate();
		
		if (d1 == null && d2 == null)
			return 0;
		else if (d1 == null)
			return -1;
		else if (d2 == null)
			return 1;
		
		return d2.compareTo(d1);
	}
}

/**
 * This class implements the servlet interface for the search engine.
 * 
 * @author Filip Radlinski
 * @version 1.0, April 2005
 */
public class SearchBean {

	protected WeightedLuceneSearcher onlinePerceptron;
	protected WeightedLuceneSearcher baseline;
	protected int randomizationLimit = 11;
	protected String indexDirectory;
	private static final String LUCENE_ESCAPE_CHARS = "[\\[\\\\+\\-\\!\\(\\)\\:\\^\\]\\{\\}\\~\\*\\?\\\"']";
	private static boolean debug;
	
	private double modeProbs[] = null;
	
	/** Look up the SearchBean for an application. */
	public synchronized static SearchBean get(ServletContext app)
			throws Exception {
		
		SearchBean bean = (SearchBean) app.getAttribute("searchBean");

		if (bean == null) {
			debug = Options.getBool("DEBUG");
			if(debug) Logger.log("Creating a SearchBean");
			bean = new SearchBean();
			app.setAttribute("searchBean", bean);
			if(debug) Logger.log("SearchBean ready");
		}

		return bean;
	}

	public static String sanitizeQuery(String query) {
		if (query == null) return "";
		// If we can parse it, return it.
		try {
			QueryParser parser = new QueryParser(Version.LUCENE_36, "article", new StandardAnalyzer(Version.LUCENE_36));
			parser.parse(query);
			return query;
		} catch (Exception e) {
			String result = query.replaceAll(LUCENE_ESCAPE_CHARS, " ");
			result = result.replaceAll("\\s(AND|OR|NOT)", " ");
			result = result.replaceAll("\\s\\s+", " ");
			result = result.replaceAll("^(AND|OR|NOT)", " ");
			return result.trim();
		}
	}
	
	/** Create a search bean with these files. */
	public SearchBean() throws IOException {
		String indexDir = Options.get("INDEX_DIRECTORY");
		debug = Options.getBool("DEBUG");

		onlinePerceptron = new WeightedLuceneSearcher(indexDir, Options.get("OSMOT_ROOT") + "/storeWeights.txt", false);
		baseline = new WeightedLuceneSearcher(indexDir, "", false);
		
		init(indexDir);

		if (debug)
			Logger.log("Done creating SearchBean.");
	}	

	/** 
         * Initialization code common to all constructors
         */

	protected void init(String indexDir) {
		indexDirectory = indexDir;	
	}

	public RerankedHits searchDebug(String query, String mode, String subGroup) throws ParseException, IOException {
		// Run the search
		RerankedHits finalResults = null;
        if (!subGroup.isEmpty()) {
        	query = "(("+ query.trim() + ") AND (category:"+subGroup+" OR group:"+subGroup+"))";
        }
        
        if (mode.equals("9a")) { // learn from clicks
			finalResults = onlinePerceptron.search(query, false, true);	//Perturb the perceptron results
        } else if (mode.equals("baseline")) {
        	finalResults = baseline.search(query, false, false);	//Do not perturb the baseline results
        }
        return finalResults;
	}
	
	public String getDebugScore(ScoredDocument d, String mode, String glueString) {
		if (mode.equals("9a")) { // learn from clicks
			return d.getDebugScore(onlinePerceptron.getWeightVector(), glueString);
        } else {
        	return d.getDebugScore(baseline.getWeightVector(), glueString);
        }
		
	}
	
	/**
	 * Faster version of main search function. Run a search for the query, 
	 * given some information needed for the logging, as well as the mode of 
	 * the query to run.
	 * 
	 * @param query
	 *            The query to run
	 * @param session
	 *            The session, used to generate seed.
	 * @param request
	 *            The request, used to generate log. If null, no log is made.
	 * @param mode
	 *            The mode of the query, set to null to use default.
	 * @param log
	 * 			  If true, the the query is logged.
	 * @param byDate
	 * 		      If true, results are sorted by date instead of by score.
	 * @param min
	 * 		      Minimum result number that will be displayed to users
	 * @param max 
	 *            Maximum result number that will be displayed to users
	 * @return An array of results, sorted as they are to be displayed to the
	 *         user.
	 */
	public RerankedHits searchFast(String query, HttpSession session,
			HttpServletRequest request, String mode, boolean log, boolean byDate, String subGroup) 
		throws IOException, ParseException {
		
        if(debug) Logger.log("Starting SearchBean.SearchFast");
        
        if (!subGroup.isEmpty()) {
        	query = "(("+ query.trim() + ") AND (category:"+subGroup+" OR group:"+subGroup+"))";
        }
		String qid = request.getParameter("qid");

        if(debug) Logger.log("Qid: " + qid);

        if(qid != null)
            mode = Logger.qidMode(qid);

        if(debug) Logger.log("Mode: " + mode);

		if (byDate) {
			mode = "dt";
		} else if (mode == null) {
			mode = pickMode(request, session);
		}
		
		// Get a qid
        if(qid == null) qid = getQid(request, mode);
		
		// Log the request
		Logger.logRequest(request, mode);
		
		if (debug) Logger.log("SearchBean.SearchFast Called. Mode is "+mode+". Log is "+log);
		
		// Run the search
		RerankedHits finalResults;
		
        if (mode.equals("9a")) { // learn from clicks
			finalResults = onlinePerceptron.search(query);	//Perturb the results
        } else if (mode.equals("mix")) { // Interleave baseline with learned ranking
        	RerankedHits baselineHits = baseline.search(query, false, false);	//Do not perturb baseline
        	RerankedHits perceptronHits = onlinePerceptron.search(query, false, false);	//Do not perturb while evaluating
        	finalResults = baseline.combine(baselineHits, perceptronHits, null, getQuerySeed(session.getId(), query));
		} else if (mode.equals("dt")) { // Sort by date
			finalResults = baseline.searchDate(query);
		} else {
			Logger.log("ERROR: Invalid mode " + mode);
			return null;
		}

        if (debug) Logger.log("Mode: " + mode + " searching complete.");

		// Save mode for debugging
		finalResults.setMode(mode);	

		// Don't log repeated queries from a long time ago
		if (Math.abs(new Date().getTime() - qidTime(qid)) > 24 * 3600 * 1000)
			log = false;
			
		// Log the query and results
		if (log) {
			Logger.logQuery(request, query, mode, finalResults, qid, session.getId(), true);		
			if (debug) Logger.log("Query logged.");	
		}
			
		if (debug) Logger.log("Query finished.");
	
		return finalResults;
	}
	
        /** 
         * Force the index to be reloaded.
	 */
 	public void updateSearcher() {
	        baseline.updateSearcher(true);
	}

        /**
	 * Generates a query id. It tells us about the mode, the reranking model file used,
	 * and the time. 
	 * 
	 * @param request  The request object that keeps track of the session.
	 * @param mode     The mode of the request.
	 * @return
	 */
	private String getQid(HttpServletRequest request, String mode) {
		
		String qid = request.getParameter("qid");
		if (qid == null) {
			qid = (String) request.getAttribute("qid");
		}
		
		if (qid == null) {
				
			long now = new Date().getTime();
			String modelStr = "";
			modelStr += "nC";
			modelStr += "nN";
				
			qid = "" + now + mode + "_" + modelStr  + "_" +  request.getRemoteAddr().hashCode();
		}
		request.setAttribute("qid", qid);

		return qid;
	}
	
	/**
	 * Returns a snippet for a given query and result document.
	 * 
	 * @param query
	 *            The query.
	 * @param d
	 *            The document for this result.
	 */
	public String getSnippet(ScoredDocument d, String query, String mode) {

		if(mode.charAt(0) == '8') {
			if((mode.charAt(1) == 'a')||(mode.charAt(1) == 'c'))
				return baseline.getAbstractSnippet(d.getDoc(), query);
			return baseline.getLongSnippet(d.getDoc(),query);
		}
		String ans =  baseline.getSnippet(d, query);
        if(mode != null && mode.substring(0,1).equals("B"))
            return ans;
        return Snippeter.boldify(ans, query);
	}

	/**
	 * Returns an extended snippet for a given query and result document.
	 * 
	 * @param query
	 *            The query.
	 * @param d
	 *            The document for this result.
	 */
	public String getLongSnippet(ScoredDocument d, String query) {

		return baseline.getLongSnippet(d.getDoc(), query);
	}	

	/**
	 * Returns an extended snippet for a given query and result document for 
	 * use in the context of display of a particular paper.
	 * 
	 * @param query
	 *            The query.
	 * @param d
	 *            The document for this result.
	 */
	public String getPaperSnippet(ScoredDocument d, String query) {

		return baseline.getPaperSnippet(d.getDoc(), query);
	}	
	
	 
	/**
	 * Return the document with this unique identifier, or null if it doesn't
	 * exist. Sets the document and document id, but sets the score to 0.
	 */
	public ScoredDocument getDoc(String uniqId) throws IOException {
		return baseline.getDoc(uniqId);
	}
	
	/**
	 * Boldify a given string (usually the title string of a result) given a
	 * query.
	 * 
	 * @param s
	 *            The string to boldify.
	 * @param query
	 *            The query.
	 */
	public String boldify(String str, String query) {
        return boldify(str,query,null);
    }
	public String boldify(String str, String query, String mode) {
        if(mode != null && mode.substring(0,1).equals("B"))
            return str;

		return Snippeter.boldify(str, query);
	}

	/**
	 * Logs a click on a result that goes to the abstract page.
	 * 
	 * @param ip
	 *            The IP address of the user.
	 * @param session
	 *            The session id of the search session.
	 * @param qid
	 *            The unique id of the query that returned this result
	 * @param doc
	 *            The unique identifier of the document
	 * @throws IOException 
	 * @throws ParseException 
	 */
	public void logClick(HttpServletRequest request, HttpSession session, String qid,
			String doc, boolean byDate, String queryString, String subGroup) throws ParseException, IOException {
        if (!subGroup.isEmpty()) {
        	queryString = "(("+ queryString.trim() + ") AND (category:"+subGroup+" OR group:"+subGroup+"))";
        }
		this.logClick(request, session, qid, doc, "abs", byDate, queryString);
		
	}

	/**
	 * Logs a click on a result that goes to the any format of the page.
	 * 
	 * @param ip
	 *            The IP address of the user.
	 * @param session
	 *            The session id of the search session.
	 * @param qid
	 *            The unique id of the query that returned this result
	 * @param doc
	 *            The unique identifier of the document
	 * @param format
	 *            The format requested
	 * @throws IOException 
	 * @throws ParseException 
	 */
	public void logClick(HttpServletRequest request, HttpSession session, String qid,
			String doc, String format, boolean byDate, String queryString) throws ParseException, IOException {
		String mode;
		if (byDate) 
			mode = "dt";
		else 
			mode = pickMode(request, session);
		
		if (mode.equals("9a") && format.equalsIgnoreCase("abs")) {
			// Update perceptron
			onlinePerceptron.updateWeights(doc, queryString, qid);
		}
		
        if(debug) Logger.log("Query time: " + qidTime(qid) + " Click time: " + new Date().getTime());
		
		// Only log clicks within a day of the query actually running. Otherwise
		// people might be following stale results, or following links to searches
		// posted on the web
		if (Math.abs(new Date().getTime() - qidTime(qid)) < 24 * 3600 * 1000)
        {
            if(debug) Logger.log("Logging Click");
			Logger.logClick(request, mode, session.getId(), qid, doc, format);
        }
	}
	
	/**
	 * Return the HTML link of a scored document that should be displayed.
	 * This is collection specific, which is why it is here, despite it being
	 * a method that should intuitively belong in ScoredDocument
	 * @param sd The scored document to generate the link
	 * @return Title string in HTML
	 */
	public String toHtml(ScoredDocument sd) {
		return baseline.toHtml(sd);
	}

	
	/**
	 * Picks a mode for a search. This is random, but takes a seed that is
	 * constant for the duration of a user's session so that page 2 is the same
	 * mode as page 1, and also so that if a query is re-run, it will also be
	 * run with the same mode.
	 * @param session 
	 * 
	 * @param seed
	 *            A random number generator seed.
	 */
	private String pickMode(HttpServletRequest request, HttpSession session) {
		// For debugging and testing: Use custom modes
		String manualMode = (String) session.getAttribute("mode");
		if (manualMode != null) {
			return manualMode;
		}
		
		if (modeProbs == null) {
			double total = 0;
			modeProbs = new double[Searcher.modes.length];
			for (int i=0; i<modeProbs.length; i++) {
				modeProbs[i] = Options.getDouble("SEARCHER_MODE_"+Searcher.modes[i]);
				if(debug) Logger.log("SEARCHER_MODE_"+Searcher.modes[i]+": "+modeProbs[i]);
				total += modeProbs[i];		
				modeProbs[i] = total;
			}
			// Normalize the probabilities to 1
			for (int i=0; i<modeProbs.length; i++) {
				modeProbs[i] /= total;
				if(debug) Logger.log("Source " + i + " == prob " + modeProbs[i]);
			}
		}
		
		// UserID is a combo of IP + userAgent + current date
		String ip = request.getRemoteAddr();
		String userAgent = request.getHeader("user-agent");
		Date dateNow = new Date ();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
        String curDate = new StringBuilder( formatter.format( dateNow ) ).toString();
		
		BigInteger hash ;
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.update(ip.getBytes(),0, ip.length());
			messageDigest.update(userAgent.getBytes(),0,userAgent.length());
			messageDigest.update(curDate.getBytes(),0,curDate.length());
			hash = new BigInteger(1,messageDigest.digest());
		} catch (NoSuchAlgorithmException e) {
			hash = null;
		}

		if(hash != null)
		{
			// get random in [0,1]
			int spread = Math.abs(hash.intValue());
			double r = (spread*1.0)/Integer.MAX_VALUE;

			if(debug) {
				Logger.log("Got user input as IP = '" + ip + "' UserAgent = '" + userAgent+"'");
				Logger.log("Resulted in hash value = " + spread + " which becomes " + r); 
			}
			for(int i=0; i<modeProbs.length; i++) {
				
				if(debug)Logger.log("Mode = " + i + " = " + modeProbs[i]);
				if (modeProbs[i] > r)
                {
                    if(debug) Logger.log("Found mode: " + Searcher.modes[i]);
					return Searcher.modes[i];
                }
			}
		}
		
		// Should never be reached, unless the random number is 1 or we get
		// a small number rounding effect.
		if(debug) {
			Logger.log("*** Reached end of all possibilities, bug?");
		}
		return "9a";
		
	}

	public void log(String msg) {
		Logger.log(msg);
	}
	/**
	 * Return the string value of a given option. This is useful in the JSP
	 * code for getting things like URLs.
	 *
	 * @name Option name to return
	 * @return The value of the option (from osmot.conf)
	 */
	public String getOption(String name) {
		return Options.get(name);
 	}

	/**
	 * Return the string value of a given session query combination.
	 *
	 * @session Session to compute data for
	 * @query Query to compute data for
	 * @return The value of the seed that could be used
	 */
	private long getQuerySeed(String session, String query) { 
		return (session.hashCode() ^ query.hashCode());
	}
	
	
	/**
	 * Return the time (milliseconds since 1/1/1970) when the qid was created.
	 * 
	 * @param qid A query id
	 * @return The time (milliseconds since 1/1/1970) when the qid was created.
	 */
	public static long qidTime(String qid) {
	    if (qid == null)
		    return 0;

	    if (qid.length() >= 13) 
		    return Long.parseLong(qid.substring(0,13));

	    return 0;
	}

   
}
