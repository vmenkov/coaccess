package edu.cornell.cs.osmot.coaccess;

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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.*;

/** Index coaccess data from all years corresponding to a arxiv_id.
 *  Command to run: java -cp lucene-analyzers-common-4.5.1.jar:lucene-demo-4.5.1-SNAPSHOT.jar:lucene-core-4.5.1.jar:lucene-queryparser-4.5.1.jar org.apache.lucene.demo.IndexFiles -docs /data/coaccess/round5
 */
public class IndexFiles {
    
    private IndexFiles() {}
    
    /** Index all text files under a directory. */
    public static void main(String[] args) {
        String usage = "java org.apache.lucene.demo.IndexFiles"
        + " -aids aidListFile [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
        + "This indexes the documents in DOCS_PATH, creating a Lucene index"
        + "in INDEX_PATH that can be searched with SearchFiles";
        String indexPath = "index";
        String docsPath = null;
	String aidListFilePath = null;
	String yearsString = null;

	boolean dry = false;
        boolean create = true;
        for(int i=0;i<args.length;i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i+1];
                i++;
            } else if ("-docs".equals(args[i])) {
                docsPath = args[i+1];
                i++;
            } else if ("-aids".equals(args[i])) {
                aidListFilePath = args[i+1];
                i++;
            } else if ("-years".equals(args[i])) {
                yearsString = args[i+1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            } else if ("-dry".equals(args[i])) {
		dry = true;
                create = false;
            }
        }
        
        if (docsPath == null || aidListFilePath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        
        final File docDir = new File(docsPath);
        // Making sure the directory exists
        if (!docDir.exists() || !docDir.isDirectory() || !docDir.canRead()) {
            System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
            System.exit(1);
        }
        
        Date start = new Date();

	int[] years = makeYearList(yearsString);

        try {
	    Vector<String> aids = readAidList(aidListFilePath); 

            System.out.println("At "+new Date()+", indexing to directory '" + indexPath + "'...");
            
            Directory dir = FSDirectory.open(new File(indexPath));
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
            IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
            
            if (dry) {
		System.out.println("This is a dry run; no indexing will be actually done!");
            } else if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }
            
            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            iwc.setRAMBufferSizeMB(512.0);            
            IndexWriter writer = dry? null : new IndexWriter(dir, iwc);

	    final int maxCnt = 100;
	    if (maxCnt>=0) {
		System.out.println("Restricting the number of results per article to " + maxCnt);
	    } else {
		System.out.println("NOT restricting the number of results per article");
	    }

	    File yearDirs[] = new File[years.length];
	    FileAccess[] fa = new FileAccess[years.length];
	    int i=0;
	    for (int year: years) {
		File y = new File(docDir, "" + year);
		if (!y.canRead()) throw new IOException("Cannot read directory " + y);
		yearDirs[i] = y;
		//		fa[i] = new SplitFileAccess(y);
		fa[i] = new JoinedFileAccess(y);
		i++;
	    }

	    int doneCnt = 0;
	    for(String aid: aids) {
		boolean done = indexDocs(writer, fa, aid, maxCnt);
		if (done) doneCnt ++;
            }

	    for(FileAccess f: fa) { f.closeAll(); }

            System.out.println("Looked for files for " + aids.size() + " articles, in " + years.length + " years' directories. Found at least some data for " + doneCnt + " articles out of these.");
	    System.out.println("At "+new Date()+ ", done indexing documents");

            // NOTE: if you want to maximize search performance,
            // you can optionally call forceMerge here.  This can be
            // a terribly costly operation, so generally it's only
            // worth it when your index is relatively static (ie
            // you're done adding documents to it):
            //


	    // Optimize is gone from modern versions of Lucene, replaced with
	    // forceMerge():
	    // http://blog.trifork.com/2011/11/21/simon-says-optimize-is-bad-for-you/
	    
	    final boolean optimize  = true;
	    if (optimize && writer!=null) {
		System.out.println("At "+new Date()+", force-merging index...");
		writer.forceMerge(1);
		// writer.optimize();
		System.out.println("At "+new Date()+", done force-merging index.");
	    }
	    

            if (writer!=null) writer.close();
            
            Date end = new Date();

            System.out.println(end.getTime() - start.getTime() + " total milliseconds");
            System.exit( dry? 2 : 0);            
        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                               "\n with message: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Reads the list of article IDs from a file. 

	@param f The name of the file to read. The file must contain one 
	article ID per line. It can be generated e.g. with "cmd.sh list".
    */
    static Vector<String> readAidList(String f) throws IOException {
	FileReader fr = new FileReader(f);
	LineNumberReader r =  new LineNumberReader(fr, 16384);
	Vector<String> aids= new    Vector<String>();
	String s=null;
	while((s = r.readLine())!=null) {
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) continue;
	    aids.add(s);
	}
	fr.close();
	System.out.println("Has read in list of "+aids.size()+" article IDs");
	return aids;
    }

