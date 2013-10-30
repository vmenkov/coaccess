package edu.cornell.cs.osmot.searcher;

import org.apache.lucene.document.Document;

import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.logger.Logger;

import java.util.Comparator;
import java.util.Arrays;
import java.util.Set;

/**
 * Compare two locations so that we can sort by the start location
 */
class StartComparer implements Comparator<int[]> {
	public int compare(int[] obj1, int[] obj2) {
		int start1 = obj1[0];
		int start2 = obj2[0];

		// -1 should always be at the end of the sorted list
		if (start1 == -1 && start2 == -1)
			return 0;
		else if (start1 == -1)
			return 1;
		else if (start2 == -1)
			return -1;
		
		return start1-start2;
	}
}

/**
 * This class is used to generate the snippets (also know as abstracts) that we
 * present users in search engine results. They are selected so as to try to
 * show words that are in the query query. The words from the query are marked
 * in bold. If we don't find a good snippet, we fall back on presenting the
 * start of the abstract. The snippeter only uses some of the document fields in
 * finding snippets, and has a preference order over them. Therefore it is
 * collection dependent, and you must set the fields to use.
 * 
 * @author Filip Radlinski
 * @version 1.0, April 2005
 */
public class Snippeter {
	
	private static boolean debug = false;
	
	private static String cSnippeterFields[];

	private static int cSnippetLength;
	private static int cLongSnippetLength;
	
	private static int cMaxDocLength;

	private static int cContextLength;
	private static int cLongContextLength;
	
	private static Cache cache = null;
	
	private static String cBuffer = " ... ";
	
	static {
		debug = Options.getBool("DEBUG");
		cSnippeterFields = Options.getStrArr("SNIPPETER_FIELDS");
		cSnippetLength = Options.getInt("SNIPPETER_SNIPPET_LENGTH");
		cLongSnippetLength = Options.getInt("SNIPPETER_LONG_SNIPPET_LENGTH");
		cMaxDocLength = Options.getInt("SNIPPETER_MAX_DOC_LENGTH");
		cContextLength = Options.getInt("SNIPPETER_CONTEXT_LENGTH");
		cLongContextLength = Options.getInt("SNIPPETER_LONG_CONTEXT_LENGTH");
		cache = new Cache(Options.get("CACHE_DIRECTORY"));
	}

	/** 
	 * Extract the segments in segments from the text. Assumes the  segments are
	 * sorted and do not overlap (i.e. merge was called first)
	 * 
	 * @param text      The original text we are extracting segments from
	 * @param segments  The start and end positions of the segments
	 * @param maxLen    The maximum length of the text (we stop when we finish that many segments)
	 * @return          The extract chosen
	 */
	private static String extract(String text, int [][] segments, int snippetLength, int contextLength, boolean isLong) {

		String result = "";
		int start, end;
		int charsLeft = snippetLength;

		// Make sure < doesn't mess things up: we're dealing with plain text. 
		if (Options.getBool("SNIPPETER_PLAIN_TEXT"))
			text = text.replaceAll("<","&lt;");
		
		// If we only found one short segment, double the context length
		if (segments[0][0] != -1 && segments[1][0] == -1 && segments[0][1] - segments[0][0] < snippetLength * 0.75) {
			debug("Single segment was "+segments[0][0]+" - "+segments[0][1]);
			segments[0][0] = Math.max(0, segments[0][0] - contextLength);
			segments[0][1] = Math.min(text.length() - 1, segments[0][1] + contextLength);
			debug("Single segment became "+segments[0][0]+" - "+segments[0][1]);
		} 
		
		for(int i=0; i<segments.length; i++) {
			
			if (result.length() > snippetLength)
				break;
			if (segments[i][0] < 0)
				break;
			
			start = segments[i][0];
			end = segments[i][1];
			
			// Find the first space after the start
			if (start <= 0) {
				start = 0;
			} else {
				int tmp = text.indexOf(" ",start);
				if (tmp > start)
					start = tmp;
			}
			
			// Make sure the total text is not longer than the maximum
			end = Math.min(end, snippetLength - (result.length() - start));
			
			// Find the last space before the end
			if (end >= text.length()) {
				end = text.length();
			} else {
				int tmp = text.lastIndexOf(" ",(Math.min(end,charsLeft)));
				if (tmp < end && tmp > start)
					end = tmp;
			}

			debug("Want to add "+start+"-"+end);
			
			// Only do the add if the word is actually in the new start-end segment
			// (i.e. start to end is at least the size of the context length)
			if (end - start < contextLength || end < 0 || start < 0 || end<start)
				break;
			
			charsLeft -= (end-start);
			
			// Now get the substring
			if (start > 0 && result.equals("")) {
				result = cBuffer;
				charsLeft -= cBuffer.length();
			} /* else if (start > 0 && isLong) {
				result += cBuffer;
			} */
			
			result += text.substring(start, end);
			
			if (end < text.length()) {
				result += cBuffer;
				charsLeft -= cBuffer.length();
			}
		}
		
		return result;
	}
	
