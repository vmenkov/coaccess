package edu.cornell.cs.osmot.features;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A parser class, which reads in a plain text file line by line and splits it 
 * into tokens. It will return a list of tokens for each line. 
 * 
 * @author Tobias Schnabel
 * @version 1.0, May 2012
 */
public class Parser {
	
	/** The lines. */
	private ArrayList<String> lines = new ArrayList<String>();
	
	/** The parsed lines. */
	private ArrayList<ArrayList<String>> parsedLines = new ArrayList<ArrayList<String>>();
	
	/** The file name. */
	private String fileName;
	
	/**
	 * Instantiates a new parser.
	 *
	 * @param fileName file to parse
	 */
	public Parser(String fileName) {
		this.fileName = fileName;
	}
    
	/**
	 * Parses the file.
	 *
	 * @param delimiter the delimiter string
	 */
	public void parse(String delimiter) {
    	readFile();
    	parseLines(delimiter);
    }
	
	/**
	 * Parses the file. Uses whitespace as delimiter.
	 */
	public void parse() {
		parse("\\s+");
	}
	
    /**
     * Gets the parsed lines.
     *
     * @return the parsed lines
     */
    public ArrayList<ArrayList<String>> getParsedLines() {
		return parsedLines;
	}

	/**
	 * Read in the file line by line.
	 */
	private void readFile() {
		try {
			lines.clear(); 
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			
			while ((fileName = br.readLine()) != null) {
				// Skip empty lines and skip comments
				if (!fileName.trim().isEmpty() && !fileName.trim().startsWith("#")) {
					lines.add(fileName);
				}
			}
			br.close();
		} catch (Exception e) {
			System.out.println("Couldn't open input file!");
		}

	}

	/**
	 * Parses the lines. Splits each line into tokens separated by the delimiter.
	 *
	 * @param delimiter the delimiter string
	 */
	private void parseLines(String delimiter) {
		ArrayList<String> parsedLine;

		for (String line : lines) {
			// tokenize using whitespaces
			parsedLine = new ArrayList<String>(Arrays.asList(line.split(delimiter)));
			parsedLines.add(parsedLine);
		}
	}

	/**
	 * Display the tokenized file for debugging.
	 */
	public void displayTokenizedFile() {
		for (int x = 0; x < this.parsedLines.size(); x++) {
			for (int y = 0; y < this.parsedLines.get(x).size(); y++) {
				System.out.print(parsedLines.get(x).get(y));
				if (y != parsedLines.get(x).size() - 1) {
					System.out.print("|");
				}
			}
			System.out.println("");
		}
	}

}