    /** Converts a command line description of the year range
	(e.g. "2000:2010" to an array of years (e.g. {2000, 2001, ..., 2010}) 
    */
    static int[] makeYearList(String yearsString) {
	GregorianCalendar cal = new GregorianCalendar();
	int y1 = 2003;
	int y2 = cal.get(Calendar.YEAR);
	if (yearsString != null) {
	    String bounds[] = yearsString.split(":");
	    if (bounds.length!=2) throw new IllegalArgumentException("Cannot parse '"+yearsString+"' as yyyy:yyyy");
	    y1 = Integer.parseInt(bounds[0]);
	    y2 = Integer.parseInt(bounds[1]);
	    if (y1 < 1900 || y2 > 2020 || y1 > y2) {
		throw new IllegalArgumentException("Invalid year range ("+y1 +" thru " + y2 + ")");
	    }
	}
	int [] q = new int[ y2 - y1 + 1];
	int pos=0;
	for(int y=y1; y <= y2; y++) {
	    q[pos++] = y;
	}
	return q;
    }


    /** The names of fields for Lucene documents to create. */
    static class Fields {
	static final String ARXIV_ID = "arxiv_id", COACCESS = "coaccess";
    }

    abstract static class FileAccess {
  	abstract String read(String aid) throws IOException;
	void closeAll() throws IOException	{}
    }

    /** Reading data from split files (one per document) */
    static class SplitFileAccess extends FileAccess {
	File ydir;
	SplitFileAccess(File _dir) {
	    ydir = _dir;
	}
	String read(String aid) throws IOException {
	    String fileName = aid.replaceAll("/", "@");
	    String prefix = getPrefix(aid);
	    File subdir = new File(ydir, prefix);
	    File temp = new File(subdir, fileName);
	    // check if file exists
	    String s = "";
	    if(temp.exists()){
		byte[] data = new byte[(int)temp.length()];
		FileInputStream fiss = new FileInputStream(temp);
		fiss.read(data);
		fiss.close();
		try {
		    return new String(data, "UTF-8");
		} catch (java.io.UnsupportedEncodingException ex) {
		    return null;
		}
	    } else {
		return null;
	    }
	}
    }

    /** Reading data from joined files (one file per prefix) */
    static class JoinedFileAccess extends FileAccess {
	File ydir;
	LineNumberReader r = null;
	String oldPrefix = null;
	String prereadAid = null;       

	JoinedFileAccess(File _dir) {
	    ydir = _dir;
	}

	/** Sets prereadAid from the given line of the form ': AID' */
	private boolean gotAid(String x)  {
	    if (x.startsWith(": ")) {
		prereadAid = x.substring(2);
		return true;
	    } else return false;
	}

	/** Expects to find a string of the form ": AID" (unless EOF
	    is reached), and reads it in, setting prereadAid.
	    @return True if the AID has been pre-read either before or now. False on EOF.
	 */
	private boolean preread() throws IOException {
	    if (r == null) throw new AssertionError("Cannot call preread() w/o a reader ready");
	    if (prereadAid != null) return true;
	    String x = r.readLine();
	    if (x==null) return false;
	    if (!gotAid(x))  throw new IOException("preread() expected to find ': id', found '"+x+"'");
	    return true;
	}

