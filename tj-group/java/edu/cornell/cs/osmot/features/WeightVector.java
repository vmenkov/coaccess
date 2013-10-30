package edu.cornell.cs.osmot.features;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import no.uib.cipr.matrix.sparse.SparseVector;

/**
 * Helper class for storing and loading the weight vector. 
 * 
 * @author Tobias Schnabel
 * @version 1.0, May 2012
 */
public class WeightVector {
	
	/**
	 * Loads a weight vector from a file.
	 *
	 * @param fileName the file name
	 * @return object array holding the weight vector and the number of updates that occurred so far
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static synchronized Object[] constructFromFile(String fileName) throws IOException {

		// Read in file
		Parser inputFile = new Parser(fileName);
		inputFile.parse();

		// Create vector from the file
		ArrayList<ArrayList<String>> lines = inputFile.getParsedLines();
		Integer updatesSoFar = 0;
		if (lines.get(0).get(0).equalsIgnoreCase("number")) {
			 updatesSoFar = Integer.parseInt(lines.get(0).get(3));
			 lines.remove(0);
		}
		
		SparseVector weightVector = new SparseVector(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			double value = Double.parseDouble(lines.get(i).get(0));
			weightVector.set(i, value);
		}

		Object[] retVals = {weightVector, updatesSoFar};
		return retVals;
	}

	/**
	 * Write a weight vector to a file.
	 *
	 * @param w the weight vector
	 * @param fileName the file name
	 * @param noUpdates the current number of perceptron updates
	 */
	public static synchronized void writeToFile(SparseVector w, String fileName, int noUpdates) {
		// Open output file
		BufferedWriter out;
		try {
			out = new BufferedWriter(new FileWriter(fileName));
			out.write("Number of iterations: " + noUpdates + "\n");
			for (int i = 0; i < w.size(); i++) {
				out.write(Double.toString(w.get(i)));
				out.write(" # " + FeatureMapping.getInstance().getFeatureDescription(i, true) + "\n");
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Write a weight vector including comments to a file.
	 *
	 * @param w the weight vector
	 * @param fileName the file name
	 * @param comments additional comments to be added to the output file
	 */
	public static synchronized void writeToFile(SparseVector w, String fileName, String[] comments) {
		// Open output file
		BufferedWriter out;
		try {
			out = new BufferedWriter(new FileWriter(fileName));
			for (String c : comments) {
				out.write("# " + c.replaceAll("\\n", " ") + "\n");
			}
			for (int i = 0; i < w.size(); i++) {
				out.write(Double.toString(w.get(i)));
				out.write(" # " + FeatureMapping.getInstance().getFeatureDescription(i, true) + "\n");
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Construct the baseline weight vector.
	 * It initializes the weight for all raw score features to 100.
	 * 
	 * These are also the weights that the perceptron will start with and setting the weights to 100 instead of 1
	 * is supposed to damp the effects of each click at the beginning of our learning. The reasoning behind this is
	 * that we don't want to the ranking to change to quickly so that users can still provide good feedback.
	 *
	 * @return the sparse vector
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static synchronized SparseVector constructBaseline() throws IOException {
		HashMap<String, Object> baseline = new HashMap<String, Object>();
		String[] fields = { "score_abstract", "score_paper", "score_article-class", "score_from", "score_date",
				"score_journal-ref", "score_articleLength", "score_authors", "score_title", "score_category",
				"score_article", "score_group", "score_dateIndexed", "score_subj-class", "score_acm-class",
				"score_comments" };

		// Each field gets initialized to 100
		for (String field : fields) {
			baseline.put(field, 100.0);
		}
		return FeatureMapping.getInstance().mapToFeatureSpace(baseline, true);
	}

	public static synchronized SparseVector constructBaseline2() throws IOException {
		HashMap<String, Object> baseline = new HashMap<String, Object>();
		SparseVector tmp = FeatureMapping.getInstance().mapToFeatureSpace(baseline, true);
		for (int i = 0; i <= 35; i++)
			tmp.set(i, 30.0f);
		return tmp;
		
	}
	
	
	/**
	 * The main method, only used for testing.
	 *
	 * @param args not used
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {
		FeatureMapping.getInstance().printVector(constructBaseline());
		writeToFile(constructBaseline(), "my_w.txt", 34);
	}
}
