package edu.cornell.cs.osmot.searcher;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.ParseException;

import java.util.Hashtable;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;

import edu.cornell.cs.osmot.logger.Logger;
import edu.cornell.cs.osmot.options.Options;

/**
 * This class implements the biggest chunk of the search engine. Given a query
 * and a search mode, it runs the query and returns ranked results.
 * 
 * @author Filip Radlinski
 * @version 1.0, April 2005
 */
public abstract class Searcher {

	public static String modes[] = { "9a", "dt", "mix"};
	
	protected boolean debug;

	

	/**
	 * Create a new searcher with no learned ranking functions. We'll just use
	 * the default ranking function defined below in this class.
	 * 
	 * @param indexDir
	 *            The directory where the Lucene index is stored.
	 */
	public Searcher(String indexDir) throws IOException {
		debug = Options.getBool("DEBUG");
		
	}

	/**
	 * Create a blank searcher. Java requires that this exists.
	 */
	public Searcher() {
		debug = Options.getBool("DEBUG");
	}

	
	/**
	 * Return a snippet for this document for this query. Some searchers may 
	 * want to over-ride this.
	 * 
	 * @param d     The document to get a snippet of.
	 * @param query The query to return a snippet for.
	 * @return The snippet to display.
	 */
	public String getSnippet(ScoredDocument d, String query) {
		return Snippeter.getSnippet(d.getDoc(), query, false, false);
	}

	/**
	 * Return an extended snippet for this document for this query. Some searchers may 
	 * want to over-ride this.
	 * 
	 * @param d     The document to get a snippet of.
	 * @param query The query to return a snippet for.
	 * @return The snippet to display.
	 */
	public String getLongSnippet(Document d, String query) {
		return Snippeter.getSnippet(d, query, true, false);
	}

	/**
	 * Return an extended snippet for this document for this query. Some searchers may 
	 * want to over-ride this.
	 * 
	 * @param d     The document to get a snippet of.
	 * @param query The query to return a snippet for.
	 * @return The snippet to display.
	 */
	public String getPaperSnippet(Document d, String query) { 
		return Snippeter.getSnippet(d, query, true, true);
	}
	
	/**
	 * Return an extended snippet for this document for this query. Some searchers may 
	 * want to over-ride this.
	 * 
	 * @param d     The document to get a snippet of.
	 * @param query The query to return a snippet for.
	 * @return The snippet to display.
	 */
	public String getAbstractSnippet(Document d, String query) { 
		return Snippeter.getAbstractSnippet(d, query, false);
	}
	
	/**
	 * Search for this query, then rerank the results with the specified
	 * reranker.
	 * 
	 * @param query
	 *            The query to run
	 * @param reranker
	 *            The reranker to use (for rank features), or null of none.
	 *            ip-dependent rerankings.
	 * @return The ranked results of the search.
	 */
	public abstract RerankedHits search(String query)
			throws IOException, ParseException;
	

	/**
	 * Returns the document with this id in the searcher
	 * @param docId  Document id in the searcher
	 */
	public abstract Document getDoc(int docId) throws IOException;
	
	/**
	 * Pick a random document from the collection.
	 * 
	 * @return The id of a random valid document in the index.
	 */
	protected abstract int randomDoc() throws IOException;

	/**
	 * Pick a random document from the collection in a specific category.
	 * 
	 * @param category
	 *            The category the document should be in
	 * @return The document id
	 */
	protected abstract int randomDoc(String category) throws IOException;

	/**
	 * Return the HTML link of a scored document that should be displayed.
	 * This is collection specific, which is why it is here, despite it being
	 * a method that should intuitively belong in ScoredDocument
	 * @param sd The scored document to generate the link
	 * @return Title string in HTML
	 */
	public abstract String toHtml(ScoredDocument sd);
	
