package edu.cornell.cs.osmot.indexer;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

import java.util.HashMap;
import java.util.Map;

import java.util.Date;
import java.util.GregorianCalendar;
import java.text.SimpleDateFormat;
import java.util.regex.*;
import java.io.*;

/**
 * This class implements adding documents to the index and updating them. 
 * It parses a the raw text files containing the abstract and body of a document 
 * and creates a new instance of the Lucene document class, which will then be added
 * to the index.
 * 
 * @author Filip Radlinski, Tobias Schnabel
 * @version 1.1, May 2012
 */

@SuppressWarnings("deprecation")
public class Indexer {

	/** This should be in j.u.GregorianCalendar... Its used to fetch 
	 * the current year later. */
	private static int YEAR = 1;

	// 
	/** Where our index lives. */
	private String indexDirectory;

	/**
	 * Instantiates a new indexer.
	 *
	 * @param indexDir the index directory
	 * @param cacheDir the cache directory
	 */
	public Indexer(String indexDir, String cacheDir) {
		indexDirectory = indexDir;
		log("Created an indexer.");
	}

	/** The create a date format class for parsing the dates. */
	private static SimpleDateFormat df[] = { new SimpleDateFormat("dd MMM yyyy HH:mm:ss"),
			new SimpleDateFormat("dd MMM yyyy HH:mm"), new SimpleDateFormat("dd MMM yyyy"),
			new SimpleDateFormat("dd MMM yy HH:mm"), new SimpleDateFormat("d MMM yy HH:mm"),
			new SimpleDateFormat("MMM dd yyyy HH:mm:ss"), new SimpleDateFormat("MMM dd yyyy HH:mm"),
			new SimpleDateFormat("MMM dd HH:mm:ss yyyy"), new SimpleDateFormat("MMM dd yyyy"),
			new SimpleDateFormat("MMM dd yy HH:mm"), new SimpleDateFormat("MMM dd yy") };

	/**
	 * Makes a document object given its content.
	 *
	 * @param paper the paper
	 * @param groups the groups
	 * @param categories the categories
	 * @param from the from
	 * @param date the date
	 * @param title the title
	 * @param authors the authors
	 * @param comments the comments
	 * @param article_class the article class
	 * @param journal_ref the journal references
	 * @param abs the abstract
	 * @param article the article
	 * @return the document
	 */
	private Document createDocument(String paper, String groups, String categories, String from, Date date,
			String title, String authors, String comments, String article_class, String journal_ref, String abs,
			String article) {

		Document document = createDocument(paper, groups, categories, from, title, authors, comments, article_class,
				journal_ref, abs, article);

		document.add(new Field("date", DateTools.timeToString(date.getTime(), DateTools.Resolution.MINUTE),
				Store.YES, Index.NOT_ANALYZED));

		return document;
	}

