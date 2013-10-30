package edu.cornell.cs.osmot.searcher;

import java.io.IOException;
import edu.cornell.cs.osmot.logger.*;
import edu.cornell.cs.osmot.options.*;

/**
 * This is a wrapper class for the results returned by the searcher class. It also contains arrays that 
 * will keep track of the origin of each document when two rankings are interleaved.
 * 
 * @author Filip Radlinski, Tobias Schnabel
 * @version 1.2, May 2012
 *
 */

public class RerankedHits {
	
	private ScoredDocument[] initialResults;
	private boolean[] sourceBits;

	// For debugging
	private String mode = null;

	// For sorting by date
	private boolean byDate;

	// Upper and lower bound on number of results (we know it better as we get results)
	private int numResultsLB = -1;  
	private int numResultsUB = -1;
 	
	private static boolean debug = Options.getBool("DEBUG");
	
	 
	public RerankedHits(ScoredDocument[] r) {
		this(r, false, null);
	}

	public RerankedHits(ScoredDocument[] r, boolean bd, boolean[] b) {
		initialResults = r;
		sourceBits = b;

		if (initialResults == null) {
			if(debug) Logger.log(" **** No Initial Results");
			initialResults = new ScoredDocument[1];
			initialResults[0] = null;
			sourceBits = new boolean[1];
			sourceBits[0] = true;
		}
		
		// Initialize date sort things.
		byDate = bd;
		
		// Get the bounds on the lengths;
		numResultsLB = initialResults.length;
		numResultsUB = initialResults.length;

		
		if (debug) Logger.log("There are "+numResultsLB+" - "+numResultsUB+" results in RerankedHits.");
	}

	public int getSourceBit(int rank) {
		if((sourceBits == null)||(rank >= initialResults.length))
			return -1;
		return ((sourceBits[rank] == true) ? 1 : 0);
	}

	public boolean setSourceBit(int rank, boolean source) {
		if((sourceBits == null)||(rank >= initialResults.length))
			return false;
		sourceBits[rank] = source;
		return true;
	}

	public void setMode(String debug) {
		mode = debug;
	}

	public String getMode() {
		return mode;
	}
		
	public boolean getByDate() {
		return byDate;
	}
	
	
	/** 
	 * Returns the approximate number of results - impossible to know exactly
	 * until we actually walk through all the results and see which ones are
	 * also in initialResults
	 *
	 * @return The approximate number of results
	 */
	public int length() {
		return initialResults.length;
	}

	/** 
	 * Returns a lower bound on the number of results
	 *
	 * @return A lower bound on the number of results
	 */
	public int lengthLB() {
		return numResultsLB;
	}
	
	
	/**
	 * Returns an upper bound on the number of results
	 * @return An upper bound on the number of results
	 */
	public int lengthUB() {
		return numResultsUB;
	}
	
	/**
	 * Returns the number of results that are in the initial results list.
	 */
	public int prefixLength() {
		return initialResults.length;
	}

	public double score(int i)  {
		return initialResults[i].getScore();
	}
	
	public ScoredDocument doc(int i) throws IOException {
		ScoredDocument sd = initialResults[i];
		return sd;
	}
		
	/**
	 * For easy conversion to strings
	 */
	public String toString() {
		return "[ResultSet: length in "+numResultsLB+"-"+numResultsUB+". Prefix of length "+initialResults.length + "]";
	}
	
	/** RerankedHits is meant to be used throughout now.
	 * Return the initial results, for old code
	 */
	public ScoredDocument[] initialResults() {
		return initialResults;
	}
	
	
	public int id(int i) throws IOException {
		return initialResults[i].getId();
	}	
}