        /**
         * Get snippet from the abstract. Terse output
         *
         * @param queryString
         *            The query as a string
         * @param d
         *            The document we want a snippet for
         */
        public static String getAbstractSnippet(Document d, String queryString, boolean isLong) {

                String snippet = "<i>" + cSnippeterFields[0] + ":</i> " + getFirstFieldStart(d, isLong);
                return snippet;
        }

	/**
	 * Get snippet from the fields specified including the text. Motivated by
	 * code from Nutch summarizer.
	 * 
	 * @param queryString
	 *            The query as a string
	 * @param d
	 *            The document we want a snippet for
	 */
	public static String getSnippet(Document d, String queryString, boolean isLong, boolean isPaper) {

		String snippet = "";
		String prefix = "";
		int realSize = cSnippetLength;

		if(isLong) {
			realSize = cLongSnippetLength;
		}

		if(isPaper) {
			prefix = "<b>Top matches in this document to your query '"+queryString+ "'</b><br />\n" ;
			realSize *= 6;
		}

		// If we want a long snippet, go straight to the complete cache if its present.
		if (cache != null && isLong) {
			String docString = cache.getContents(d.get(Options.get("UNIQ_ID_FIELD")));
			if (docString != null && docString.length() > 0)
				snippet += getSnippet(docString, queryString, isLong);
		}

		for (int i = 0; (i < cSnippeterFields.length)
				&& (snippet.length() < realSize); i++) {

			String newsnippet = getSnippet(d.get(cSnippeterFields[i]), queryString, isLong);

			if (!newsnippet.equals("")) {
				debug("Adding to snippet: "+newsnippet);

				// Prepend the field.
				newsnippet = "<i>" + cSnippeterFields[i] + "</i>: " + newsnippet;

				if (snippet.equals("")) {
					snippet = newsnippet;
				} else {
					snippet = snippet + " &nbsp; " + newsnippet;
				}
			}
		}

		// Try to get the document from the cache, if we haven't done so yet and have no snippet so far.
		if (cache != null && snippet.equals("")) {
			String docString = cache.getContents(d.get(Options.get("UNIQ_ID_FIELD")));
			if (docString != null && docString.length() > 0)
				snippet = getSnippet(docString, queryString, isLong);
		}
		
		// Make sure the snippet isn't empty.
		if (snippet.equals("")) {
			if (isPaper)
				prefix = "<i>The search terms were not found in this document. We may have returned it "+
				  "because other users seemed to have liked this document.</i>";
			else 
				snippet = "<i>" + cSnippeterFields[0] + ":</i> " + getFirstFieldStart(d, isLong);
		}
		
		// Make the query terms be bold
		//if (snippet != "")
	//		snippet = boldify(snippet, queryString);
				
		return prefix + snippet;
	}

