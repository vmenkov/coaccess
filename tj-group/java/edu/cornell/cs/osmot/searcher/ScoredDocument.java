package edu.cornell.cs.osmot.searcher;

import no.uib.cipr.matrix.sparse.SparseVector;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DateTools;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeSet;

import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.features.FeatureMapping;
import edu.cornell.cs.osmot.logger.Logger;

/**
 * This class implements a document with a score attached. We also store two
 * ranks. This is so that when we combine two rankings, we can keep track of
 * which ranking which document came from. The class is comparable, so we can
 * sort by score, but obviously in the case of combined rankings we don't want
 * to do that.
 * There will be a HashMap which collects all the raw feature values. This HashMap
 * will then be transformed into a feature vector using the FeatureMapping class.
 * 
 * @author Filip Radlinski, Tobias Schnabel
 * @version 1.2, May 2012
 */
public class ScoredDocument implements Comparable<ScoredDocument> {

	/** Reference to the document in the index. */
	private Document doc;
	
	/** The score of this document. */
	private double score;
	
	/** The internal id of this document in Lucene's index */
	private int docId;
	
	/** The unique id of this document */
	private String uniqId;
	
	/** The date. */
	private Date docDate;
	
	/** The ranks of this document. */
	private int ranks[];
	
	/** Holds a copy of a document's feature vector. */
	private SparseVector featureVector;

	/** This HashMap will hold all raw feature values, before they get transformed. */
	private HashMap<String, Object> features = new HashMap<String, Object>();


	/**
	 * Create a new scored document with given document id and score.
	 *
	 * @param d The document.
	 * @param id The document id in the Lucene index.
	 */
	public ScoredDocument(Document d, int id) {
		doc = d;
		score = 0.0;
		ranks = new int[2];
		ranks[0] = 0;
		ranks[1] = 0;
		docId = id;
		docDate = null;
		if (getAge("") != -1) {
			addFeature("doc_age", (double) getAge(""));
		}
		addFeature("doc_category", getField("category"));
		try {
			addFeature("doc_articleLength", Double.parseDouble(getField("articleLength")) );
		} catch(Exception e) {
			addFeature("doc_articleLength", 0.0);
		}
		String author = getField("from").replaceAll("<.*>", "");
		addFeature("doc_popularity", FeatureMapping.getInstance().getAuthorPopularity(author));
		
		// Set to null if d is null
		uniqId = getField(Options.get("UNIQ_ID_FIELD"));
	}
	
	/**
	 * Compute the final score for this document.
	 *
	 * @param weights the weight vector
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void computeScore(SparseVector weights) throws IOException {
		featureVector = FeatureMapping.getInstance().mapToFeatureSpace(features);
		this.score = featureVector.dot(weights);
	}
	
	/**
	 * Gets the debug score, which is feature value multiplied by its corresponding weight.
	 *
	 * @param weights the weight vector
	 * @param glueString the glue string
	 * @return the debug score
	 */
	public String getDebugScore(SparseVector weights, String glueString) {
		SparseVector tmp = new SparseVector(featureVector);
		for (int i = 0; i < tmp.size(); i++) {
			tmp.set(i, tmp.get(i)*weights.get(i));
		}
		return FeatureMapping.implodeArray(FeatureMapping.getInstance().getVectorSummary(tmp), glueString);
	}
	
	/**
	 * Sets the document id to something new.
	 *
	 * @param id the new id
	 */
	public void setId(int id) {
		docId = id;
	}
	
	/**
	 * Returns the two ranks of the document.
	 * 
	 * @return The two ranks of the document.
	 */
	public int[] getRanks() {
		return ranks;
	}

	/** 
	 * Returns the id of the document.
	 * 
	 * @return the id of the document
	 */
	public int getId() {
		return docId;
	}
	
	/**
	 * Set the two ranks of the document.
	 * 
	 * @param i1
	 *            The rank of the document according to the first ranking.
	 * @param i2
	 *            The rank of the document according to the second ranking.
	 */
	public void setRanks(int i1, int i2) {
		ranks[0] = i1;
		ranks[1] = i2;
	}

	/**
	 * Returns the document stored in this scored document.
	 * 
	 * @return The document stored in this scored document.
	 */
	public Document getDoc() {
		return doc;
	}