	/** Expects that the AID line has been pre-read already; reads the doc body, and pre-reads the next ID line */
	private String readBody()  throws IOException{
	    if (prereadAid == null) throw new AssertionError("Cannot call readBody() w/o pre-read ID");
	    //System.out.println("Readbody ("+ydir+") for aid=" + prereadAid);
	    prereadAid = null;
	    StringBuffer b= new StringBuffer(8192);
	    String x=null;
	    while((x=r.readLine()) != null && !gotAid(x)) {
		b.append(x + "\n");  // LineNumberReader strips CR/LF!
	    }
	    //System.out.println("Readbody has: " + b);
	    return b.toString();
	}

	String read(String aid) throws IOException {
	    String prefix = getPrefix(aid);

	    if (oldPrefix == null || !prefix.equals(oldPrefix)) {
		if (r!=null) closeAll();
		File f = new File(ydir, prefix + ".txt");
		if (!f.exists()) return null;
		FileReader fr = new FileReader(f);
		r =  new LineNumberReader(fr, 16384);
		oldPrefix = prefix;
	    }

	    if (!preread()) return null;
	    while( prereadAid != null && prereadAid.compareTo(aid) < 0) {
		readBody();
	    }
	    return  (prereadAid!=null && prereadAid.equals(aid))? readBody() : null;
	}
	void closeAll()	 throws IOException{
	    if (r!=null) r.close();
	    r = null;
	}
    }

    
    /**
     Indexes the given file using the given writer, or if a directory
     is given, recurses over files and directories found under the
     given directory.
     
     NOTE: This method indexes one document per input file.  This is
     slow.  For good throughput, put multiple documents into your
     input file(s).  An example of this is in the benchmark module,
     which can create "line doc" files, one document per line, using
     the <a
     href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html">WriteLineDocTask</a>.
     
     @param writer Writer to the index where the given file/dir info will be stored
     @param dataDir Directory in which year subdirectories are to be found. E.g.  "/data/coaccess/round5/"

     @return true if a document has been created

     @throws IOException If there is a low-level I/O error
     */
    static boolean indexDocs(IndexWriter writer, FileAccess[] fa, String aid, int maxCnt)
    throws IOException {

	// Loads 10+ years of top k documents and uses :  as delimiter to separate years
	String prefix = getPrefix(aid);
	if (prefix==null) {
	    System.err.println("Warning: no prefix in aid=" + aid);
	    return false;
	}
	//String fileName = aid.replaceAll("/", "@");
	String[] v= new	String[fa.length];
	int foundFileCnt = 0;
	int yp=0;
	//	File subDirs[] = new File[] yearDirs;
	

	for (FileAccess f: fa) {
	    String s = f.read(aid);
	    if (s!=null) {
		foundFileCnt++;
	    } else {
		s = "";
	    }
	    v[yp++] = s;
	}
	if (foundFileCnt==0) return false; // no files found for this article
	
	// make a new, empty document
	Document doc = new Document();

	String coaccessData = SearchFiles.consolidate( v,  maxCnt);
      	Field yearField = new StringField(Fields.COACCESS, coaccessData, Field.Store.YES);
	doc.add(yearField);
        
	// Add unique id; this is arxiv id in this case
	Field uniqueField = new StringField(Fields.ARXIV_ID, aid, Field.Store.YES);

	doc.add(uniqueField);
	
	if (writer==null) {
	    System.out.println("Would add doc " + aid);
	} else if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
	    // New index, so we just add the document (no old
	    // document can be there):
	    System.out.println("Adding doc " + aid);
	    writer.addDocument(doc);
	} else {
	    // Existing index (an old entry for this article ID
	    // may have been indexed) so we use updateDocument
	    // instead to replace the old one matching the article
	    // ID, if present:
	    System.out.println("Updating doc " + aid);
	    writer.updateDocument(new Term(Fields.ARXIV_ID, aid), doc);
	}
	return true;
    }

    private static final char sep[] = {'.', '/', '@'};

    private static String getPrefix(String aid) {
	for(char c:  sep) {
	    int k = aid.indexOf(c);
	    if (k > 0) return aid.substring(0, k);
	}
	return null;
    }

}