	/** 
	 * Merge segments so that we don't print the same sub-section multiple times.
	 * 
	 * @param segments  The locations of the segments we want to print
	 */
	private static int merge(int [][] segments) {

		if (segments.length == 0)
			return 0;
		
		int [][] newSegments = new int[segments.length][];

		for(int i=0; i<segments.length; i++) {
			newSegments[i] = new int[2];
			newSegments[i][0] = -1;
			newSegments[i][1] = -1;
		}
			
		// Sort the segments by the start location
		Arrays.sort(segments, new StartComparer());
		
		if (debug) {
			for (int i=0; i<segments.length && segments[i][1] != -1; i++)
				debug("merge: After sort, segment "+i+" is "+segments[i][0]+" - "+segments[i][1]);
		}
		
		int numSegments = 1;		
		newSegments[0] = new int[2];
		newSegments[0][0] = segments[0][0];
		newSegments[0][1] = segments[0][1];
		
		for (int i=1; i<segments.length; i++) {
			// Stop merging when we get to the unused segments
			if (segments[i][0] == -1)
				break;
			// If the current segment ends before this one starts, then make a new one
			if (newSegments[numSegments-1][1] + cBuffer.length() < segments[i][0]) {
				newSegments[numSegments][0] = segments[i][0];
				newSegments[numSegments][1] = segments[i][1];
				numSegments++;
			} else {
				newSegments[numSegments-1][0] = Math.min(segments[i][0],newSegments[numSegments-1][0]);
				newSegments[numSegments-1][1] = Math.max(segments[i][1],newSegments[numSegments-1][1]);
			}
		}

		int length = 0;
		
		for(int i=0; i<segments.length; i++) {
			segments[i][0] = newSegments[i][0];
			segments[i][1] = newSegments[i][1];	
			if (segments[i][0] >= 0)
				length += segments[i][1] - segments[i][0];
		}

		if (debug) {
			for (int i=0; i<segments.length && segments[i][1] != -1; i++)
				debug("merge: After merge, segment "+i+" is "+segments[i][0]+" - "+segments[i][1]);
		}
				
		debug("Segment lengths add up to "+length);
		
		return length;
	}
	
	/**
	 * If we don't find a good snippet, we return the start of the first field.
	 * 
	 * @param d
	 *            The document we want a snippet for.
	 * @return The start of the first field (in the options in this source
	 *         code).
	 */
	private static String getFirstFieldStart(Document d, boolean isLong) {

		String snippet = d.get(cSnippeterFields[0]);

		// In case the field doesn't exist
		if (snippet == null)
			return "";

		// Determine how long it needs to be
		int effectiveLength = cSnippetLength;
		if(isLong)
			effectiveLength = cLongSnippetLength;

		if (snippet.length() > effectiveLength) {
			snippet = snippet.substring(0, effectiveLength);
			int where = snippet.lastIndexOf(" ");
			if (where != -1)
				snippet = snippet.substring(0, where);
			snippet = snippet + cBuffer;
		}

		return snippet;
	}

	/**
	 * Find the occurence of any query string in the document string. We get
	 * ContextLength of text on either side of any match, increasing the size
	 * more if by getting the context we find additional matching text.
	 * 
	 * @param queryString
	 *            The string of the query (needle).
	 * @param docString
	 *            The string we are searching in (haystack).
	 */
	private static String getSnippet(String docString, String queryString, boolean isLong) {
		if (docString == null || queryString == null || docString.length() == 0) {
			return "";
		}
		queryString = queryString.replace("*", "");

		if (docString.length() > cMaxDocLength)
			docString = docString.substring(0, cMaxDocLength);

		/* Get rid of any field names in the query (i.e. category:, title:, etc) */
		queryString = queryString.replaceAll("(^|\\s)[A-Za-z]+:([A-Za-z])",
				"$1$2");

		String tokens[] = tokenize(queryString);

		int [][] segments = new int[40][];

		// Initialize the segments
		for(int i=0; i<40; i++) {
			segments[i] = new int[2];
			segments[i][0] = -1;
			segments[i][1] = -1;
		}
			
		int curSegment = 0;
		int curPos = 0, newPos = 0;
		boolean change = true;
		
		int snippetLength, contextLength;
		if (isLong) {
			snippetLength = cLongSnippetLength;
			contextLength = cLongContextLength;
		} else {
			snippetLength = cSnippetLength;
			contextLength = cContextLength;
		}
		
		debug("Snippet length is "+snippetLength+" with context length "+contextLength);
		
		while (change && merge(segments) < snippetLength && curSegment < 40) {
			
			change = false;
			
			// Only update curPos once for every run through all the tokens
			// so that each token is search for in the whole snippet
			curPos = newPos;
			
			// Find what the current segment should be (merge messes things up)
			curSegment = 40;
			for(int i=0; i<segments.length; i++)
				if (segments[i][0] == -1) {
					curSegment = i;
					break;
				}
				
			for (int i = 0; i < tokens.length && curSegment < 40; i++) {
				segments[curSegment] = wordIndexOf(docString, tokens[i], curPos);
				if (segments[curSegment][0] != -1) {
					newPos = Math.max(newPos, segments[curSegment][1]);

					debug(curSegment+": Found " + tokens[i] + " at " + segments[curSegment][0] + "-" + segments[curSegment][1]);
					segments[curSegment][0] = Math.max(0, segments[curSegment][0]-contextLength);
					segments[curSegment][1] += contextLength;
					curSegment++;
					change = true;
				}
			}			
		}
		
		return extract(docString, segments, snippetLength, contextLength, isLong);
	}

