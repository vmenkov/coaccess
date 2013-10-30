package edu.cornell.cs.osmot.features;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import edu.cornell.cs.osmot.features.Parser;
import edu.cornell.cs.osmot.options.Options;
import no.uib.cipr.matrix.sparse.SparseVector;


/**
 * The feature mapping class. It maps a HashMap containing raw
 * feature values to an actual feature vector. It offers various
 * mapping functions:
 * - COPY: Copies a raw value
 * - BINARIZE: Binarizes a raw value using >=
 * - BINARIZE_LEQ: Binarizes a raw value using <=
 * - CATEGORIZE: Creates a new binary feature for each token in the input list
 * 
 * If there is no mapping function specified, COPY will used.  
 * The keyword "QUADRATIC" will cause a feature to get added to a set of 
 * features Q. For each pairwise combination of features x_1 and x_2 in Q, 
 * there will be an new feature x_1*x_2 added to the final feature vector. 
 * Also, one can specify which similarity function should be used with the
 * SETSIMILARITY command. If no similarity function is set, the default
 * similarity function will be used.  
 * 
 * Here is the complete config file format description:
 * <line> .=. SETSIMILARITY <similarityname> | <featuremapping> | # <comment>
 * <featuremapping> .=. <featurename> | <featurename> <mappingfunction> | <featurename> QUADRATIC <mappingfunction>
 * <mappingfunction> .=. COPY | BINARIZE <thresholds> | BINARIZE_LEQ <thresholds> | COPY | CATEGORIZE <categories>
 * <thresholds> .=. <float> <float> ... <float>
 * <categories> .=. <string> <string> ... <string>
 * <featurename> .=. <string>
 * <comment> .=. <string>
 * 
 * To be more efficient, FeatureMapping will construct a lookup table which
 * maps feature names to indices in the final feature vector. In doing so,
 * feature values that are not set in the input HashMap will not have to
 * be considered when doing the actual mapping.
 * 
 */
public final class FeatureMapping {

	/**
	 * Enumeration representing different mapping functions. 
	 * Please see the comment above for an explanation.
	 */
	enum Function {
		COPY, BINARIZE,	BINARIZE_LEQ, CATEGORIZE, QUADRATIC;
	}

	/**
	 * Helper class which models the mapping for each feature.
	 */
	class Mapping {

		/** The mapping function */
		public Function mapFunc;

		/** The feature name. */
		public String featureName;

		/** Optional parameters for the mapping function */
		public Object parameters;

		/**
		 * Instantiates a new mapping.
		 * 
		 * @param mapFunc the map func
		 * @param featureName the feature name
		 * @param value optional parameters for the mapping function 
		 */
		public Mapping(Function mapFunc, String featureName, Object parameters) {
			this.mapFunc = mapFunc;
			this.featureName = featureName;
			this.parameters = parameters;
		}
	}

	/** FeatureMapping singleton. */
	private static FeatureMapping instance;

	/** The configFile. */
	private final static String configFile = "feature.conf";

	/** The name of the author popularity file. */
	private final static String authorFile = "author_popularity.txt";
	
	/** The author popularity map. */
	private HashMap<String, Integer> authors;
	
	/** Mapping describing the final feature vector */
	private ArrayList<Mapping> map;

	/** Maps a feature name to a list of feature which use this feature. */
	private HashMap<String, ArrayList<Integer>> featureNameToIndices;

	/** Maps a feature name to a list of features which use this quadratic feature. */
	private HashMap<String, ArrayList<Integer>> featureNamesForQuadratic;



	/**
	 * Instantiates a new feature mapping.
	 */
	private FeatureMapping() {
		// Read configuration file
		readConfig(Options.get("CONFIG_DIRECTORY") + "/" + configFile);

		// Create hashtable with author popularity
		readAuthors(Options.get("CONFIG_DIRECTORY") + "/" + authorFile);

	}

	/**
	 * Read in the popularity file that has been created in a pre-processing
	 * step. The popularity of an author is the number of papers he has submitted
	 * using the same e-mail address.
	 * 
	 * @param fileName the file name
	 */
	private void readAuthors(String fileName) {
		// Read in file
		Parser inputFile = new Parser(fileName);
		inputFile.parse("\\|");

		// Create vector from the file
		authors = new HashMap<String, Integer>();
		for (ArrayList<String> line : inputFile.getParsedLines()) {
			String author = line.get(0);
			Integer popularity = Integer.parseInt(line.get(1));
			authors.put(author, popularity);
		}
	}

