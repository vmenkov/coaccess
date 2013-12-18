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
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

/** Index coaccess data from all years corresponding to a arxiv_id.
 *  Command to run: java -cp lucene-analyzers-common-4.5.1.jar:lucene-demo-4.5.1-SNAPSHOT.jar:lucene-core-4.5.1.jar:lucene-queryparser-4.5.1.jar org.apache.lucene.demo.IndexFiles -docs /data/coaccess
 *  /round5/finals
 */
public class IndexFiles {
    
    private IndexFiles() {}
    
    /** Index all text files under a directory. */
    public static void main(String[] args) {
        String usage = "java org.apache.lucene.demo.IndexFiles"
        + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
        + "This indexes the documents in DOCS_PATH, creating a Lucene index"
        + "in INDEX_PATH that can be searched with SearchFiles";
        String indexPath = "index";
        String docsPath = null;
        boolean create = true;
        for(int i=0;i<args.length;i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i+1];
                i++;
            } else if ("-docs".equals(args[i])) {
                docsPath = args[i+1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            }
        }
        
        if (docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }
        
        final File docDir = new File(docsPath);
        if (!docDir.exists() || !docDir.canRead()) {
            System.out.println("Document directory '" +docDir.getAbsolutePath()+ "' does not exist or is not readable, please check the path");
            System.exit(1);
        }
        
        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");
            
            Directory dir = FSDirectory.open(new File(indexPath));
            Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);
            IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_40, analyzer);
            
            if (create) {
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
            // iwc.setRAMBufferSizeMB(256.0);
            
            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir);
            
            // NOTE: if you want to maximize search performance,
            // you can optionally call forceMerge here.  This can be
            // a terribly costly operation, so generally it's only
            // worth it when your index is relatively static (ie
            // you're done adding documents to it):
            //
            // writer.forceMerge(1);
            
            writer.close();
            
            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");
            
        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                               "\n with message: " + e.getMessage());
        }
    }
    
    /**
     * Indexes the given file using the given writer, or if a directory is given,
     * recurses over files and directories found under the given directory.
     *
     * NOTE: This method indexes one document per input file.  This is slow.  For good
     * throughput, put multiple documents into your input file(s).  An example of this is
     * in the benchmark module, which can create "line doc" files, one document per line,
     * using the
     * <a href="../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
     * >WriteLineDocTask</a>.
     *
     * @param writer Writer to the index where the given file/dir info will be stored
     * @param file The file to index, or the directory to recurse into to find files to index
     * @throws IOException If there is a low-level I/O error
     */
    static void indexDocs(IndexWriter writer, File file)
    throws IOException {
        // do not try to index files that cannot be read
        if (file.canRead()) {
            if (file.isDirectory()) {
                String[] files = file.list();
                // an IO error could occur
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        indexDocs(writer, new File(file, files[i]));
                    }
                }
            } else {
                
                FileInputStream fis;
                try {
                    fis = new FileInputStream(file);
                } catch (FileNotFoundException fnfe) {
                    // at least on windows, some temporary files raise this exception with an "access denied" message
                    // checking if the file can be read doesn't help
                    return;
                }
                
                try {
                    
                    // make a new, empty document
                    Document doc = new Document();
                    
                    String[] years= {"2003", "2004", "2005", "2006", "2007", "2008", "2009", "2010", "2011", "2012", "2013"};
                    
                    // Loads 10 years of top k documents and uses :  as delimiter to separate years
                    String fileName = file.getName();
                    StringBuilder holdK = new StringBuilder();
                    for (String year: years){
                        String filer = "/data/coaccess/round5/" + year + "/" + fileName;
                        // check if file exists
                        File temp = new File(filer);
                        if(temp.exists()){
                            byte[] data = new byte[(int)temp.length()];
                            FileInputStream fiss = new FileInputStream(temp);
                            fiss.read(data);
                            fiss.close();
                            String s = new String(data, "UTF-8");
                            holdK.append(s+ "\n:\n");
                        }
                    }
                    // Field yearField = new StringField("Year", holdK.toString(), Field.Store.YES);
                    Field yearField = new StringField("Year", holdK.toString(), Field.Store.YES);
                    doc.add(yearField);
                    
                    // Add unique id; this is arxiv id in this case
                    String filename = file.getName().replaceAll("[@-]", "");
                    System.out.println(filename);
                    Field uniqueField = new StringField("arxiv_id", filename, Field.Store.YES);
                    doc.add(uniqueField);
                    
                    // Add author
                    Field authorField = new StringField("author", "author", Field.Store.YES);
                    doc.add(authorField);
                    
                    // Add title
                    Field titleField = new StringField("title", "title" + file.getName(), Field.Store.YES);
                    doc.add(titleField);
                    
                    if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                        // New index, so we just add the document (no old document can be there):
                        System.out.println("adding " + file);
                        writer.addDocument(doc);
                    } else {
                        // Existing index (an old copy of this document may have been indexed) so
                        // we use updateDocument instead to replace the old one matching the exact
                        // path, if present:
                        System.out.println("updating " + file);
                        writer.updateDocument(new Term("path", file.getPath()), doc);
                    }
                    
                }  finally {
                    fis.close();
                }
            }
        }
    }
}