	/**
	 * Boldifies the words in the query in the given string.
	 * 
	 * @param s
	 *            The string in which we want to boldify words.
	 * @param query
	 *            The words we want to make bold.
	 * @return The string s with words from query in bold.
	 */
	public static String boldify(String s, String query) {

		String result = s;
		query = query.replace("*", "");
		//debug("Called boldify with string "+s);
				
		String[] tokens = tokenize(query, false);

		for (int i = 0; i < tokens.length; i++) {

			int start = 0;
			int loc[];
			do {
				loc = commonFind(result.substring(start), tokens[i]);
				//debug("Loc is " + loc[0] + " - " + loc[1]);
				if (loc[0] != -1) {
					result = result.substring(0, start + loc[0]) + "<b>"
							+ result.substring(start + loc[0], start + loc[1])
							+ "</b>" + result.substring(start + loc[1]);
				}
				start += loc[1] + 7;
			} while (loc[0] != -1);
		}

		/*
		 * This old approach dies when we get a complex query that the pattern
		 * matcher has trouble compiling.
		 */
		/*
		 * // Tokenize query String[] tokens = tokenize(query);
		 * 
		 * for(int i=0; i <tokens.length; i++) { //String p =
		 * "(?!\\W)("+tokens[i]+")(?=\\W)"; String p =
		 * "(?!^|\\W|_)("+tokens[i]+")(?=\\W|_|$)"; result = Pattern.compile(p,
		 * Pattern.CASE_INSENSITIVE).matcher(result).replaceAll(" <b>$1 </b>"); }
		 */
		return result;
	}

	/**
	 * Return the position of the word <needle> in <haystack> such that the
	 * needle starts and ends on a word boundary, or with an "s" after it.
	 * 
	 * @param haystack
	 *            The string in which we want to search.
	 * @param needle
	 *            The string we want to find in the haystack
	 * @param startPos
	 *            The position at which we start looking.
	 * @return The start and end positions of the first occurrence of needle
	 *         after startPos.
	 */
	private static int[] wordIndexOf(String haystack, String needle,
			int startPos) {

		int result[] = { -1, -1 };

		if (haystack.length() == 0)
			return result;
		if (startPos < 0)
			startPos = 0;
		if (startPos > haystack.length())
			return result;

		// Chop off the stuff thats too early
		haystack = haystack.substring(startPos);

		result = commonFind(haystack, needle);
		if (result[0] != -1) {
			result[0] += startPos;
			result[1] += startPos;
		}

		return result;
	}
	
	/**
	 * Returns true if the the character at position p in the string s is a
	 * non-word character and thus a break between words.
	 * 
	 * @param s
	 *            The string we are looking in.
	 * @param p
	 *            The position we are looking at.
	 * @return true if s[p] is a non-word character, false otherwise.
	 */
	private static boolean nonWord(String s, int p) {

		if (p >= s.length() || p < 0)
			return true;

		if (s.charAt(p) >= 'a' && s.charAt(p) <= 'z')
			return false;
		if (s.charAt(p) >= '0' && s.charAt(p) <= '9')
			return false;
		if (s.charAt(p) >= 'A' && s.charAt(p) <= 'Z')
			return false;

		return true;
	}

