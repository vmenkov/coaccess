package edu.cornell.cs.osmot.indexer;

import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Iterator;

import javax.servlet.ServletContext;

import org.apache.lucene.document.Document;

import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;

import edu.cornell.cs.osmot.cache.Cache;
import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;
import edu.cornell.cs.osmot.indexer.Indexer;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * This class implements the servlet interface for adding documents to the
 * index, and updating them. It just calls the Indexer class after an upload to
 * update2.jsp has occurred.
 * 
 * @author Filip Radlinski
 * @version 1.0, April 2005
 */

public class IndexBean {

	private Indexer i;

	private Cache cache;

	public synchronized static IndexBean get(ServletContext app) throws Exception {
		IndexBean bean = (IndexBean) app.getAttribute("indexBean");
		if (bean == null) {
			log("Creating an IndexBean");
			bean = new IndexBean();
			app.setAttribute("indexBean", bean);
			log("IndexBean ready");
		}

		return bean;
	}

	public IndexBean() throws Exception {
		this(Options.get("INDEX_DIRECTORY"), Options.get("CACHE_DIRECTORY"));
	}

	public IndexBean(String indexDir, String cacheDir) throws Exception {

		i = new Indexer(indexDir, cacheDir);
		cache = new Cache(cacheDir);
	}

	public String reIndex(HttpServletRequest req) throws Exception {

		DiskFileUpload fu = new DiskFileUpload();
		Document d;
		String paper = "(blank)";

		try {
			// maximum size before a FileUploadException will be thrown (5 Meg)
			fu.setSizeMax(Options.getInt("INDEXER_MAX_LENGTH"));

			// maximum size that will be stored in memory (5 Meg)
			fu.setSizeThreshold(Options.getInt("INDEXER_MAX_LENGTH"));

			// the location for saving data that is larger than
			// getSizeThreshold()
			fu.setRepositoryPath("/tmp");

			List fileItems = fu.parseRequest(req);

			// There are two files. The shorter one is the abstract, the longer
			// is the document
			Iterator iter = fileItems.iterator();
			FileItem fi = (FileItem) iter.next();
			String abs, doc, docName = "";
			if (fi.getFieldName().equals("abstract")) {
				abs = fi.getString();
				fi = (FileItem) iter.next();
				doc = fi.getString();
				docName = fi.getName();
			} else if (fi.getFieldName().equals("document")) {
				doc = fi.getString();
				docName = fi.getName();
				abs = ((FileItem) iter.next()).getString();
			} else {
				throw new Exception("Got invalid field " + fi.getFieldName());
			}

			d = i.parse(abs, doc);

			if (d.get("paper").equals("")) {
				throw new Exception("Document " + docName + " has a blank paper field.");
			} else {
				paper = d.get("paper");
			}

			// RE-ADD ANY NEW OR SPECIAL FIELDS

			// This includes deleting any old document with the same paper number
			// It also puts the document into the cache
			i.indexDocument(d);

			// Overwrite the old file in the cache with the new file
			cache.cacheDocument(d.get("paper"), doc);

		} catch (Exception e) {
			log("FAILED POST UPD: " + e.toString());
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String exceptionAsString = sw.toString();
			exceptionAsString = exceptionAsString.replaceAll("\\n", "STACK POST UPD: ");
			log("STACK POST UPD: " + exceptionAsString);
			Logger.logIndexUpdate(paper, 0);
			throw new Exception(e);
		}

		log("UPD:" + d.get("paper"));
		Logger.logIndexUpdate(paper, 1);
		return d.get("paper");
	}

	private static void log(String log) {
		Logger.log(log);
	}

}
