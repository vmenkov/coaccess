package edu.cornell.cs.osmot.options;

import java.io.*;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import edu.cornell.cs.osmot.logger.Logger;
import edu.cornell.cs.osmot.searcher.Searcher;

/**
 * This class collects all the high level options of the search engine in one
 * place. The values are all ready from a config file.
 * 
 * @author Filip Radlinski, Tobias Schnabel
 * @version 1.2, May 2012
 */
public class Options {

	private static HashMap<String, Object> h;
	private static Date lastUpdate;

	// Options that must not be blank
	private static String requiredOptions[] = { "OSMOT_ROOT", "OPTIONS_LIFETIME",
			"INDEX_DIRECTORY", "UNIQ_ID_FIELD",
			"SEARCHER_NUM_FIELD_RESULTS", "SEARCHER_LIFETIME", "SEARCHER_URL", 
			"SEARCHER_BASE_URL", "SNIPPETER_FIELDS", "SNIPPETER_SNIPPET_LENGTH", 
			"SNIPPETER_CONTEXT_LENGTH", "SNIPPETER_MAX_DOC_LENGTH", 
			"SNIPPETER_PLAIN_TEXT", 
			"CACHE_DIRECTORY", "CACHE_DEFAULT_LENGTH", "INDEXER_MAX_LENGTH", "DEBUG",
			"LOG_DB", "LOG_USER", "LOG_PWD", "LOG_PREFIX"};

	// Options that must be set, but can also be blank
	private static String requiredBlankableOptions[] = { 
			"SEARCHER_BASE_VISIBLE_URL", "WEIGHTS_CLIP_AT_0", "WEIGHTS_CLIP_AT_1"};
	
	static {
		lastUpdate = new Date();
		loadOptions(false);		
	}
	
	public static String get(String name) {

		if (new Date().getTime() - lastUpdate.getTime() > 
				1000*60*Integer.parseInt((String)h.get("OPTIONS_LIFETIME"))) {
			loadOptions(true);
			lastUpdate = new Date();
		}
		
		if (h.get(name) == null) {
			System.err.println("Error: Option " + name + " is not set.");
			Logger.log("Error: Option " + name + " is not set.");
			// Funny hack to get a stack trace in a string so we can send it to the log file
			CharArrayWriter c = new CharArrayWriter();
			new Exception("StackTrace").printStackTrace(new PrintWriter(c));
			Logger.log(c.toString());
		}
		return (String) h.get(name);
	}

	public static int getInt(String name) {
		String s = get(name);
		return Integer.parseInt(s);
	}

	public static String[] getStrArr(String name) {
		String s = get(name);
		return s.split(",");
	}

	public static double getDouble(String name) {
		String s = get(name);
		return Double.parseDouble(s);
	}
	
	public static boolean getBool(String name) {
		String s = get(name);
		if (s.equalsIgnoreCase("true"))
			return true;
		return false;
	}
	
	/**
	 * Parses a range into an int[] array. This can
	 * be used to select a subset of features.
	 * The format allows the specification of a number of subranges,
	 * for example 0-17,38,42,50-52,70-end
	 *
	 * @param name the name
	 * @param maxIndex the last index
	 * @return an int array list holding all indices
	 */
	public static ArrayList<Integer> getRange(String name, int maxIndex) {
		ArrayList<Integer> indices = new ArrayList<Integer>();
		
		String s = get(name);
		if (!s.isEmpty()) {
			String[] subRanges = s.split(",");
			for (String subRange : subRanges) {
				int startIndex, endIndex;
				if (subRange.contains("-")) {
					startIndex = Integer.parseInt(subRange.split("-")[0]);
					String endPos = subRange.split("-")[1];
					if (endPos.equalsIgnoreCase("end")) {
						endIndex = maxIndex;
					} else {
						endIndex = Integer.parseInt(endPos);
					}
				} else {
					startIndex = Integer.parseInt(subRange);
					endIndex = startIndex;
				}
				for (int i = startIndex; i <= endIndex; i++) {
					indices.add(i);
				}
			}
		} 
		return indices;			
	}
	
	/**
	 * Load the options from a config file. If reload is true and an error occurs,
	 * the old options will not get replaced.
	 *
	 * @param reload if option file gets reloaded
	 */
	public static synchronized void loadOptions(boolean reload) {
		
		// Initialize the hash table
		HashMap<String, Object> hNew = new HashMap<String, Object>();
		
		for (int i = 0; i < requiredOptions.length; i++)
			hNew.put(requiredOptions[i], "");

		// Check if there is a global variable, that contains the path to the
		// options.
		String configPath = getWebDir() + "/config";
		if (!new File(configPath).exists()) {
			configPath = getWebDir() + "/trunk";
		}
		boolean error = false;

		// Open the options file.
		try {
			if (reload)
				System.out.println("Osmot reloading config file from "+configPath+"/osmot.conf");
			else
				System.out.println("Osmot loading config file from "+configPath+"/osmot.conf");
			System.out.println("Current working directory is "+System.getProperty("/user.dir"));
			BufferedReader in = new BufferedReader(new FileReader(configPath+"/osmot.conf"));
			
			while (in.ready()) {
				String line = in.readLine().trim();
				if (line.length() == 0)
					continue;
				if (line.charAt(0) == '#')
					continue;
				String parts[] = line.split("=",2);
				if (parts.length > 1)
					hNew.put(parts[0].trim(), parts[1].trim());
				else
					hNew.put(parts[0], "");
			}
			in.close();
		} catch (IOException e) {
			error = true;
			System.err.println("Error reading from file " + configPath + "/osmot.conf");
			System.err.println(e);
		}

		// Set paths
		hNew.put("OSMOT_ROOT", getWebDir());
		hNew.put("LOG_DIRECTORY", getWebDir() + "/logs");
		hNew.put("CONFIG_DIRECTORY", configPath);
		// Check we don't have any values that aren't set correctly.
		for (int i=0; i<requiredOptions.length; i++) {
			String key = requiredOptions[i];
			String value = (String) hNew.get(key);
			if (value == null || value.equals("")) {
				System.err.println("Error: Option " + key + " is not set.");
				error = true;
			}
		}

		for (int i=0; i<requiredBlankableOptions.length; i++) {
			String key = requiredOptions[i];
			String value = (String) hNew.get(key);
			if (value == null) {
				System.err.println("Error: Option " + key + " is not set (set it to blank if you want it blank).");
				error = true;
			}
		}
		
		// Check that we have all mode probabilities 
		for (int i=0; i<Searcher.modes.length; i++) {
			String key = "SEARCHER_MODE_"+Searcher.modes[i];
			String value = (String) hNew.get(key);
			if (value == null) {
				System.err.println("Error: Option "+ key +" is not set. It must be a positive number.");
				error = true;
			}
		}
 		
		// All errors in loading the options are fatal. Reloading can fail
		// without being fatal.
		if (error) {
			if (reload) {
				System.err.println("RELOAD OF OSMOT OPTIONS ABORTED!");
				return;
			} else {
				System.err.println("OSMOT EXITING!");
			}
		}

		if (!error) {
			h = hNew;
		}
	}
	
	
	/**
	 * Loads the current web directory by looking up the path of the current *.jar file.
	 *
	 * @return the path to the main directory
	 */
	static private String getWebDir() {
		String path = Options.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String decodedPath;
		try {
			 decodedPath = URLDecoder.decode(path, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			 decodedPath = path;
		}
		path = (new File(decodedPath)).getParent() + "/../..";
		return path;
	}
}
