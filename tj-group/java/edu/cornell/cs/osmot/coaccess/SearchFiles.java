package edu.cornell.cs.osmot.coaccess;
import java.util.*;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/** Command-line based coaccess demo. 
 * Command to run: java -cp lucene-analyzers-common-4.5.1.jar:lucene-demo-4.5.1-SNAPSHOT.jar:lucene-core-4.5.1.jar:lucene-queryparser-4.5.1.jar org.apache.lucene.demo.SearchFiles
 */
public class SearchFiles {
    
    private SearchFiles() {}
    
    /** Command-line based coaccess demo. */
    public static void main(String[] args) throws Exception {
        String usage =
        "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }
        
        String index = "index";
        String field = "contents";
        String queries = null;
        int repeat = 0;
        boolean raw = false;
        String queryString = null;
        int hitsPerPage = 10;
        
        for(int i = 0;i < args.length;i++) {
            if ("-index".equals(args[i])) {
                index = args[i+1];
                i++;
            } else if ("-field".equals(args[i])) {
                field = args[i+1];
                i++;
            } else if ("-queries".equals(args[i])) {
                queries = args[i+1];
                i++;
            } else if ("-query".equals(args[i])) {
                queryString = args[i+1];
                i++;
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[i+1]);
                i++;
            } else if ("-raw".equals(args[i])) {
                raw = true;
            } else if ("-paging".equals(args[i])) {
                hitsPerPage = Integer.parseInt(args[i+1]);
                if (hitsPerPage <= 0) {
                    System.err.println("There must be at least 1 hit per page.");
                    System.exit(1);
                }
                i++;
            }
        }
        
        IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(index)));
        IndexSearcher searcher = new IndexSearcher(reader);
        Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
        
        BufferedReader in = null;
        if (queries != null) {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(queries), "UTF-8"));
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        }
        QueryParser parser = new QueryParser(Version.LUCENE_40, field, analyzer);
        while (true) {
            if (queries == null && queryString == null) {                        // prompt the user
                System.out.println("Enter query: ");
            }
            
            String line = queryString != null ? queryString : in.readLine();
            
            if (line == null || line.length() == -1) {
                break;
            }
            
            line = line.trim();
            if (line.length() == 0) {
                break;
            }
            
            Query query = parser.parse(line);
            System.out.println("Searching for: " + query.toString(field));
            
            if (repeat > 0) {                           // repeat & time as benchmark
                Date start = new Date();
                for (int i = 0; i < repeat; i++) {
                    searcher.search(query, null, 100);
                }
                Date end = new Date();
                System.out.println("Time: "+(end.getTime()-start.getTime())+"ms");
            }
            
            doCoaccessSearch(in, searcher, query, hitsPerPage, raw, queries == null && queryString == null);
            
            if (queryString != null) {
                break;
            }
        }
        reader.close();
    }
    
    /**
     * This function will get the coaccess data corresponding to an arxiv_id and merge and sort them according
     * to the coaccess times. Then it will return the top ten coaccess ids.
     *
     */
    public static void doCoaccessSearch(BufferedReader in, IndexSearcher searcher, Query query,
                                      int hitsPerPage, boolean raw, boolean interactive) throws IOException {
        
        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;
        
        int numTotalHits = results.totalHits;
        
        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);
        
        while (true) {
            if (end > hits.length) {
                System.out.println("Only results 1 - " + hits.length +" of " + numTotalHits + " total matching documents collected.");
                System.out.println("Collect more (y/n) ?");
                String line = in.readLine();
                if (line.length() == 0 || line.charAt(0) == 'n') {
                    break;
                }
                
                hits = searcher.search(query, numTotalHits).scoreDocs;
            }
            
            end = Math.min(hits.length, start + hitsPerPage);
            
            for (int i = start; i < end; i++) {
                if (raw) {                              // output raw format
                    System.out.println("doc="+hits[i].doc+" score="+hits[i].score);
                    continue;
                }
                
                Document doc = searcher.doc(hits[i].doc);
                String yearArray = doc.get(IndexFiles.Fields.COACCESS);
                
		// Merge coaccess data from all years.
		List<Map.Entry<String,Integer>> list=aggregateCounts(yearArray);
 
                String topTen = "";
                int num = 0;
                for(Map.Entry<String, Integer> entry : list){
                    if(num == 10)
                        break;
                    topTen = topTen + entry.getKey() + " " + entry.getValue() + "\n";
                    num++;
                }
                
                System.out.println(topTen);
                
                //System.out.println(yearArray);
                
            }
            
            if (!interactive || end == 0) {
                break;
            }
            
            if (numTotalHits >= end) {
                boolean quit = false;
                while (true) {
                    System.out.print("Press ");
                    if (start - hitsPerPage >= 0) {
                        System.out.print("(p)revious page, ");
                    }
                    if (start + hitsPerPage < numTotalHits) {
                        System.out.print("(n)ext page, ");
                    }
                    System.out.println("(q)uit or enter number to jump to a page.");
                    
                    String line = in.readLine();
                    if (line.length() == 0 || line.charAt(0)=='q') {
                        quit = true;
                        break;
                    }
                    if (line.charAt(0) == 'p') {
                        start = Math.max(0, start - hitsPerPage);
                        break;
                    } else if (line.charAt(0) == 'n') {
                        if (start + hitsPerPage < numTotalHits) {
                            start+=hitsPerPage;
                        }
                        break;
                    } else {
                        int page = Integer.parseInt(line);
                        if ((page - 1) * hitsPerPage < numTotalHits) {
                            start = (page - 1) * hitsPerPage;
                            break;
                        } else {
                            System.out.println("No such page");
                        }
                    }
                }
                if (quit) break;
                end = Math.min(numTotalHits, start + hitsPerPage);
            }
        }
    }

    /** Processes the entry retrived from the Lucene database, computing 
	aggregate coaccess counts for all years.
	@return a List of (article id, count) pairs, ordered, in descending
	order, by count
     */
    static List<Map.Entry<String, Integer>> aggregateCounts(String yearArray) {
	Hashtable<String, Integer> ht = new Hashtable<String, Integer>();
	String[] years = yearArray.split("\n:\n");

	for(String year : years){
	    String[] coaccesses = year.split("\n");
	    for(String coaccess : coaccesses){
		if(coaccess != ""){
		    String[] temp = coaccess.split(" ");
		    if(ht.containsKey(temp[0])){
			int coaccessNum = ht.get(temp[0]);
			ht.put(temp[0], coaccessNum+Integer.parseInt(temp[1]));
		    } else {
			ht.put(temp[0], Integer.parseInt(temp[1]));
		    }
		}
	    }
	}
	List<Map.Entry<String, Integer>> list =
	    new LinkedList<Map.Entry<String, Integer>>( ht.entrySet() );
	Collections.sort( list, new Comparator<Map.Entry<String, Integer>>()
			  {
			      public int compare( Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2 )
			      {
				  return -(o1.getValue()).compareTo(o2.getValue());
			      }
			  } );
	return list;
    }

    /** Reformats a database entry, merging together data from
	separate years' secions.
	@param maxCnt Truncate the list to this many values. If negative, the
	parameter is ignored. This can be interpreted as a sparsity control
	on the coaccess matrix, restricting the number of non-zero values
	to a certain value. Note that using this parameter may make the
	sparsity matrix non-symmetric.
     */
    static String consolidate(String yearData, int maxCnt) {
	List<Map.Entry<String, Integer>> list = aggregateCounts(yearData);
	StringBuffer buf = new StringBuffer();	
	int cnt=0;
	for(Map.Entry<String, Integer> x: list) {
	    if (cnt==maxCnt) break;
	    buf.append(x.getKey() + " " + x.getValue() + "\n");
	    cnt++;
	}
	return buf.toString();
    }

}