	/**
	 * Creates a document from a number of index field.
	 *
	 * @param paper the paper
	 * @param groups the groups
	 * @param categories the categories
	 * @param from the from
	 * @param title the title
	 * @param authors the authors
	 * @param comments the comments
	 * @param article_class the article class
	 * @param journal_ref the journal references
	 * @param abs the abstract
	 * @param article the article
	 * @return the document
	 */
	private Document createDocument(String paper, String groups, String categories, String from, String title,
			String authors, String comments, String article_class, String journal_ref, String abs, String article) {

		Document document = new Document();

		document.add(new Field("paper", paper, Store.YES, Index.NOT_ANALYZED));
		document.add(new Field("category", categories, Store.YES, Index.ANALYZED));
		document.add(new Field("group", groups, Store.YES, Index.ANALYZED));

		// Should be: Field(name, value, Store.YES, Field.Index.TOKENIZED)
		document.add(new Field("title", title, Store.YES, Index.ANALYZED, TermVector.WITH_POSITIONS_OFFSETS));
		document.add(new Field("abstract", abs, Store.YES, Index.ANALYZED, TermVector.WITH_POSITIONS_OFFSETS));
		document.add(new Field("authors", authors, Store.YES, Index.ANALYZED, TermVector.WITH_POSITIONS_OFFSETS));
		
		document.add(new Field("from", from, Store.YES, Index.ANALYZED));		
		document.add(new Field("comments", comments, Store.YES, Index.ANALYZED));
		document.add(new Field("article-class", article_class, Store.YES, Index.ANALYZED));
		document.add(new Field("journal-ref", journal_ref, Store.YES, Index.ANALYZED));
		
		document.add(new Field("article", article, Store.NO, Index.ANALYZED, TermVector.WITH_POSITIONS_OFFSETS));
		String[] articleSplit = Shingle.split(article, 5);
		for (int k = 1; k <= 5; k++)
			document.add(new Field("article_" + k, articleSplit[k - 1], Store.NO, Index.ANALYZED));

		String catchAll =   paper + " " +  groups + " " +  categories + " " +  from + " " +  title + " " + authors + " " +  comments + " " +  article_class + " " +  journal_ref + " " +  abs + " " +  article;
		
		document.add(new Field("catch-all", catchAll, Store.NO, Index.ANALYZED, TermVector.WITH_POSITIONS_OFFSETS));
		
		// Date document was indexed
		document.add(new Field("dateIndexed",
				DateTools.timeToString(new Date().getTime(), DateTools.Resolution.SECOND), Store.YES,
				Index.NOT_ANALYZED));

		// Set article length
		document.add(new Field("articleLength", Integer.toString(article.length()), Store.YES,
				Index.NOT_ANALYZED));

		return document;
	}

	
	/**
	 * Add a document to the index, replacing any existing document with the same paper id
	 *
	 * @param document the document that should be added
	 * @return true, if successful
	 * @throws Exception the exception
	 */
	public synchronized boolean indexDocument(Document document) throws Exception {
		// Open a new instance of the index writer
		Directory fsDir = FSDirectory.open(new File(indexDirectory));
		Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
		analyzerPerField.put("category", new WhitespaceAnalyzer(Version.LUCENE_36));
		PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(new StandardAnalyzer(Version.LUCENE_36),
				analyzerPerField);
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_36, wrapper);
		IndexWriter indexWriter = new IndexWriter(fsDir, config);
		
		// Updates a document if it exists, creates a new one otherwise
		indexWriter.updateDocument(new Term("paper", document.get("paper")), document);
		indexWriter.close();

		log("ADD: Wrote " + document.get("paper") + " (" + document.get("group") + ")");