	/**
	 * Gets the author popularity.
	 * 
	 * @param authorName
	 *            the author name
	 * @return the author popularity
	 */
	public double getAuthorPopularity(String authorName) {
		if (authors.containsKey(authorName)) {
			return (double) authors.get(authorName);
		} else {
			return 0.0d;
		}
	}

	/**
	 * Gets the single instance of FeatureMapping.
	 * 
	 * @return single instance of FeatureMapping
	 */
	public synchronized static FeatureMapping getInstance() {
		if (instance == null) {
			instance = new FeatureMapping();
		}
		return instance;
	}

	/**
	 * Reads in the config file. For a description of the format used,
	 * please see the comment at the top.
	 * 
	 * @param configFile the config file
	 */
	private void readConfig(String configFile) {
		// Parse configuration file
		Parser p = new Parser(configFile);
		p.parse();
		ArrayList<ArrayList<String>> lines = p.getParsedLines();

		// Parse internal feature space
		String similarity = "";
		map = new ArrayList<Mapping>();
		ArrayList<ArrayList<Integer>> quadraticFeatures = new ArrayList<ArrayList<Integer>>();
		for (int j = 0; j < lines.size(); j++) {
			boolean useQuadratic = false;
			ArrayList<String> line = lines.get(j);
			String label = line.get(0) + similarity;

			String command;
			int offset = 2;
			if (line.size() == 1) {
				// Default: Copy feature
				command = "copy";
			} else if (line.size() >= 2 && line.get(1).equalsIgnoreCase("quadratic")) {
				command = line.get(2);
				useQuadratic = true;
				quadraticFeatures.add(new ArrayList<Integer>());
				offset = 3;
			} else if (line.get(0).equalsIgnoreCase("setsimilarity")) {
				command = line.get(0);
			} else {
				command = line.get(1);
			}

			if (command.equalsIgnoreCase("binarize")) {
				// Binarize feature using custom thresholds using >=
				for (int k = offset; k < line.size(); k++) {
					if (useQuadratic)
						quadraticFeatures.get(quadraticFeatures.size() - 1).add(map.size());
					map.add(new Mapping(Function.BINARIZE, label, Double.parseDouble(line.get(k))));
				}
			} else if (command.equalsIgnoreCase("binarize_leq")) {
				// Binarize feature using custom thresholds using <=
				for (int k = offset; k < line.size(); k++) {
					if (useQuadratic)
						quadraticFeatures.get(quadraticFeatures.size() - 1).add(map.size());
					map.add(new Mapping(Function.BINARIZE_LEQ, label, Double.parseDouble(line.get(k))));

				}
			} else if (command.equalsIgnoreCase("categorize")) {
				// Build a mapping for categorial features
				for (int k = offset; k < line.size(); k++) {
					if (useQuadratic)
						quadraticFeatures.get(quadraticFeatures.size() - 1).add(map.size());
					map.add(new Mapping(Function.CATEGORIZE, label, line.get(k)));
				}
			} else if (command.equalsIgnoreCase("copy")) {
				if (useQuadratic)
					quadraticFeatures.get(quadraticFeatures.size() - 1).add(map.size());
				map.add(new Mapping(Function.COPY, label, -1.0f));
			} else if (command.equalsIgnoreCase("setsimilarity")) {
				similarity = line.get(1);
				if (similarity.equals("default")) {
					similarity = "";
				} else {
					similarity = "_" + similarity;
				}
			}

		}

		// Create a lookup table to map keys back to indices in the feature
		// vector that use that key
		featureNameToIndices = new HashMap<String, ArrayList<Integer>>();
		for (int i = 0; i < map.size(); i++) {
			Mapping m = map.get(i);
			if (!featureNameToIndices.containsKey(m.featureName)) {
				featureNameToIndices.put(m.featureName, new ArrayList<Integer>());
			}
			featureNameToIndices.get(m.featureName).add(i);
		}

		featureNamesForQuadratic = new HashMap<String, ArrayList<Integer>>();
		// Add quadratic features to map
		for (int i = 0; i < quadraticFeatures.size(); i++) {
			for (int j = i + 1; j < quadraticFeatures.size(); j++) {
				ArrayList<Integer> set1 = quadraticFeatures.get(i);
				ArrayList<Integer> set2 = quadraticFeatures.get(j);

				// For each pair of features in those sets, add new mapping
				for (int k = 0; k < set1.size(); k++) {
					for (int l = 0; l < set2.size(); l++) {
						// Create new entry in mapping
						int[] indices = { set1.get(k), set2.get(l) };
						map.add(new Mapping(Function.QUADRATIC, "combined", indices));

						// Add entry to reverse map
						Mapping m = map.get(set1.get(k));
						if (!featureNamesForQuadratic.containsKey(m.featureName)) {
							featureNamesForQuadratic.put(m.featureName, new ArrayList<Integer>());
						}
						featureNamesForQuadratic.get(m.featureName).add(map.size() - 1);
					}
				}
			}
		}

	}

