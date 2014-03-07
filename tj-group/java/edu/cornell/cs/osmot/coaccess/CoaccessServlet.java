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
      
       Term term =new Term(AID, aid);
       Query query = new TermQuery(term);
       TopDocs 	 top = searcher.search(query,1);
       ScoreDoc[] hits = top.scoreDocs;
       Document doc = searcher.doc(hits[0].doc);
       String yearArray = doc.get("Year");
       

       return yearArray;
    }

    public static void main(String[] argv) throws IOException {
	for(int i=0; i<argv.length; i++) {
	    String aid = argv[i];
	    aid = aid.replace("/", "");
	    System.out.println("aid=" + aid);
	    System.out.println(getData(aid));
	}
    }

}