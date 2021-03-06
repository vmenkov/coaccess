package edu.cornell.cs.osmot.coaccess;

import java.util.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;


/** This servlet is used to access the coaccess data stored in the
    Lucene datastore. */

public class CoaccessServlet extends HttpServlet {

    final static String AID = IndexFiles.Fields.ARXIV_ID;

   public void	service(HttpServletRequest request, HttpServletResponse response
) {

       try { 
	   String aid = request.getParameter(AID);
	   if (aid==null) {
	       throw new IllegalArgumentException(AID + " not supplied");
	   }
	   aid = aid.trim();
	   boolean raw = getBoolean(request, "raw", false);
	   int maxlen = (int)getLong(request, "maxlen", 20);

	   String rawData = getRawData(aid);

	   response.setContentType("text/plain");
	   OutputStream aout = response.getOutputStream();
	   PrintWriter w = new PrintWriter(aout);

	   if (rawData==null) {
	       if (raw)  {
		   String result =  "NO MATCH FOR arxiv_id='" + aid + "'";
		   w.println(result);
	       }
	   } else if (raw) {
	       w.println(rawData);
	   } else {
		List<Map.Entry<String, Integer>> list= SearchFiles.aggregateCounts(rawData);
		int cnt=0;
		for(Map.Entry<String, Integer> e: list) {
		    w.println(e.getKey() + " " + e.getValue());
		    cnt++;
		    if (maxlen>0 && cnt>=maxlen) break;
		}
	   }

	   w.close();


       } catch(Exception e) {
	   try {
	       e.printStackTrace(System.out);
	       response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in CoaccessServlet: " + e); //e.getMessage());
	   } catch(IOException ex) {};
       }


   }

    /** The Lucene index directory where coaccess data are stored. */
    static final public String indexDir = "/data/coaccess/round5/lucene_framework/index";


    /** The main method for getting stored coaccess data from the
	Lucene index.  (God knows why they are stored in a Lucene
	index, but that was A&amp;Z's solution).
     */
    static String getRawData(String aid) throws IOException {
       IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexDir)));
       IndexSearcher searcher = new IndexSearcher(reader);

       /*
       String aidx = simplifyAid(aid);       	
       Term term =new Term(AID, aidx);
       */
       Term term =new Term(AID, aid);

       Query query = new TermQuery(term);
       TopDocs 	 top = searcher.search(query,1);
       ScoreDoc[] hits = top.scoreDocs;
       if (hits.length == 0) {
	   return null;
       } else {
	   Document doc = searcher.doc(hits[0].doc);
	   //	   String yearArray = doc.get("Year");
	   String yearArray = doc.get(IndexFiles.Fields.COACCESS);
	   return yearArray;
       } 
    }

    /** For some strange reasons, article IDs are stored in the data store
	with dashes and slashes removed! */
    /*
    static String simplifyAid(String aid) {
	return aid.replace("/", "").replace("-","");
    }
    */
    
    static private boolean getBoolean(HttpServletRequest request, String name, boolean defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    Boolean a = (Boolean)request.getAttribute(name);
	    return (a!=null) ? a.booleanValue() : defVal;
	}
	try {
	    return Boolean.parseBoolean(s);
	} catch (Exception ex) {
	    return defVal;
	}
    }

    /** Retrives an integer HTTP request parameter. If not found in
      the HTTP request, also looks in the attributes (which can be used
      by SurveyLogicServlet in case of internal redirect)
     */
    static public long getLong(HttpServletRequest request, String name, long defVal) {
	String s = request.getParameter(name);
	if (s==null) {
	    Long a = (Long)request.getAttribute(name);
	    return (a!=null) ? a.longValue() : defVal;
	}
	try {
	    return Long.parseLong(s);
	} catch (Exception ex) {
	    return defVal;
	}
    }


    public static void main(String[] argv) throws IOException {
	for(int i=0; i<argv.length; i++) {
	    String aid = argv[i];
	    System.out.println("Raw data for aid=" + aid);
	    String rawData = getRawData(aid);
	    if (rawData==null) {
		System.out.println( "NO MATCH FOR arxiv_id=" + aid);
	    } else {
		System.out.println(rawData);
		System.out.println("Aggregate data for aid=" + aid);
		List<Map.Entry<String, Integer>> list= SearchFiles.aggregateCounts(rawData);
		for(Map.Entry<String, Integer> e: list) {
		    System.out.println(e.getKey() + " " + e.getValue());
		}
	    }

	}
    }

}