	/**
	 * Prints the current feature map
	 */
	public void print() {
		for (int i = 0; i < map.size(); i++) {
			System.out.println(getFeatureDescription(i, true, false));
		}
	}

	/**
	 * Prints a vector with the description of each feature
	 * 
	 * @param w the weight vector
	 */
	public void printVector(SparseVector w) {
		for (int i = 0; i < w.size(); i++) {
			if (w.get(i) > 0.0d) {
				System.out.println(i + ". " + w.get(i) + " (" + getFeatureDescription(i, false, false) + ")");
			}
		}
	}

	/**
	 * Implodes an array using glue string between different array entries.
	 * 
	 * @param inputArray the input array
	 * @param glueString the glue string
	 * @return imploded array
	 */
	public static String implodeArray(String[] inputArray, String glueString) {
		// Output variable
		String output = "";

		if (inputArray.length > 0) {
			StringBuilder sb = new StringBuilder();
			sb.append(inputArray[0]);

			for (int i = 1; i < inputArray.length; i++) {
				sb.append(glueString);
				sb.append(inputArray[i]);
			}
			output = sb.toString();
		}
		return output;
	}

	/**
	 * Gets an array containing the feature values along with their description.
	 * 
	 * @param w the vector
	 * @return array with feature values and their descriptions 
	 */
	public String[] getVectorExplanation(SparseVector w) {
		ArrayList<String> result = new ArrayList<String>();
		for (int i = 0; i < w.size(); i++) {
			if (w.get(i) > 0.0d) {
				result.add(i + ". " + w.get(i) + " (" + getFeatureDescription(i, false, false) + ")");
			}
		}
		String[] retVal = new String[result.size()];
		retVal = result.toArray(retVal);
		return retVal;
	}

	/**
	 * Returns a summary of the vector, consolidating features by adding
	 * up the values for features that use the same raw feature.
	 * 
	 * @param w the vector
	 * @return the vector summary
	 */
	public String[] getVectorSummary(SparseVector w) {
		HashMap<String, Double> accumulate = new HashMap<String, Double>();
		for (int i = 0; i < w.size(); i++) {
			if (w.get(i) != 0.0d) {
				String featureName = map.get(i).featureName;
				if (map.get(i).mapFunc != Function.QUADRATIC) {
					if (!accumulate.containsKey(featureName)) {
						accumulate.put(featureName, w.get(i));
					} else {
						accumulate.put(featureName, accumulate.get(featureName) + w.get(i));
					}
				}
			}
		}

		// Create a summary that contains all summed fields
		String[] summary = new String[accumulate.size()];
		int i = 0;
		for (Map.Entry<String, Double> entry : accumulate.entrySet()) {
			summary[i] = entry.getKey() + ": " + entry.getValue();
			i++;
		}
		return summary;
	}