	/**
	 * Combine two sets of results as described in Joachims, T (2003) Evaluating
	 * retrieval performance using clickthrough data. In J Franke, G
	 * Nakhaeizadeh, and I Renz, editors, Text Mining, pages 79-96,
	 * Physica/Springer Verlag, 2003. This allows unbiased comparison of two
	 * ranking functions by intertwining the results.
	 * 
	 * @param results1
	 *            First set of results.
	 * @param results2
	 *            Second set of results.
	 * @param category
	 *            If not null, make sure all documents are in this category.
	 * @param seed
	 *            Random number generator seed.
	 * @return A combined ranking.
	 */
	protected final RerankedHits combine(RerankedHits results1, RerankedHits results2, 
			String category, long seed) throws IOException {
		
		int pl1 = results1.prefixLength();
		int pl2 = results2.prefixLength();
	
		if (debug) {
			Logger.log("Combining two result sets. Prefix lengths are " + pl1 + " and " + pl2);
		}
		
		// Somewhere to store the combined results.
		int maxLength = Math.max(pl1, pl2);
		ScoredDocument[] combinedResults = new ScoredDocument[2 * maxLength];

		int indexCombined = 0;
		int index1 = 0; // Index of first element in A not in combined
		int index2 = 0; // Index of first element in B not in combined

		// Used to pick if results1 or results2 gets preference
		Random gen = new Random(seed);
		boolean randomBit = gen.nextBoolean();

		// Make a hash table of document ids to ranks in the two result sets
		Hashtable h1 = new Hashtable(pl1);
		for (int i = 0; i < pl1; i++)
			h1.put(results1.doc(i).getUniqId(), new Integer(i));
		Hashtable h2 = new Hashtable(pl2);
		for (int i = 0; i < pl2; i++)
			h2.put(results2.doc(i).getUniqId(), new Integer(i));
		Hashtable hCombined = new Hashtable(pl1 + pl2);

		// Combine them all.
		while (index1 < pl1 || index2 < pl2) {

			String uniqDocId = "";
			int rank1 = 0;
			int rank2 = 0;

			// If equal and A has priority, or A is smaller, add A
			if (((index1 == index2) && (!randomBit)) || (index1 < index2)) {
				if (index1 < pl1) {

					// Check results1 hasn't ended
					uniqDocId = results1.doc(index1).getUniqId();
					rank1 = getRank(h1, uniqDocId);
					rank2 = getRank(h2, uniqDocId);

				} else {

					// If A has ended, pick something random not in A
					// or the combined set
					uniqDocId = randomFromCategory(category, hCombined, h1);
					rank1 = index1;
					rank2 = getRank(h2, uniqDocId);
				}

				// We need to increment here since we don't get
				// into increment loop below.
				index1++;
				
			} else { // Else equal and B has priority, or B is smaller
				if (index2 < pl2) {

					// Check B hasn't ended
					uniqDocId = results2.doc(index2).getUniqId();
					rank1 = getRank(h1, uniqDocId);
					rank2 = getRank(h2, uniqDocId);

				} else {
					// If B has ended, pick something random not in B
					// or the combined set
					uniqDocId = randomFromCategory(category, hCombined, h2);
					rank1 = getRank(h1, uniqDocId);
					rank2 = index2;
				}
				// We need to increment here since we don't get
				// into increment loop below.
				index2++;
			}

			// Common processing in any case
			combinedResults[indexCombined] = getDoc(uniqDocId);
			if (debug) Logger.log("Ranks for "+uniqDocId+" are "+rank1+","+rank2);
			combinedResults[indexCombined].setRanks(rank1, rank2);
			hCombined.put(uniqDocId, new Integer(indexCombined));
			
			// Update index1 and index2 to be the number of top n elements from
			// results1 and results2 to be in the combined list.
			while ((index1 <= indexCombined) && (index1 < pl1)
					&& (getRank(hCombined, results1.doc(index1).getUniqId()) != -1))
				index1++;

			while ((index2 <= indexCombined) && (index2 < pl2)
					&& (getRank(hCombined, results2.doc(index2).getUniqId()) != -1))
				index2++;

			indexCombined++;
		}

		// Reset combined to be shorter if we got repeats
		ScoredDocument[] combined2 = new ScoredDocument[indexCombined];
		for (int i = 0; i < indexCombined; i++) {
			combined2[i] = combinedResults[i];
		}

		if (debug) {
			//int ranks[] = combined2[0].getRanks();
			//Logger.log("After combining before wrapping, first doc has ranks "+ranks[0]+","+ranks[1]);
		}
		
		// The combined set goes first, padded with the original results
		// as needed (i.e. we serve combined 2 until they run out, then
		// continue with new results in results1).
		return new RerankedHits(combined2, results1.getByDate(), null);
	}

