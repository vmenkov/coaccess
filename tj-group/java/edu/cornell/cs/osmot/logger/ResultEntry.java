package edu.cornell.cs.osmot.logger;

/**
 * This class models a result from a query with interleaving and will be
 * used by EvalInterleaving.
 * It contains the ranks of the document in rankings A and B along 
 * with a flag that indicates whether a user clicked on this result.
 * 
 * @author Tobias Schnabel
 * @version 1.0, May 2012
 */
public class ResultEntry {
	
	/** Indicates whether a user clicked on this result. */
	private boolean clickedOn;
	
	/** The rank of the document in Ranking A. */
	private int rankInA;
	
	/** The rank of the document in Ranking B. */
	private int rankInB;
	
	/** The document id. */
	private String id = "";
	
	/**
	 * Instantiates a new result entry.
	 *
	 * @param rankInA the rank in a
	 * @param rankInB the rank in b
	 * @param clickedOn the clicked on
	 */
	public ResultEntry(int rankInA, int rankInB, boolean clickedOn) {
		this.rankInA = rankInA;
		this.rankInB = rankInB;
		this.clickedOn = clickedOn;
	}
	
	/**
	 * Instantiates a new result entry.
	 *
	 * @param rankInA the rank in a
	 * @param rankInB the rank in b
	 * @param clickedOn the clicked on
	 * @param id the id
	 */
	public ResultEntry(int rankInA, int rankInB, boolean clickedOn, String  id) {
		this.rankInA = rankInA;
		this.rankInB = rankInB;
		this.clickedOn = clickedOn;
		this.id = id;
	}
	
	/**
	 * Checks if result was clicked on.
	 *
	 * @return true, if result was clicked on
	 */
	public boolean isClickedOn() {
		return clickedOn;
	}
	
	/**
	 * Gets the document id.
	 *
	 * @return the id
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * Sets the clickedOn flag
	 *
	 * @param clickedOn
	 */
	public void setClickedOn(boolean clickedOn) {
		this.clickedOn = clickedOn;
	}
	
	/**
	 * Gets the rank in A.
	 *
	 * @return the rank in A
	 */
	public int getRankInA() {
		return rankInA;
	}
	
	/**
	 * Sets the rank in A.
	 *
	 * @param rankInA the new rank in A
	 */
	public void setRankInA(int rankInA) {
		this.rankInA = rankInA;
	}
	
	/**
	 * Gets the rank in B.
	 *
	 * @return the rank in B
	 */
	public int getRankInB() {
		return rankInB;
	}
	
	/**
	 * Sets the rank in B.
	 *
	 * @param rankInB the new rank in B
	 */
	public void setRankInB(int rankInB) {
		this.rankInB = rankInB;
	}
}
