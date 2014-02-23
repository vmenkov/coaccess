package edu.cornell.cs.osmot.searcher;

import org.apache.lucene.search.similarities.DefaultSimilarity;

/**
 * SimplifiedSimilarity overrides the Lucene's default similarity by setting
 * the idf and coord function to 1.0.
 */
public class SimplifiedSimilarity extends DefaultSimilarity {
	
	/** The Constant serialVersionUID. */
	private static final long serialVersionUID = 1L;

	/**
	 * Instantiates a new simplified similarity.
	 */
	public SimplifiedSimilarity() {
	}
		
	/**
	 * Coordination factor
	 */
	public float coord(int overlap, int maxOverlap) {
		return (float) Math.pow(overlap, 1.4);
	}

}