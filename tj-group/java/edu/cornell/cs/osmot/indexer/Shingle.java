package edu.cornell.cs.osmot.indexer;

/**
 * A helper class which provides a method to split text into n equal-sized chunks.
 * Used during indexing to create 5 chunks for the document body which can be 
 * searched on separately.
 */
public class Shingle {

	/**
	 * Split a string into n equal-sized chunks. 
	 *
	 * @param text the string 
	 * @param n the number of chunks
	 * @return array that contains the n chunks
	 */
	public static String[] split(String text, int n) {
		if (text == null) text = "";
		String[] shingles = new String[n];
		String[] words = text.split("\\s+");
		for (int i = 0; i < n; i++) {
			int start = (i*words.length)/n;
			int end = ((i+1)*words.length)/n;
			shingles[i]= implode(words, start, end);
		}
		return shingles;
	}

	/**
	 * Implodes an array from index position start to end (exclusive), 
	 * using whitespace while concatenating.
	 *
	 * @param words the words
	 * @param start the start index in the array
	 * @param end the end index
	 * @return the imploded array
	 */
	private static String implode(String[] words, int start, int end) {
		StringBuilder sb = new StringBuilder();

		for (int i = start; i < Math.min(words.length, end); i++) {
			if (sb.length() == 0)
				sb.append(words[i]);
			else
				sb.append(" " + words[i]);
		}
		return sb.toString();
	}

	/**
	 * The main method, only used for testing.
	 *
	 * @param args not used
	 */
	public static void main(String[] args) {
		int n = 0;
		String test = "test0";
		for (int i = 1; i < n; i++)
			test += " test" + i; 
		String[] res = Shingle.split(test, 18);
		for (String s : res) {
			System.out.println(s.split("\\s+").length);
			System.out.println(s);
		}
	}
}