	/**
	 * Internal function to actually do the finding of the needle in the
	 * haystack. The needle may start or end with a "*" which indicates we are
	 * to ignore word boundaries on that end of the needle. Otherwise the needle
	 * must start and end at word boundaries.
	 * 
	 * @param haystack
	 *            The string we are searching in.
	 * @param needle
	 *            The string we are searching for.
	 * @return The start and end positions of the needle in the haystack.
	 */
	private static int[] commonFind(String haystack, String needle) {

		int result[] = { -1, -1 };
		int start = -1, end = -1;
		boolean startOk = false;
		boolean endOk = false;

		if (needle.startsWith("*")) {
			if (needle.endsWith("*")) {
				// * on both ends
				needle = needle.substring(1, needle.length() - 1);
				startOk = true;
				endOk = true;
			} else {
				// Starts with *
				needle = needle.substring(1, needle.length());
				startOk = true;
			}
		} else {
			if (needle.endsWith("*")) {
				// Ends with *
				needle = needle.substring(0, needle.length() - 1);
				endOk = true;
			}
		}

		haystack = haystack.toLowerCase();
		needle = needle.toLowerCase();

		int len = needle.length();
		// Needle is gone - nothing to find
		if (len == 0) {
			return result;
		}

		// Look for the needle and ignore before it.
		for (start = 0; start <= haystack.length() - len; start++) {
			if (haystack.charAt(start) == needle.charAt(0)) {
				if (haystack.substring(start, start + len).equals(needle)
						&& (startOk == true || nonWord(haystack, start - 1))
						&& (endOk == true || nonWord(haystack, start + len) ||
							(haystack.charAt(start+len) == 's' && nonWord(haystack, start + len + 1)))) {
					end = start + len - 1;
					break;
				}
			}
		}

		// Nothing found.
		if (start > haystack.length() - len)
			return result;

		// If we have *s, expand the start or end to the edge of the word
		if (startOk == true) {
			while (nonWord(haystack, start - 1) == false)
				start--;
		}
		if (endOk == true) {
			while (nonWord(haystack, end + 1) == false)
				end++;
		}

		result[0] = start;
		result[1] = end + 1;

		return result;

		/*
		 * For strange needles, this can still run out of memory since we take
		 * the query and insert it here pretty much directly. So we don't do it.
		 * 
		 * String pattern = "(^|\\W|_)("+needle+")(\\W|_|$)"; Pattern p =
		 * Pattern.compile(pattern, Pattern.CASE_INSENSITIVE |
		 * Pattern.MULTILINE); Matcher m = p.matcher(haystack);
		 * 
		 * if (m.find()) { //debug("Pattern "+pattern+" found at "+m.start(2)+"
		 * in "+shorter(haystack)); result[0] = m.start(2); result[1] =
		 * m.end(2); }
		 */
	}

	/* Tokenize the query and remove stopwords. */
	private static String[] tokenize(String q) {
		return tokenize(q, true);
	}

	/*
	 * Tokenize the query and remove stopwords if asked to. Preserves quoted
	 * text as individual tokens. @param q The query to tokenize. @param
	 * removeStopwords Set true if you want to remove stopwords. @return An
	 * array of tokens in the query.
	 */
	private static String[] tokenize(String q, boolean removeStopwords) {

		q = q.trim();
		int i, j;
		String s;

		// Split on quotes
		String segments[] = q.split("[\"\']");

		// Strings in even-numbered segments (base 0) aren't quoted, so split
		// those on spaces
		String subsegments[][] = new String[segments.length][];
		int count = 0;
		for (i = 0; i < segments.length; i++) {
			if (i % 2 == 1) {
				subsegments[i] = new String[1];
				subsegments[i][0] = segments[i];
				//subsegments[i][0] = subsegments[i][0].replaceAll("
				// +","[\\\\W_]+");
				count++;
			} else {
				subsegments[i] = segments[i].split("[^a-zA-Z0-9\\*]+");
				count += subsegments[i].length;
			}
		}

		// Trim everything
		String result[] = new String[count];
		count = 0;
		for (i = 0; i < subsegments.length; i++) {
			for (j = 0; j < subsegments[i].length; j++) {
				subsegments[i][j] = subsegments[i][j].trim();
				if (subsegments[i][j].length() > 0
						&& (removeStopwords == false || notStopword(subsegments[i][j]))) {
					s = subsegments[i][j];
					//s = s.replaceAll("\\*","\\\\w*");
					result[count++] = s;
				}
			}
		}

		String short_result[] = new String[count];
		for (i = 0; i < count; i++) {
			short_result[i] = result[i];
		}

		return short_result;
	}

	/*
	 * Returns true of the string s is not a stopword. Uses a Lucene table of
	 * stopwords. 
	 * 	@param s The word to test. 
	 * 	@return true of s is not a stopword.
	 */
	private static boolean notStopword(String s) {
		Set<?> sw = org.apache.lucene.analysis.standard.StandardAnalyzer.STOP_WORDS_SET;
		
		s = s.toLowerCase();

		return !sw.contains(s);
	}

	/**
	 * Trim the string s, returning only the first 80 characters followed by
	 * "..." if s is too long.
	 * 
	 * @param s
	 *            The string to shorten.
	 * @return s such that it is at most 80 characters long.
	 */
	public static String shorter(String s) {

		String result;

		result = s;
		if (result.length() > 80) {
			result = result.substring(0, 75) + cBuffer;
		}

		return result;
	}

	/** Used for debugging. */
	private static void debug(String s) {
		if (debug)
			Logger.log(s);
	}

}