	/**
	 * Maps a HashMap to feature space
	 * 
	 * @param input HashMap containing feature name - feature value pairs
	 * @param onlyCopy if true, only features whose mapping is the copy function will be set
	 * @return feature vector
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public SparseVector mapToFeatureSpace(HashMap<String, Object> input, boolean onlyCopy) throws IOException {
		SparseVector result = new SparseVector(noOutputFeatures());

		// Fill in linear features
		for (String featureName : input.keySet()) {
			if (featureNameToIndices.containsKey(featureName)) {
				for (Integer i : featureNameToIndices.get(featureName)) {
					fillIn(input.get(featureName), onlyCopy, result, i);
				}

			}
		}

		// Fill in quadratic features
		for (String featureName : input.keySet()) {
			if (featureNamesForQuadratic.containsKey(featureName)) {
				for (Integer i : featureNamesForQuadratic.get(featureName)) {
					fillIn(map.get(i).parameters, onlyCopy, result, i);
				}

			}
		}

		return result;
	}

	/**
	 * Maps a HashMap to feature space
	 * 
	 * @param input HashMap containing feature name - feature value pairs
	 * @return feature vector
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public SparseVector mapToFeatureSpace(HashMap<String, Object> input) throws IOException {
		return mapToFeatureSpace(input, false);
	}
	
	/**
	 * Fills in a feature into a vector according to the different mapping functions.
	 * 
	 * @param inputVal the input value
	 * @param onlyCopy if true, feature will only be set if the mapping function is COPY
	 * @param result vector that will get set
	 * @param i the index of the feature to fill in
	 */
	private void fillIn(Object inputVal, boolean onlyCopy, SparseVector result, int i) {
		Mapping m = map.get(i);

		switch (m.mapFunc) {
		case COPY:
			result.set(i, (Double) inputVal);
			break;
		case BINARIZE:
			if (((Double) inputVal >= (Double) m.parameters) && !onlyCopy) {
				result.set(i, 1.0d);
			}
			break;
		case BINARIZE_LEQ:
			if (((Double) inputVal <= (Double) m.parameters) && !onlyCopy) {
				result.set(i, 1.0d);
			}
			break;
		case CATEGORIZE:
			if (!onlyCopy) {
				String category = "(?i).*\\b" + (String) m.parameters + "\\b.*";
				if (((String) inputVal).matches(category)) {
					result.set(i, 1.0d);
				}
			}
			break;
		case QUADRATIC:
			if (!onlyCopy) {
				int[] indices = (int[]) inputVal;
				result.set(i, result.get(indices[0]) * result.get(indices[1]));
			}
			break;
		}
	}

	/**
	 * Gets the number of output features.
	 * 
	 * @return number of output features
	 */
	public int noOutputFeatures() {
		return map.size();
	}

	/**
	 * Gets the feature description.
	 * 
	 * @param index the index
	 * @param includeNumber the include number
	 * @return the feature description
	 */
	public String getFeatureDescription(int index, boolean includeNumber) {
		return getFeatureDescription(index, includeNumber, false);
	}

	/**
	 * Gets the description for a particular feature.
	 * 
	 * @param index the index of the feature
	 * @param includeIndex include the index of the feature
	 * @param noMapping if true, will not include the mapping function
	 * @return the description string
	 */
	public String getFeatureDescription(int index, boolean includeIndex, boolean noMapping) {
		String description = "";
		Mapping m = map.get(index);

		// Create description for this feature index
		if (includeIndex) {
			description += index + ". (" + m.featureName + ")";
		} else {
			description += m.featureName;
		}
		if (!noMapping)
			description += " - Mapping: " + m.mapFunc;
		if (m.mapFunc == Function.BINARIZE) {
			description += " >= " + m.parameters;
		} else if (m.mapFunc == Function.BINARIZE_LEQ) {
			description += " <= " + m.parameters;
		} else if (m.mapFunc == Function.QUADRATIC) {
			int[] indices = (int[]) m.parameters;
			description += " = (" + getFeatureDescription(indices[0], false, true) + ")*("
					+ getFeatureDescription(indices[1], false, true) + ")";
		} else if (m.mapFunc != Function.COPY) {
			// Only add value if the mapping is not just copying
			description += " " + m.parameters;
		}

		return description;
	}

	/**
	 * The main method, only used for testing.
	 *
	 * @param args not used
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {

		FeatureMapping.getInstance().print();
		HashMap<String, Object> testMap = new HashMap<String, Object>();
		testMap.put("score_category", 3.0);
		testMap.put("score_title", 5.0);
		testMap.put("score_whatever", 3.0);
		SparseVector v = FeatureMapping.getInstance().mapToFeatureSpace(testMap);
		FeatureMapping.getInstance().printVector(v);
		System.out.println(FeatureMapping.getInstance().getAuthorPopularity("Vasily Golyshev"));
	}

}