	/**
	 * Combine two sets of results as described in Joachims, T (2003) Evaluating
	 * retrieval performance using clickthrough data. In J Franke, G
	 * Nakhaeizadeh, and I Renz, editors, Text Mining, pages 79-96,
	 * Physica/Springer Verlag, 2003. This allows unbiased comparison of two
	 * ranking functions by intertwining the results.
	 * 
	 * @param results1
	 *            First set of results.
	 * @param results2
	 *            Second set of results.
	 * @param category
	 *            If not null, make sure all documents are in this category.
	 * @param seed
	 *            Random number generator seed.
	 * @return A combined ranking.
	 */
	protected final RerankedHits combineGameSelection(RerankedHits results1, RerankedHits results2, 
			String category, long seed) throws IOException {
		
		int pl1 = results1.prefixLength();
		int pl2 = results2.prefixLength();
	
		if (debug) {
			Logger.log("Combining two result sets. Prefix lengths are " + pl1 + " and " + pl2);
			Logger.log("Unique seed used for query " + seed);
		}
		
		// Somewhere to store the combined results.
		int maxLength = Math.max(pl1, pl2);
		ScoredDocument[] combinedResults = new ScoredDocument[2 * maxLength];
		boolean[] combinedBits = new boolean[2 * maxLength];

		int indexCombined = 0;
		int index1 = 0; // Index of first element in A not in combined
		int index2 = 0; // Index of first element in B not in combined

		// Used to pick if results1 or results2 gets preference
		Random gen = new Random(seed);
		boolean randomBit = gen.nextBoolean();
		int twostep = 0;

		// Make a hash table of document ids to ranks in the two result sets
		Hashtable h1 = new Hashtable(pl1);
		for (int i = 0; i < pl1; i++)
			h1.put(results1.doc(i).getUniqId(), new Integer(i));
		Hashtable h2 = new Hashtable(pl2);
		for (int i = 0; i < pl2; i++)
			h2.put(results2.doc(i).getUniqId(), new Integer(i));
		Hashtable hCombined = new Hashtable(pl1 + pl2);

		// Combine them in the game selection style
		// Here, each turn the contributing source *has* to provide
		// a previously non combined result
		while ((index1 < pl1) || (index2 < pl2)) {

			// Every time you finish a set of choices
			// Flip coin to reset prob
			if(twostep == 2) {
				twostep = 0;
				randomBit = gen.nextBoolean();
			}
			twostep++;
			
			// Common variables for processing
			String uniqDocId = "";
			int rank1 = 0;
			int rank2 = 0;

			if(!randomBit) {
			
				// A has priority
				do
				{
					// Get for either results, or cookup random
					if(index1 < pl1) 
                    {
                        ScoredDocument sd1 = results1.doc(index1);
                        if(debug) Logger.log("doc1: " + sd1);
						uniqDocId = sd1.getUniqId();
                        if(debug) Logger.log("doc1 ID: " + uniqDocId);
                    }
					else 
                    {
                        if(debug) Logger.log("grabbing low doc1");
						uniqDocId = randomFromCategory(category, hCombined, h1);
                    }
					index1++;		
				} while (getRank(hCombined, uniqDocId) != -1);
				
				rank1 = index1-1;
				rank2 = getRank(h2, uniqDocId);
			}
			else {
				
				// B has priority
				do
				{
					// Get for either results, or cookup random
					if(index2 < pl2) 
                    {
                        ScoredDocument sd2 = results2.doc(index2);
                        if(debug) Logger.log("doc2: " + sd2);
						uniqDocId = sd2.getUniqId();
                        if(debug) Logger.log("doc2 ID: " + uniqDocId);
                    }
					else 
                    {
                        if(debug) Logger.log("grabbing low doc2");
						uniqDocId = randomFromCategory(category, hCombined, h2);
                    }
					index2++;		
				} while (getRank(hCombined, uniqDocId) != -1);
			
				rank1 = getRank(h1, uniqDocId);
				rank2 = index2-1;
			}
	
			// debug
			if(debug) {
				Logger.log("Ranks for "+uniqDocId+" are "+rank1+","+rank2);
				Logger.log("Randombit "+randomBit+" ResetCounter = " + twostep);
			}

			// Common processing in any case
			combinedResults[indexCombined] = getDoc(uniqDocId);
			combinedResults[indexCombined].setRanks(rank1, rank2);
			combinedBits[indexCombined] = randomBit;
			hCombined.put(uniqDocId, new Integer(indexCombined));

			// Next step in loop
			indexCombined++;
			randomBit = !randomBit;
		}

		// Reset combined to be shorter if we got repeats
		ScoredDocument[] combined2 = new ScoredDocument[indexCombined];
		boolean[] combinedBits2 = new boolean[indexCombined];
		for (int i = 0; i < indexCombined; i++) {
			combined2[i] = combinedResults[i];
			combinedBits2[i] = combinedBits[i];
		}

		if (debug) {
			int ranks[] = combined2[0].getRanks();
			Logger.log("After combining before wrapping, first doc has ranks "+ranks[0]+","+ranks[1]);
		}
		
		// The combined set goes first, padded with the original results
		// as needed (i.e. we serve combined 2 until they run out, then
		// continue with new results in results1).
		return new RerankedHits(combined2, results1.getByDate(), combinedBits2);
	}
	

