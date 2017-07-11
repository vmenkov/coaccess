package edu.cornell.cs.osmot.coaccess;

import java.util.*;
import java.io.*;


import org.apache.commons.lang.mutable.*;

/** Computes the cumulative distribution function from the distribution data prepared
    by CoaccessStats.

    <p>For an example of command line for this tool, see
    coaccess-cumulative.sh (which is to be run aftet
    coaccess-stats.sh)

    <p>
    The data files produced by this tool can be viwed with gnuplot as follows:

<pre>
set logscale x
set nologscale y
set grid xtics ytics
set grid nomxtics nomytics

set term x11 1
set title 'Score differences - cumulative distribution function'
plot \
"coaccess-diff-cumul.dat" index "[Rank 1]"with lines title "Rank 1", \
"coaccess-diff-cumul.dat" index "[Rank 2]"with lines title "Rank 2", \
"coaccess-diff-cumul.dat" index "[Rank 3]"with lines title "Rank 3", \
"coaccess-diff-cumul.dat" index "[Rank 4]"with lines title "Rank 4", \
"coaccess-diff-cumul.dat" index "[Rank 5]"with lines title "Rank 5", \
"coaccess-diff-cumul.dat" index "[Rank 6]"with lines title "Rank 6", \
"coaccess-diff-cumul.dat" index "[Rank 7]"with lines title "Rank 7", \
"coaccess-diff-cumul.dat" index "[Rank 8]"with lines title "Rank 8", \
"coaccess-diff-cumul.dat" index "[Rank 9]"with lines title "Rank 9", \
"coaccess-diff-cumul.dat" index "[Rank 10]"with lines title "Rank 10"

set term png large
set out "coaccess-diff-cumul.png"
replot


set term x11 2
set title 'Scores - cumulative distribution function'
plot \
"coaccess-count-cumul.dat" index "[Rank 1]"with lines title "Rank 1", \
"coaccess-count-cumul.dat" index "[Rank 2]"with lines title "Rank 2", \
"coaccess-count-cumul.dat" index "[Rank 3]"with lines title "Rank 3", \
"coaccess-count-cumul.dat" index "[Rank 4]"with lines title "Rank 4", \
"coaccess-count-cumul.dat" index "[Rank 5]"with lines title "Rank 5", \
"coaccess-count-cumul.dat" index "[Rank 6]"with lines title "Rank 6", \
"coaccess-count-cumul.dat" index "[Rank 7]"with lines title "Rank 7", \
"coaccess-count-cumul.dat" index "[Rank 8]"with lines title "Rank 8", \
"coaccess-count-cumul.dat" index "[Rank 9]"with lines title "Rank 9", \
"coaccess-count-cumul.dat" index "[Rank 10]"with lines title "Rank 10"

set term png large
set out "coaccess-count-cumul.png"
replot
</pre>


    @data 2017-07-05
 */
public class Cumulative {

    static public void main(String argv[]) throws IOException {
	if (argv.length != 1) throw new IllegalArgumentException();
	doFile(argv[0]);
    }

    /** Converts the data from one file. The results go to stdout */
    static void doFile(String fname) throws IOException {
	File f = new File(fname);
    	FileReader fr = new FileReader(f);
	LineNumberReader r = new LineNumberReader(fr);
	String s;
	Vector<int[]> v = new 	Vector<int[]>();
	int linecnt = 0, cnt=0;
	while((s=r.readLine())!=null) {
	    linecnt++;
	    s = s.trim();
	    if (s.equals("") || s.startsWith("#")) {
		flush(v);
		System.out.println(s);
		if (s.length()>0) System.err.println(s);
		continue;
	    } else {
		String q[] = s.split("\\s+");
		if (q==null || q.length != 2) {
		    throw new IOException("Cannot parse line " + linecnt + " in file " + f);
		}
		int z[] = {Integer.parseInt(q[0]),Integer.parseInt(q[1])};
		v.add(z);
	    }
	}
	r.close();
	flush(v);
    }

    /** Processes the data in v[], and prints out the results.

	@param v Elements of v[] are of the form {coaccess_value, count} */
    static private void flush(Vector<int[]> v) {
	int n=0;
	for(int[] z: v) { n+=z[1]; }
	System.err.println(n);
	int sum  = 0;
	for(int[] z: v) { 
	    sum +=z[1];
	    double cumul = (double)sum/(double)n;
	    System.out.println(z[0] + "\t" + cumul);
	}	
	v.clear();
    }
	
}
