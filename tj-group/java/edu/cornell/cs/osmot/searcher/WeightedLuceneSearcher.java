package edu.cornell.cs.osmot.searcher;

import no.uib.cipr.matrix.sparse.SparseVector;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.SetBasedFieldSelector;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.misc.SweetSpotSimilarity;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.ReaderUtil;
import org.apache.lucene.util.Version;

import java.sql.SQLException;
import java.util.*;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.FileReader;

import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.cache.SimpleCache;
import edu.cornell.cs.osmot.features.WeightVector;
import edu.cornell.cs.osmot.logger.EvalInterleaving;
import edu.cornell.cs.osmot.logger.Logger;

/**
 * This class implements searching a Lucene index with weighted scoring. It also implements the online perturbed-perceptron update
 * rule so that weights can be updated with every click. Each retrieved document will have score and rank 
 * features for each field, i.e. 
 * score on author field, score on article, rank of document in a query just run on the author field ...
 * The scored document class adds even more features to each document such as the age of a document or the popularity
 * of the authors.
 * It also uses a sparse vector implementation to efficiently store and compute to store the weights for each feature. 
 *
 * @author Karthik Raman, Tobias Schnabel, Filip Radlinski
 * @version 1.3,Jan 2013
 */
public class WeightedLuceneSearcher extends Searcher {

	/** The index directory. */
	protected String indexDir;

	/** The Lucene searchers. */
	private HashMap<String, IndexSearcher> iSearcher = new HashMap<String, IndexSearcher>();
	
	/** The index reader. */
	private IndexReader iReader;
	
	/** The last update. */
	private Date lastUpdate;

	/** The file name of the weight vector file. */
	private String fileName = "";
	
	/** The weight vector. */
	private SparseVector weightVector;
	
	/** The query cache. */
	private SimpleCache<SparseVector> queryCache;
	
	/** The clicks cache. */
	private SimpleCache<HashMap<String, Boolean>> clicksCache;

	/** The query results cache: This enables us to look up the perturbations applied for a query */
	private SimpleCache< ArrayList<Integer> > queryResultsCache;

	/** The query results cache: This enables us to look up the reverse mapping of the perturbations applied for a query i.e What was it compared against*/
	private SimpleCache< ArrayList<Integer> > queryResultsRevMapCache;
	
	/** The no updates. */
	private int noUpdates = 0;

	/** The running sum. */
	private double runningSum = 0;
	
	/** Whether to use normalized scores. */
	private boolean useNormalizedScores;
	
	/** Fields to do the first step of the search on */
	private String[] candidateFields;
	
	/** Fields that we want to include in the feature set */
	private String[] subscoreFields;

	/** The fields to load when getting a document from the index. */
	private FieldSelector fieldsToLoad;

	/** The indices of the weights that we want to clip at 0 or 1 */
	private ArrayList<Integer> weightsClipAt0;
	private ArrayList<Integer> weightsClipAt1; 

