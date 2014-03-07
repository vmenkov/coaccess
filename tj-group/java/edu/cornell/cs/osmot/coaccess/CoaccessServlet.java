package edu.cornell.cs.osmot.coaccess;
import java.util.*;


import java.io.*;
import java.util.*;
//import java.text.*;
//import java.lang.reflect.*;
//import java.util.regex.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;


/** This servlet is used to access the coaccess data stored in the
    Lucene datastore. */

public class CoaccessServlet extends HttpServlet {

    final static String AID = "arxiv_id";

   public void	service(HttpServletRequest request, HttpServletResponse response
) {

       try { 
	   String aid = request.getParameter(AID);
	   if (aid==null) {
	       throw new IllegalArgumentException(AID + " not supplied");
	   }
	   String result = getData(aid);

	   response.setContentType("text/plain");
	   OutputStream aout = response.getOutputStream();
	   PrintWriter w = new PrintWriter(aout);
	   
	   w.println(result);
	   w.close();


       } catch(Exception e) {
	   try {
	       e.printStackTrace(System.out);
	       response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error in CoaccessServlet: " + e); //e.getMessage());
	   } catch(IOException ex) {};
       }


   }

    static final public String indexDir = "/data/coaccess/round5/lucene_framework/index";


    static String getData(String aid) throws IOException {
       IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexDir)));
       IndexSearcher searcher = new IndexSearcher(reader);
      
       String aidx = simplifyAid(aid);       	
       Term term =new Term(AID, aidx);
       Query query = new TermQuery(term);
       TopDocs 	 top = searcher.search(query,1);
       ScoreDoc[] hits = top.scoreDocs;
       if (hits.length == 0) {
	   return "NO MATCH FOR arxiv_id=" + aid;
       } else {
	   Document doc = searcher.doc(hits[0].doc);
	   String yearArray = doc.get("Year");
	   return yearArray;
       } 
    }

    /** For some strange reasons, article IDs are stored in the data store
	with dashes and slashes removed! */
    static String simplifyAid(String aid) {
	return aid.replace("/", "").replace("-","");
    }

    public static void main(String[] argv) throws IOException {
	for(int i=0; i<argv.length; i++) {
	    String aid = argv[i];
	    System.out.println("aid=" + aid);
	    System.out.println(getData(aid));
	}
    }

}