	/**
	 * Get a random doc from the appropriate category that is not present already or in the other ranking
	 * 
	 * @param category
	 *            Category we are interested in
	 * @param hCombined
	 *            The combined hash table
	 * @param other
	 *            The hash table of the other ranking
	 * @return The rank of this document id, or -1 if its not present.
	 */
	protected final String randomFromCategory(String category, Hashtable hCombined, 
			Hashtable other) throws IOException {

		String uniqDocId = "";
		while (uniqDocId == null || uniqDocId.equals("")) {
			uniqDocId = randomDocUniqId(category);
            if (debug) Logger.log("Random from category " + category + ": " + uniqDocId);
			if (uniqDocId != null && (other.get(uniqDocId) != null || hCombined.get(uniqDocId) != null))
				uniqDocId = "";
		}
		return uniqDocId;
	}
	
	/**
	 * Get the rank of document docId in the hashtable of results h.
	 * 
	 * @param h
	 *            Hashtable storing document ids and the rank of the document.
	 * @param docId
	 *            The document id we want to look up.
	 * @return The rank of this document id, or -1 if its not present.
	 */
	protected final int getRank(Hashtable h, String uniqDocId) {

		Integer r = (Integer) h.get(uniqDocId);
		if (r == null)
			return -1;
		else
			return r.intValue();
	}

	/**
	 * Returns the category in the query, if any.
	 * 
	 * @param query
	 *            The query
	 * @return The category the query requires, or null if none is present.
	 */
	protected final static String getCategory(String query) {

		String pattern = "[^a-z]category:([a-z]*)";
		Pattern p = Pattern.compile(pattern);
		Matcher m = p.matcher(query.toLowerCase());
		if (m.find()) {
			return m.group(1);
		}

		return null;
	}

	/** Log the message s to the general log file. */
	protected final void log(String s) {
		Logger.log(s);
	}

	/**
	 * Convert a hashtable containing scored documents to an array of scored
	 * documents.
	 */
	public static final ScoredDocument[] hashtableToArray(Hashtable h) {

		if (h == null)
			return null;

		return (ScoredDocument[]) (h.values()).toArray(new ScoredDocument[0]);
	}

	/**
	 * Return the unique identifier of a random document in the collection or
	 * null on failure.
	 */
	public abstract String randomDocUniqId() throws IOException;

	/**
	 * Return the unique identifier of a random document in the collection or
	 * null on failure, such that the document is in the given category.
	 */	
	public abstract String randomDocUniqId(String category) throws IOException;

	
	/**
	 * Return the document with this unique identifier, or null if it doesn't
	 * exist. Sets the document and document id, but sets the score to 0.
	 */
	public abstract ScoredDocument getDoc(String uniqId) throws IOException;

}