	/**
	 * Sets the stored document.
	 *
	 * @param d the new doc
	 */
	public void setDoc(Document d) {
		doc = d;
		uniqId = getField(Options.get("UNIQ_ID_FIELD"));
	}
	
	/**
	 * Returns the contents of the named field in the document.
	 * 
	 * @param name The name of a document field.
	 * @return The contents of the field.
	 */
	public String getField(String name) {
		if (doc != null)
			return doc.get(name);
		
		return "";
	}

	/**
	 * Return the score of the scored document.
	 * 
	 * @return The score of the scored document.
	 */
	public double getScore() {
		return score;
	}

	/**
	 * Return the age of the document, in days by using the document field named
	 * in the parameter. Returns -1 on failure to parse this field.
	 *
	 * @param fieldName the field name
	 * @return the age
	 */
	public int getAge(String fieldName) {

		Date docDate = getDate();

		if (docDate != null) {
			Date now = new Date();
			double age = Math.floor(1.0 * (now.getTime() - docDate.getTime())
					/ (24 * 3600000));

			return (int) age;
		} else {
			return -1;
		}
	}
	
	/**
	 * Adds a raw feature to the hash map
	 *
	 * @param name the name
	 * @param value the value
	 */
	public void addFeature(String name, Object value) {
		this.features.put(name, value);
	}

	/**
 	 * Get some additional explanation for this document
 	 *
 	 * @return the explanation
 	 */
 	public String getExplanation() {
		return "Ranks are "	+ ranks[0] + "," + ranks[1];
	}
	
	/**
	 * Gets the raw feature values of this document.
	 *
	 * @param glueString the glue string
	 * @return the raw feature values and names
	 */
	public String getRawFeatureValues(String glueString) {
		String output = "";
		TreeSet<String> keys = new TreeSet<String>(features.keySet());
		for (String key : keys) { 
			if (!output.isEmpty())
				output += glueString;
			output += key + ": " + features.get(key).toString();
		}
		return output;
	}
	
	/**
	 * Gets an explained version of the feature vector for this document.
	 *
	 * @param glueString the glue string
	 * @return the feature vector explanation
	 */
	public String getFeatureVectorExplanation(String glueString) {
		return FeatureMapping.implodeArray(FeatureMapping.getInstance().getVectorExplanation(featureVector), glueString);
	}
	
	/**
	 * Allows us to easily compare ScoredDocuments, and therefore sort an array
	 * of them by score.
	 *
	 * @param sd the document that this document will be compared to
	 * @return integer indicating the order
	 */
	public int compareTo(ScoredDocument sd) {

		double sd_score = sd.getScore();
		if (this.score > sd_score)
			return -1;
		else if (this.score < sd_score)
			return 1;
		else
			return 0;
	}

	/**
	 * Implements the toString() method
	 */
	public String toString() {
		return getUniqId() + ":" + score;
	}
	
	/**
	 * Gets the feature vector.
	 *
	 * @return the feature vector
	 */
	public SparseVector getFeatureVector() {
		return featureVector;
	}
	
	/**
	 * Return the unique id of this document.
	 *
	 * @return the unique id
	 */
	public String getUniqId() {
		return uniqId;
	}
	
	/**
	 * Gets the date of this document
	 *
	 * @return the date
	 */
	public Date getDate() {
		if (docDate != null) {
			return docDate;
		} else {
			docDate = getDate(doc);
			return docDate;
		}
 	}
	
	/**
	 * Gets the date of a document
	 *
	 * @param d the document
	 * @return the date
	 */
	public static Date getDate(Document d) {
			
		String strDate = d.get("date");

		if (strDate == null || strDate.length() == 0)
			return null;
		
		Date docDate = null;
		try {
			docDate = DateTools.stringToDate(strDate);
		} catch (ParseException e) {
			// Try the old function. It may fail silently.
			//docDate = DateField.stringToDate(strDate);
			docDate = null;
		}
		
		return docDate;
	}
	
	/**
	 * Return the link to the document.
	 *
	 * @return the link
	 */
	public String getLink() {
		return Options.get("SEARCHER_BASE_URL") + getUniqId();
	}

	/**
	 * Return the link we show the user for the document.
	 *
	 * @return the visible link
	 */
	public String getVisibleLink() {
		return Options.get("SEARCHER_BASE_VISIBLE_URL") + getUniqId();
	}
}
