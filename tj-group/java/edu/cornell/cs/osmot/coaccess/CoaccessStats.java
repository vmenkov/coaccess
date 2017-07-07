package edu.cornell.cs.osmot.coaccess;

import java.util.*;
import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.commons.lang.mutable.*;

/** Computing some aggregate statistics for the coaccess data. Used as a command-line tool 
    @data 2017-07-05
 */

public class CoaccessStats {
    /** Usage:
	<pre>
	CoccessStats aid1 aid2 aid3 ...
	CoccessStats -
	</pre>
    */
    static public void main(String argv[]) throws IOException {

	int maxRank = 10;
	CoaccessStats stats = new CoaccessStats(maxRank);
	Vector<String> aids = (new ArgvIterator(argv,0)).readAll();
	int n = aids.size();
	for(String aid: aids) {
	    String rawData = CoaccessServlet.getRawData(aid);
	    if (rawData==null) {
		System.err.println("Ignoring " + aid);
		continue;
	    }
	    List<Map.Entry<String, Integer>> list= SearchFiles.aggregateCounts(rawData);
	    stats.addStats(list);
	}	

	String fname = "coaccess-count.dat";
	stats.writeToFile( fname, stats.aid2cnt);

	fname = "coaccess-diff.dat";
	stats.writeToFile( fname, stats.aid2diffCnt);


    }

    final int maxRank;
    private Vector<TreeMap<Integer, MutableInt>> aid2cnt, aid2diffCnt;

    CoaccessStats(int _maxRank) {
	maxRank = _maxRank;
	aid2cnt = new Vector<TreeMap<Integer, MutableInt>>();
	aid2diffCnt =  new Vector<TreeMap<Integer, MutableInt>>();
	for(int i=0; i<maxRank; i++) {
	    aid2cnt.add( new TreeMap<Integer, MutableInt>());
	    aid2diffCnt.add( new TreeMap<Integer, MutableInt>());
	}
    }

    /** map{key} += inc */
    private void addToMap(TreeMap<Integer, MutableInt> map, Integer key, int inc) {
	MutableInt cnt = map.get(key);
	if (cnt==null) map.put(key, cnt=new MutableInt());
	cnt.add(inc);
    }

    /** Fills the buckets for the histograms */
    private void addStats(List<Map.Entry<String, Integer>> list) {
	int rank=0;
	int prev= -1;
	for(Map.Entry<String, Integer> e: list) {
	    String a = e.getKey();
	    Integer score = e.getValue();
	    if (rank<maxRank) addToMap(aid2cnt.elementAt(rank), score, 1);
	    if (prev>=0) {
		int diff = prev-score;
		addToMap(aid2diffCnt.elementAt(rank-1), new Integer(diff), 1);
	    }
	    if (rank == maxRank) break;
	    prev=score;
	    rank ++;
	}
    }

    /*
    static class DescendingCountComparator implements Comparator<MapEntry<Integer,Double>> {
	public int compare(MapEntry<Integer,Double> o1, MapEntry<Integer,Double> o2)
TjA1Entry o1,TjA1Entry o2) {
	    double x = o2.ub(gamma) - o1.ub(gamma);
	    return (x>0) ? 1 : (x<0) ? -1 : 0;
	}
    }
    */
   

    static void writeToFile(String fname, Vector<TreeMap<Integer, MutableInt>> data)  throws IOException {
	System.out.println("Writing data to " +fname);
	File f= new File(fname);
	PrintWriter w= new PrintWriter(new FileWriter(f));
	for(int i=0; i<data.size(); i++) {
	    w.println("# [Rank "+(i+1)+"]");
	    TreeMap<Integer, MutableInt> q = data.elementAt(i);
	    for(Integer key: q.keySet()) {
		int val =q.get(key).intValue();
		w.println(key + "\t" + val);
	    }
	    w.println();
	}
	w.close();
    }


}