		return true;
	}

	/**
	 * Given two file names, parses them and indexes them. Top level call.
	 *
	 * @param whole_abstract the complete abstract of a document including metadata
	 * @param whole_doc the body of the document
	 * @return the document containing all the parsed data
	 * @throws Exception the exception
	 */
	public Document parse(String whole_abstract, String whole_doc) throws Exception {

		int FIELD_TITLE = 0, FIELD_AUTHORS = 1, FIELD_COMMENTS = 2, FIELD_JOURNAL = 3;
		int FIELD_REPORT = 4, FIELD_ARTICLE_CLASS = 5, FIELD_ID = 6, FIELD_FROM = 7;
		int FIELD_GROUPS = 8, FIELD_DATE = 9, FIELD_CATEGORIES = 10;

		int maxField = 10;

		// Find the meta-data
		String contents = "(([^\n]+|(\n  ))+)";
		String types[] = { "Title: ", "Authors?: ", "Comments: ", "Journal-ref: ", "Report-no: ", "ACM-class: ",
				"MSC-class: ", "arXiv:", "From: ", "Groups: ", "Date( \\([a-z0-9 ]+\\))?: ", "Categories: " };
		int groups[] = { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1 };
		int field[] = { FIELD_TITLE, FIELD_AUTHORS, FIELD_COMMENTS, FIELD_JOURNAL, FIELD_REPORT, FIELD_ARTICLE_CLASS,
				FIELD_ARTICLE_CLASS, FIELD_ID, FIELD_FROM, FIELD_GROUPS, FIELD_DATE, FIELD_CATEGORIES };
		String answer[] = new String[maxField + 1];

		for (int i = 0; i < answer.length; i++)
			answer[i] = "";

		for (int i = 0; i < types.length; i++) {
			Pattern pattern = Pattern.compile("\n" + types[i] + contents);
			Matcher matcher = pattern.matcher(whole_abstract);
			// Since this is a while loop, we'll get the last entry matching
			// any type. This is important for making sure we get the date
			// right.
			while (matcher.find()) {
				if (Options.getBool("DEBUG"))
					log("Found " + types[i] + matcher.group(groups[i]));
				if (field[i] != FIELD_ID) {
					if (answer[field[i]].length() > 0)
						answer[field[i]] += " ";
					answer[field[i]] += matcher.group(groups[i]);
				} else { // arXiv IDs: we only ever take the first one
					if (answer[field[i]] == "")
						answer[field[i]] = matcher.group(groups[i]);
				}
			}
		}
		// System.out.println("Parsing done");

		// Trim the arXiv line to not have any gunk after it, just in case
		int pos = answer[FIELD_ID].indexOf(" ");
		if (pos > 0) {
			String msg = "arXiv line trimmed from '" + answer[FIELD_ID] + "' to '";
			answer[FIELD_ID] = answer[FIELD_ID].substring(0, pos);
			log(msg + answer[FIELD_ID] + "'");
		}

		// Find the abstract
		String patternStr = "\\\\\\\\\\n\\s+(([^\\n]+|\\n)+)\\n\\\\\\\\$";
		Pattern pattern = Pattern.compile(patternStr);
		Matcher matcher = pattern.matcher(whole_abstract);
		if (matcher.find()) {
			// System.out.println("Abstract itself:\n"+matcher.group(1)+"!");
			whole_abstract = matcher.group(1);
		} else {
			patternStr = "\\\\\\\\\\nAbstract:(([^\\n]+|\\n)+)\\n\\\\\\\\$";
			pattern = Pattern.compile(patternStr);
			matcher = pattern.matcher(whole_abstract);
			if (matcher.find()) {
				whole_abstract = matcher.group(1);
			}
		}

		// Remove the day of the week, anything in parenthesis, any timezone
		// info
		answer[FIELD_DATE] = answer[FIELD_DATE].replaceFirst(
				"([Mm][Oo][Nn]|[Tt][Uu][Ee]|[Ww][Ee][Dd]|[Tt][Hh][Uu]|[Ff][Rr][Ii]|[Ss][Aa][Tt]|[Ss][Uu][Nn]),? ", "");
		answer[FIELD_DATE] = answer[FIELD_DATE].replaceAll("\\(.*\\)", "");
		answer[FIELD_DATE] = answer[FIELD_DATE].replaceFirst("[-\\+][0-9]{3,4} ", "");

		// Remove common time zones (the first letter is one that doesn't
		// start a month name)
		answer[FIELD_DATE] = answer[FIELD_DATE].replaceFirst("[BCEGHIKLPQRT-Z][A-Z][A-Z] ", "");

		// System.out.println("Time is now "+answer[9]);

		// Try get the date into date format
		Date email_date = null;
		int i = 0;
		while (email_date == null && i < df.length) {
			try {
				email_date = df[i].parse(answer[9]);
			} catch (Exception e) {
				// Can't convert the date, it stays null.
			}
			i++;
		}

		// System.out.println("Parsed as "+email_date);

		// Fix the year if necessary (conversion to Calendar necessary
		// since most Date() functions are deprecated into Calendar()
		// for some reason beyond me.
		if (email_date != null) {
			GregorianCalendar cal = new GregorianCalendar();
			cal.setTime(email_date);
			if (cal.get(YEAR) < 70) {
				// Its a lost cause, we probably didn't get a year at all.
				email_date = null;
			} else if (cal.get(YEAR) < 1900) {
				cal.set(YEAR, cal.get(YEAR) + 1900);
				email_date = cal.getTime();
			}
		}

		// Remove grp_ at start of groups
		answer[FIELD_GROUPS] = answer[FIELD_GROUPS].replaceAll("grp_", "");

		Document d;
		if (email_date == null) {
			Logger.log("Error [Indexer.java]: Date is still null for paper " + answer[FIELD_ID] + ". Got '"
					+ answer[FIELD_DATE] + "'");
			/* Create document without date */
			d = createDocument(answer[FIELD_ID], answer[FIELD_GROUPS], answer[FIELD_CATEGORIES], answer[FIELD_FROM],
					answer[FIELD_TITLE], answer[FIELD_AUTHORS], answer[FIELD_COMMENTS], answer[FIELD_ARTICLE_CLASS],
					answer[FIELD_JOURNAL], whole_abstract, whole_doc);
		} else {
			/* Use the fields to index document */
			d = createDocument(answer[FIELD_ID], answer[FIELD_GROUPS], answer[FIELD_CATEGORIES], answer[FIELD_FROM],
					email_date, answer[FIELD_TITLE], answer[FIELD_AUTHORS], answer[FIELD_COMMENTS],
					answer[FIELD_ARTICLE_CLASS], answer[FIELD_JOURNAL], whole_abstract, whole_doc);
		}

		return d;

	}
	
	/**
	 * Log a message.
	 *
	 * @param s the string to log
	 */
	private static void log(String s) {
		Logger.log(s);
	}

}