	/**
	 * Write the running sum to a file.
	 *
	 * @param w the weight vector
	 * @param fileName the file name
	 * @param noUpdates the current number of perceptron updates
	 */
	private synchronized void writeSumToFile(String fileName) {
		// Open output file
		BufferedWriter out;
		try {
			out = new BufferedWriter(new FileWriter(fileName));
			out.write(runningSum + "\n");
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Read the running sum from a file.
	 *
	 * @param w the weight vector
	 * @param fileName the file name
	 * @param noUpdates the current number of perceptron updates
	 */
	private synchronized double readSumFromFile(String fileName) {
		// Open file
		try {
			BufferedReader inp  = new BufferedReader (new FileReader(fileName) );
			String line = inp.readLine();
			if (line != null)
				return Double.parseDouble( line.trim() );
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0.0;
	}



	/**
	 * Instantiates a new weighted Lucene searcher. If the filename is empty, a new weight vector with
	 * the baseline weights (raw scores weights = 100) will be created. If a filename is specified, but 
	 * doesn't exist, a new file will be created. 
	 *
	 * @param indexDir the directory where the Lucene index is stored.
	 * @param file the weight vector file (optional)
	 * @param normalizeScores whether to use the normalized Lucene scores for ranking
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public WeightedLuceneSearcher(String indexDir, String file, boolean normalizeScores) throws IOException {
		init(indexDir);

		// Create cache for weight vectors associated with a query
		// Weight vectors get stored for 1 hour
		queryCache = new SimpleCache<SparseVector>(3600);
		clicksCache = new SimpleCache<HashMap<String, Boolean>>(3600);
		queryResultsCache = new SimpleCache< ArrayList<Integer> >(7200);		//Query Results are stored for two hour
		queryResultsRevMapCache = new SimpleCache< ArrayList<Integer> >(7200);		//Query Results are stored for two hour
		useNormalizedScores = normalizeScores;
		// Read in prior weight vector, if it exists
		fileName = file;
		boolean exists = (new File(fileName)).exists();
		if (!file.isEmpty() && exists) {
			Object[] objects = WeightVector.constructFromFile(fileName);
			weightVector = (SparseVector) objects[0];
			noUpdates = (Integer) objects[1];

			//Read the running sum also from a file
			runningSum = readSumFromFile(   Options.get("LOG_DIRECTORY") + "/runningsum_" + noUpdates + ".txt" );

//		} else if (!file.isEmpty()) {
//			weightVector = WeightVector.constructBaseline2();
		} else {
			weightVector = WeightVector.constructBaseline2();
		}
		if (!fileName.isEmpty()) {
			WeightVector.writeToFile(weightVector, fileName, noUpdates);
		}
		
		// Get indices of weights that should be clipped after an update
		weightsClipAt0 = Options.getRange("WEIGHTS_CLIP_AT_0", weightVector.size() - 1);
		weightsClipAt1 = Options.getRange("WEIGHTS_CLIP_AT_1", weightVector.size() - 1);
	}

	/**
	 * Reload the searcher if we need to - it has a lifetime before we are
	 * guaranteed to reload it so as to see any changes.
	 *
	 * @param force whether to force and update
	 */
	public void updateSearcher(boolean force) {
		try {
			// Check if we need to update the searcher. If so, reload the index
			// searcher.
			long openTime = new Date().getTime() - lastUpdate.getTime();
			openTime = openTime / (1000 * 60);
			if (force || openTime > Options.getInt("SEARCHER_LIFETIME")) {
				Logger.log("Reloading Searcher. Open time was " + openTime);
				for (IndexSearcher searcher : iSearcher.values()) {
					searcher.close();
				}
			    IndexReader newReader = IndexReader.openIfChanged(iReader);
				if (newReader != null) {
					iReader.close();
					iReader = newReader;
				}
				initSearchers();
				lastUpdate = new Date();
			}
		} catch (Exception e) {
			Logger.log("Exception reloading index: " + e.toString() + ": " + e.getMessage());
		}
	}

	/**
	 * Opens the Lucene index and creates the required Lucene instances.
	 *
	 * @param idxDir the index directory
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected void init(String idxDir) throws IOException {

		this.indexDir = idxDir;

		// Set up IndexSearcher
		Directory dir = FSDirectory.open(new File(indexDir));
		iReader = IndexReader.open(dir);
		initSearchers();
				
		// Get the fields that exist in the document collection.
		String[] excludeFromCandidates = {"article_1", "article_2", "article_3", "article_4", "article_5", 
				"articleLength", "catch-all"};
		candidateFields = loadFields(excludeFromCandidates);
		
		String[] excludeFromSubscores = {"catch-all"};
		subscoreFields = loadFields(excludeFromSubscores);
		
		// Select the document fields that we want to load lazily 
		HashSet<String> eagerFields = new HashSet<String>();
		HashSet<String> lazyFields = new HashSet<String>();
		lazyFields.add("abstract");
		String[] noAbstract = {"abstract"};
		eagerFields.addAll(Arrays.asList(loadFields(noAbstract)));
		fieldsToLoad = new SetBasedFieldSelector(eagerFields, lazyFields);
		
		if (debug)
			log("WeightedLuceneSearcher initialized.");

		// This determines when we reload the index next.
		lastUpdate = new Date();
	}
	
	/**
	 * Initializes the 3 different Lucene index searchers. Each searcher
	 * uses a different similarity measure.
	 */
	private void initSearchers() {
		// First searcher uses default similarity
		iSearcher.put("default", new IndexSearcher(iReader));
		
		iSearcher.put("phraseSim", new IndexSearcher(iReader));
		
		// Second searcher uses a simplified similarity
		IndexSearcher iSearcherSim1 = new IndexSearcher(iReader);
		iSearcherSim1.setSimilarity(new SimplifiedSimilarity());
		iSearcher.put("simplifiedSim",iSearcherSim1);
		
		
		// Third searcher uses the sweet spot similarity
		/*IndexSearcher iSearcherSim2 = new IndexSearcher(iReader);
		SweetSpotSimilarity sweetspot = new SweetSpotSimilarity();
		// We chose arbitrary parameters
		sweetspot.setLengthNormFactors(1,1,0.5f);
		sweetspot.setBaselineTfFactors(5, 10);
		sweetspot.hyperbolicTf(5);
		sweetspot.setHyperbolicTfFactors(3.3f, 7.7f, Math.E, 5.0f);
		iSearcherSim2.setSimilarity(sweetspot);
		iSearcher.put("sweetspotSim", iSearcherSim2);*/
		
	}
	
	
	/* (non-Javadoc)
	 * @see edu.cornell.cs.osmot.searcher.Searcher#search(java.lang.String)
	 */
	public RerankedHits search(String query) throws ParseException, IOException {
		return search(query, true);
	}

	/**
	 * This is a two parameter call used to indicate that the search should be done *WITH* perturbation
	 */
	public RerankedHits search(String query, boolean useCachedWeights) throws ParseException, IOException {
		return search(query, useCachedWeights, true);
	}


	/**
	 * Gets the analyzer that will be used for searching. 
	 *
	 * @return the analyzer
	 */
	private static Analyzer getAnalyzer() {
		Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
		// Split categories at whitespace
		analyzerPerField.put("category", new WhitespaceAnalyzer(Version.LUCENE_36));
		// Don't tokenize the unique id field at all
		analyzerPerField.put(Options.get("UNIQ_ID_FIELD"), new KeywordAnalyzer());
		// All other fields will use the default analyzer
		PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(new StandardAnalyzer(Version.LUCENE_36),
				analyzerPerField);
		return wrapper;
	}
	
	/**
	 * Gets the weights vector. We might use the cached weights to have constant
	 * rankings for a query, otherwise users might get confused when navigating
	 * through the list of results.
	 *
	 * @param query the query
	 * @param useCachedWeights indicates whether to use cached weights
	 * @return the weight vector
	 */
	private SparseVector getWeights(String query, boolean useCachedWeights) {
		SparseVector weights;
		if (useCachedWeights) {
			if (queryCache.get(query) != null) {
				// If query is already in cache, get it
				if (debug)
					Logger.log("Got cached weight vector.");
				weights = queryCache.get(query);
			} else {
				// Cache current weight vector
				if (debug)
					Logger.log("Put weight vector in cache.");
				weights = new SparseVector(weightVector);
				queryCache.put(query, weights);

			}
		} else {
			// Use current weight vector
			if (debug)
				Logger.log("Use current weight vector.");
			weights = weightVector;
		}
		return weights;
	}
	public static Query phrasedQuery(String fieldName, String query, Analyzer analyzer) {
		ArrayList<String> result = new ArrayList<String>();
		query = query.replaceAll("\\w+:\\w+", "");
		TokenStream stream = analyzer.tokenStream(fieldName, new StringReader(query));
		try {
			while (stream.incrementToken()) {
				result.add(stream.getAttribute(CharTermAttribute.class).toString());
			}
		} catch (IOException e) {
		}
		BooleanQuery rQuery = new BooleanQuery();
		for (int i = 0; i < result.size() - 1; i++) {
			PhraseQuery phrase = new PhraseQuery();
			phrase.setSlop(2);
			phrase.add(new Term(fieldName, result.get(i)));
			phrase.add(new Term(fieldName, result.get(i + 1)));
			rQuery.add(phrase, BooleanClause.Occur.SHOULD);
		}
		return rQuery;
	}
	
	/**
	 * Document are only retrieved when we need them.
	 * Returns the full set of document ids, unlike search(). You can specify
	 * which documents you want the actual content from.
	 *
	 * Additionally if the query was recently asked for, then it will be served directly from the cache
	 *
	 * @param query The query to run
	 * @param useCachedWeights the use cached weights
	 * @return the reranked hits
	 * @throws ParseException the parse exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public RerankedHits search(String query, boolean useCachedWeights, boolean perturbResults) throws ParseException, IOException {
		if (debug)
			Logger.log("WeightedLuceneSearcher.SearchFast: Called with query " + query);
		
		Stopwatch timer = new Stopwatch();
		
		// We either use scores or ranks when reranking
		String uniqIdFieldName = Options.get("UNIQ_ID_FIELD");
		int noOfResults = Options.getInt("SEARCHER_NUM_FIELD_RESULTS");
		boolean queryContainsAuthor = false;
		
		updateSearcher(false);
		
		// Get a new analyzer
		Analyzer analyzer = getAnalyzer();
		timer.addPoint("Created analyzer.");
		
		/* 
		 * Step 1:
		 * Generate a set of candidates that will get reranked
		 * This set consists of the union of the top 200 matches on the catchAll field, as well
		 * as the top 50 documents for each field
		 */
		HashSet<String> candidateSet = new HashSet<String>();

		String[] docIdCache = FieldCache.DEFAULT.getStrings(iReader, uniqIdFieldName);
		timer.addPoint("FieldCache created.");
		
		// Get the top 200 matches on the catchAll field
		// A document has to match every word in the query
		QueryParser catchAll = new QueryParser(Version.LUCENE_36, "catch-all", getAnalyzer());
		catchAll.setDefaultOperator(QueryParser.AND_OPERATOR);
		Query catchAllQuery = catchAll.parse(query);
		CachingWrapperFilter catchAllFilter = new CachingWrapperFilter(new QueryWrapperFilter(catchAllQuery));
		TopDocs catchAllHits = iSearcher.get("default").search(catchAllQuery, noOfResults);
		timer.addPoint("Issued catch-all query.");
		for (ScoreDoc result : catchAllHits.scoreDocs) {
			String uniqId = docIdCache[result.doc];
			candidateSet.add(uniqId);
		}
		timer.addPoint("Added catch-all query results to candidate set.");
		if (debug)
			Logger.log("General hits query returned " + catchAllHits.totalHits + " results.");

		// For every field, find the top 50 documents that match part of the
		// query and also include all words somewhere.
		for (int j = 0; j < candidateFields.length; j++) {
			Query fieldQuery = new QueryParser(Version.LUCENE_36, candidateFields[j], analyzer).parse(query);
			TopDocs hitsTmp = iSearcher.get("default").search(fieldQuery, catchAllFilter, 50);
			//TopDocs hitsTmp = iSearcher.get("default").search(fieldQuery, 50);
			if (candidateFields[j].equalsIgnoreCase("authors") && hitsTmp.scoreDocs.length > 5) {
				queryContainsAuthor = true;
			}
			for (int r = 0; r < 50 && r < hitsTmp.scoreDocs.length; r++) {
				ScoreDoc result = hitsTmp.scoreDocs[r];
				String uniqId = docIdCache[result.doc];
				candidateSet.add(uniqId);
			}
		}
		timer.addPoint("Found top 50 documents for each field.");
		
		// Create a filter representing the set of candidates
		String[] candidateArray = candidateSet.toArray(new String[candidateSet.size()]);
		Filter candidateFilter = new CachingWrapperFilter(new FieldCacheTermsFilter(uniqIdFieldName, candidateArray));
		if (debug)
			Logger.log("Created filter for all documents we want to look at.");
		timer.addPoint("Created candiate set.");
		
		/*
		 * Step 2:
		 * Query fields separately, collecting scores for each field
		 */
		
		// This holds the final result set
		HashMap<Integer, HashMap<String, Double>> resultSet = new HashMap<Integer, HashMap<String, Double>>();
		
		// For every result we have, get the complete scores
		if (!candidateSet.isEmpty()) {
			for (Map.Entry<String, IndexSearcher> similarity : iSearcher.entrySet()) {
				IndexSearcher searcherSim = similarity.getValue();
				String name = similarity.getKey();
				if (name.equals("default")) {
					name = "";
				} else {
					name = "_" + name;
				}
				for (int j = 0; j < subscoreFields.length; j++) {
					Query fieldQuery;
					if (similarity.getKey().equalsIgnoreCase("phraseSim")) {
						fieldQuery = phrasedQuery(subscoreFields[j], query, analyzer);
					} else {
						fieldQuery = new QueryParser(Version.LUCENE_36, subscoreFields[j], analyzer).parse(query);
					}
					TopDocs h = searcherSim.search(fieldQuery, candidateFilter, candidateSet.size());
					for (int r = 0; r < h.scoreDocs.length; r++) {
						int id = h.scoreDocs[r].doc;
						if (!resultSet.containsKey(id)) {
							resultSet.put(id, new HashMap<String, Double>());
						}
						
						HashMap<String, Double> scores = resultSet.get(id);
						scores.put("rank_" + subscoreFields[j] + name, new Double(r+1));
						if (useNormalizedScores) {
							scores.put("score_" + subscoreFields[j] + name,new Double(h.scoreDocs[r].score/h.getMaxScore()));
						} else {
							scores.put("score_" + subscoreFields[j] + name,new Double(h.scoreDocs[r].score));
						}
					}
				}
			}
			
		}
		if (debug)
			Logger.log("Final result set has " + resultSet.size() + " documents.");
		timer.addPoint("Got all features.");
		// Get current weight vector
		SparseVector weights = getWeights(query, useCachedWeights);

		// Create ScoredDocument for each candidate
		ScoredDocument[] sortedResults = new ScoredDocument[resultSet.size()];
		int index = 0;
		for (Map.Entry<Integer, HashMap<String, Double>> result : resultSet.entrySet()) {
			Integer docId = result.getKey();
			HashMap<String, Double> scores = result.getValue();
			
			// Add all features (scores + ranks for each subquery)
			ScoredDocument sd = new ScoredDocument(iSearcher.get("default").doc(docId, fieldsToLoad), docId);
			for (Map.Entry<String, Double> scorePair : scores.entrySet()) {
				String name = scorePair.getKey();
				Double value = scorePair.getValue();
				sd.addFeature(name, value);
			}
			if (queryContainsAuthor) {
				sd.addFeature("contains_author", "true");
			} else {
				sd.addFeature("contains_author", "false");
			}
			sd.computeScore(weights);
			
			// Add to final output array
			sortedResults[index]=sd;
			index++;
		}

		// Sort by score
		Arrays.sort(sortedResults);
		timer.addPoint("Created final (unperturbed) ScoredDocument[].");
		if (debug)
			Logger.log("Results sorted.");

		if (!perturbResults) {	//Return directly if no perturbation required
			// Make a new RerankedHits and return that
			RerankedHits rh = new RerankedHits(sortedResults);

			if (debug) {
				Logger.log(timer.summary());
				Logger.flushAll();
			}
			return rh;
		}

		// Perturb the ranking at this point
		int tot_size = resultSet.size();
		ScoredDocument[] perturbedResults = new ScoredDocument[tot_size];

		//Check if this query exists in the cache and has a corresponding perturbation
		if (queryResultsCache.get(query) != null){
			ArrayList<Integer> perturbation = queryResultsCache.get(query);
			for (int i2 = 0; i2 < tot_size; i2++)
				perturbedResults[i2] = sortedResults[ perturbation.get(i2) ];
			
			if ( debug )
				Logger.log("Results perturbed by Fair Pairs using existing perturbation.");
		} else {
			// We will use fair pairs (perturbing only 2i-1 and 2i results.
			boolean perturb12 = true;
			int offset = 0;
			ArrayList<Integer> perturbation = new ArrayList<Integer>();
			ArrayList<Integer> perturbationRev = new ArrayList<Integer>();
			if ( (Math.random() <= 0.5) && (tot_size > 0) ){		//Perturb the 2-3 rankings onwards
				offset = 1;
				perturbedResults[0] = sortedResults[0]; perturbation.add(0); perturbationRev.add(0);
				Logger.log("Perturbing query " + query + " as 2-3");
			} else
				Logger.log("Perturbing query " + query+ " as 1-3");

			int half_size = 0;
			boolean leftOver = false;
			if ((tot_size - offset) % 2 == 1){
				half_size = (tot_size - offset - 1) / 2;
				leftOver = true;
			} else
				half_size = (tot_size - offset)/2;
	
			double swapProb = 0.5;
			//double swapProb = -1;
			for (int i2 = 0 ; i2 < half_size; i2++) {
				double randDraw = Math.random();
				int curI = (2*i2) + offset;
				if ( randDraw <= swapProb ) {	//Swap the results
					perturbedResults[ curI ] = sortedResults[ curI + 1 ];  perturbation.add(curI + 1); perturbationRev.add(curI + 1);
					perturbedResults[ curI + 1 ] = sortedResults[ curI ];  perturbation.add( curI ); perturbationRev.add( curI );

				} else {	//Do not swap
					perturbedResults[ curI ] = sortedResults[ curI  ];  perturbation.add(curI); perturbationRev.add(curI + 1);
					perturbedResults[ curI + 1 ] = sortedResults[ curI + 1 ];  perturbation.add(curI + 1); perturbationRev.add(curI);
				}
			}

			if(leftOver && (tot_size > 0) ){
				perturbedResults[tot_size - 1] = sortedResults[tot_size - 1];		//For the last result
				perturbation.add(tot_size - 1); perturbationRev.add(tot_size - 1);
			}

			//if ( debug )
			Logger.log("Results perturbed by Fair Pairs.");

			//Store the ranking in the cache
			queryResultsCache.put( query, perturbation );
			queryResultsRevMapCache.put( query, perturbationRev );
			if (debug)
				Logger.log("Results entered into the cache.");
		}


		
		// Make a new RerankedHits and return that
		RerankedHits rh = new RerankedHits(perturbedResults);
		
		if (debug)
			Logger.log("After wrapping, there are " + rh.length() + " results.");
		if (debug) {
			Logger.log(timer.summary());
			Logger.flushAll();
		}
		return rh;
	}

	/**
	 * Search for all the words in the query and sort the results by date in descending order.
	 *
	 * @param query the query
	 * @return the reranked hits object
	 * @throws ParseException the parse exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public RerankedHits searchDate(String query) throws ParseException, IOException {
		// We want each result to match all words in the query
		QueryParser catchAll = new QueryParser(Version.LUCENE_36, "catch-all", getAnalyzer());
		catchAll.setDefaultOperator(QueryParser.AND_OPERATOR);
		Query catchAllQuery = catchAll.parse(query);
		
		Sort byDate = new Sort(new SortField("date", SortField.STRING, true));
		TopDocs results = iSearcher.get("default").search(catchAllQuery, null, Options.getInt("SEARCHER_NUM_FIELD_RESULTS"), byDate);

		ScoredDocument[] sortedResults = new ScoredDocument[results.scoreDocs.length];
		for (int i = 0; i < results.scoreDocs.length; i++) {
			int docId = results.scoreDocs[i].doc;
			sortedResults[i]=new ScoredDocument(iSearcher.get("default").doc(docId), docId);
		}
		// Make a new RerankedHits and return that - its sorted by date
		RerankedHits rh = new RerankedHits(sortedResults, true, null);

		return rh;
	}
	
	/**
	 * Gets the current weight vector.
	 *
	 * @return the weight vector
	 */
	public SparseVector getWeightVector() {
		return weightVector;
	}

	/**
	 * Return the fields present in the document collection.
	 *
	 * @param forbiddenFields fields that should not be returned
	 * @return An array of the fields present in the index.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected String[] loadFields(String[] forbiddenFields) throws IOException {
		// Create set containing fields that should not be included
		HashSet<String> forbiddenField = new HashSet<String>();
		for (String field : forbiddenFields) {
			forbiddenField.add(field);
		}
		
		FieldInfos myFields = ReaderUtil.getMergedFieldInfos(iReader);
		ArrayList<String> allFields = new ArrayList<String>();
		for (FieldInfo field : myFields) {
			if ((field.isIndexed) && !forbiddenField.contains(field.name))
				allFields.add(field.name);
		}
		return allFields.toArray(new String[allFields.size()]);
	}

	/**
	 * Pick a random document from the collection.
	 *
	 * @return The id of a random valid document in the index.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected int randomDoc() throws IOException {

		int maxDoc = iSearcher.get("default").maxDoc();
		int docNo;

		while (true) {
			docNo = (int) Math.floor(Math.random() * maxDoc);
			try {
				if (iSearcher.get("default").doc(docNo) != null)
					return docNo;
			} catch (Exception e) {
				// Ignore (we might for example pick a deleted document)
			}
		}
	}

	/**
	 * Pick a random document from the collection in the category specified.
	 * This is terribly inefficient!
	 *
	 * @param category the category
	 * @return The id of a random valid document in this category, or -1 if we
	 * fail in finding on.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	protected int randomDoc(String category) throws IOException {

		int counter = 0;

		if (category == null)
			return randomDoc();

		while (counter < 1000) {
			int i = randomDoc();
			String s = iSearcher.get("default").doc(i).get("category");
			if (s.equals(category))
				return i;

			counter++;
		}

		return -1;
	}

	/**
	 * Pick a random document from the collection in the category specified.
	 * This is terribly inefficient!
	 *
	 * @param category the category
	 * @return The id of a random valid document in this category, or -1 if we
	 * fail in finding on.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public String randomDocUniqId(String category) throws IOException {

		int counter = 0;

		if (category == null)
			return randomDocUniqId();

		while (counter < 1000) {
			int i = randomDoc();
			String s = iSearcher.get("default").doc(i).get("category");
			if (s.equals(category))
				return iSearcher.get("default").doc(i).get(Options.get("UNIQ_ID_FIELD"));

			counter++;
		}

		return null;
	}

	/**
	 * Return the unique identifier of a random document in the collection.
	 *
	 * @return A random document identifier that is valid.
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public String randomDocUniqId() throws IOException {

		int docId = randomDoc();
		Document d = null;

		if (docId != -1)
			d = iSearcher.get("default").doc(docId);

		if (d != null)
			return d.get(Options.get("UNIQ_ID_FIELD"));

		return null;
	}

	/**
	 * Returns the document with this unique ID, or null if id doesn't exist.
	 *
	 * @param uniqId the uniq id
	 * @return the doc
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public ScoredDocument getDoc(String uniqId) throws IOException {
		// Find the paper with id = uniqId
		TermQuery paperId = new TermQuery(new Term(Options.get("UNIQ_ID_FIELD"), uniqId));
		ScoreDoc[] hits = iSearcher.get("default").search(paperId, 2).scoreDocs;

		// Return document
		ScoredDocument sd = null;
		if (hits.length == 0)
			log("WeightedLuceneSearcher.getDoc Warning: Document " + uniqId + " not found.");
		else
			sd = new ScoredDocument(iSearcher.get("default").doc(hits[0].doc), hits[0].doc);

		return sd;
	}

	/**
	 * Return the maximum document id present in the index.
	 * 
	 * @return The maximum document id present in the index.
	 */
	public long getIndexSize() {
		return iSearcher.get("default").maxDoc();
	}

	/**
	 * Return a HTML version of the document. This is collection specific, so
	 * you want to change it to match your collection.
	 *
	 * @param sd the sd
	 * @return the string
	 */
	public String toHtml(ScoredDocument sd) {

		Document doc = sd.getDoc();

		if (doc == null) {
			return "Error: Null Document";
		}

		Calendar c = new GregorianCalendar();

		String author = doc.get("authors");
		if (author == null)
			author = "";

		// Filter affiliations out of author strings
		author = author.replaceAll("\\([^\\)]*\\)", "");

		// If the list of authors is too long, break at the third comma
		if (author.length() > 45) {
			int pos = 0;
			for (int i = 0; i < 3 && pos != -1; i++)
				pos = author.indexOf(',', pos + 1);
			if (pos > 0)
				author = author.substring(0, pos) + " et al.";
		}

		String title = doc.get("title");
		if (title == null)
			title = "";

		Date date = sd.getDate();

		// Get rid of things in parenthesis from the author field
		int len = 0;
		while (len != author.length()) {
			len = author.length();
			author = author.replaceAll("\\([^\\)\\(]*\\)", "").trim();
		}

		String st_year = "";
		if (date != null) {
			c.setTime(date);
			int year = c.get(Calendar.YEAR);
			st_year = " (<span class=\"year\">" + year + "</span>)";
		}

		String result = "";
		if (author.length() > 0)
			result += "<span class=\"author\">" + author + "</span>, ";
		if (title.length() > 0)
			result += "<span class=\"title\">" + title + "</span>";
		if (st_year.length() > 0)
			result += st_year;

		return result;
	}

	/* (non-Javadoc)
	 * @see edu.cornell.cs.osmot.searcher.Searcher#getDoc(int)
	 */
	public Document getDoc(int id) throws IOException {
		return iSearcher.get("default").doc(id);
	}
	
	/**
	 * Increment counter and return current number of updates
	 *
	 * @return the number of updates before incrementing
	 */
	private synchronized int incrementCounter() {
		noUpdates++;
		return noUpdates - 1;
	}
	
	/**
	 * Update weights using the perceptron update rule. The updated weight vector will
	 * be clipped at 1.0 and 0.0 to prevent the ranking from getting reversed.
	 *
	 * @param doc the id of the clicked document
	 * @param queryString the query string
	 * @param queryId the query id
	 * @throws ParseException the parse exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public void updateWeights(String doc, String queryString, String queryId) throws ParseException, IOException {
		if (queryId == null)
			return;
		int noUpdatesLocal = incrementCounter();
		Logger.log("Updating weight vector: " + noUpdatesLocal);
		// Get clicks associated with this query id
		if (clicksCache.get(queryId) == null) {
			clicksCache.put(queryId, new HashMap<String, Boolean>());
		}
		if (clicksCache.get(queryId).containsKey(doc.toLowerCase())) {
			// Don't make an update for duplicate clicks
			Logger.log("Finished updating weight vector: " + noUpdatesLocal + " due to DUP");
			return;
		} else {
			clicksCache.get(queryId).put(doc.toLowerCase(), true);
		}

		// Get back perturbation
		if (queryResultsCache.get(queryString) == null) {
			// Don't make any updates if the perturbation is unavailable
			Logger.log("Finished updating weight vector: " + noUpdatesLocal + " due to NO PERT in cache");
			return;
		}
		ArrayList<Integer> perturbation = queryResultsCache.get(queryString);
		ArrayList<Integer> perturbationRev = queryResultsRevMapCache.get(queryString);

		// Get back ranking
		RerankedHits query = search(queryString, true, false);	//Do not perturb the ranking as we want to apply the presented perturbation later
		ScoredDocument[] ranking = query.initialResults();
		ScoredDocument clickedDocument = null;
		int rank = 0;
		String title = "";
		String authors = "";
		for (int i = 0; i < ranking.length; i++) {
			ScoredDocument d = ranking[i];
			// Find clicked document
			if (d.getUniqId().trim().equalsIgnoreCase(doc)) {
				clickedDocument = d;
				try {
					title = d.getField("title");
					authors = d.getField("authors");
				} finally {
					rank = i ;
				}
				break;
			}
		}

		if (ranking.length < 1){
			//Should never come here
			Logger.log("KARTHIK-ERROR: Somehow 0 ranking length has click on it");
			return;		
		}

		int perturbed_rank = perturbation.get( rank );
		int perturbed_cmp = perturbationRev.get( rank );
		int cmp_perturbed_rank = perturbation.get( perturbed_cmp );

		//Get the comparison document
		ScoredDocument orig_doc = ranking[ perturbed_cmp ];
		String orig_doc_id = orig_doc.getUniqId().trim().toLowerCase();

		// Increment the number of updates (includes the cases where no update is made as well)
		noUpdatesLocal++;

		//Check if an update should be made
		String updCase = "normal_";	//Five kinds: 1) self (no comp) 2) upper (no update) 3) lower (no update) 4) normal (for regular update) 5) comp (for compensatory update
		int whichCase = 4;
		if (cmp_perturbed_rank == perturbed_rank) { updCase = "self_"; whichCase = 1; }
		else {
			if (cmp_perturbed_rank > perturbed_rank){
    				if (clicksCache.get(queryId).containsKey(orig_doc_id))  { updCase = "comp_"; whichCase = 5;} 
					else { updCase = "upper_"; whichCase = 2;}
			}
			else if (clicksCache.get(queryId).containsKey(orig_doc_id))  { updCase = "lower_"; whichCase = 3;}
		}

		if ( whichCase < 4 ) {
			//No update neccesary
			//Still log this as a non-update
			if (!fileName.isEmpty()) {
				// Write out clicked document
				String[] comments2 = { "query: " + queryString, "queryId : " + queryId, "click: " + doc, "rank: " + (rank +1),
					"perturbed_rank : " + (perturbed_rank + 1), "cmp : " + (perturbed_cmp + 1), "title: " + title
					, "authors: " + authors };
				WeightVector.writeToFile(clickedDocument.getFeatureVector(), Options.get("LOG_DIRECTORY") + "/noupd_" + updCase
					+ noUpdatesLocal + ".txt", comments2);
			}
			Logger.log("Finished updating weight vector: " + noUpdatesLocal + " due to No UPD required");
			return;
		}
		
		// Compute difference vector: phi(feedback) - phi(original)

		// Get phi(feedback)
		SparseVector phiVector = new SparseVector(clickedDocument.getFeatureVector());

		// Subtract phi(original)
		phiVector.add(-1, orig_doc.getFeatureVector());

		//Scale by the discount factor
		double discount =   ( Math.log(2) / Math.log(cmp_perturbed_rank + 2) )   -  ( Math.log(2) / Math.log(perturbed_rank + 2) ) ;
		if (whichCase == 5) discount = -discount;		//Make it +ve
		phiVector.scale(discount);
		if (debug)
			log("Update discounted by factor of : "+ discount);

		//Update the Running Sum
		runningSum = runningSum + (  discount * (  clickedDocument.getScore() - orig_doc.getScore()  )  );

		if (!fileName.isEmpty()) { //Write previous weight out to a file
			WeightVector.writeToFile(weightVector, Options.get("LOG_DIRECTORY") + "/weights_" + (noUpdatesLocal - 1) + ".txt",
					noUpdatesLocal - 1);
			writeSumToFile(   Options.get("LOG_DIRECTORY") + "/runningsum_" + noUpdatesLocal + ".txt" );
		}
		
		// Add difference vector to current weight vector
		weightVector.add(phiVector);

		// Clip weights for raw scores  at 1.0
		for (Integer i : weightsClipAt1) {
			if (weightVector.get(i) < 1.0d) {
				weightVector.set(i, 1.0d);
			}
		}
		// Clip weights for binary features at 0.0
		for (Integer i : weightsClipAt0) {
			if (weightVector.get(i) < 0.0d) {
				weightVector.set(i, 0.0d);
			}
		}

		if (!fileName.isEmpty()) {
			WeightVector.writeToFile(weightVector, fileName, noUpdatesLocal);

			// Write out phi
			String[] comments = { "query: " + queryString, "queryId : " + queryId, "click: " + doc, "rank-upper: " + (1 + cmp_perturbed_rank)
					, "rank-lower: " + (1 + perturbed_rank), "title: " + title, "authors: " + authors };
			WeightVector.writeToFile(phiVector, Options.get("LOG_DIRECTORY") + "/debug_" + updCase + noUpdatesLocal + ".txt", comments);

			// Write out clicked document
			String[] comments2 = { "query: " + queryString, "queryId : " + queryId, "click: " + doc, "orig-rank: " + (rank + 1),
					 "perturbed_cmp : " + (perturbed_cmp + 1),"title: " + title, "authors: " + authors };
			WeightVector.writeToFile(clickedDocument.getFeatureVector(), Options.get("LOG_DIRECTORY") + "/click_" + updCase
					+ noUpdatesLocal + ".txt", comments2);

			// Write out document in original ranking
			String[] comments3 = { "query: " + queryString, "queryId : " + queryId, "title: " + orig_doc.getField("title"),
					"authors: " + orig_doc.getField("authors") };
			WeightVector.writeToFile(orig_doc.getFeatureVector(), Options.get("LOG_DIRECTORY") + "/pair_" + updCase
					+ noUpdatesLocal + ".txt", comments3);

		}

		if (!fileName.isEmpty() && (noUpdatesLocal % Options.getInt("SEARCHER_STORE_RESULTS")) == 0) {
			try {
				EvalInterleaving.writeToFile(Options.get("OSMOT_ROOT") + "/results.txt", noUpdatesLocal);
			} catch (SQLException e) {
				Logger.log("Error while outputting interleaving results!");
			}
		}
		Logger.log("Finished updating weight vector: " + noUpdatesLocal );
	}
}
