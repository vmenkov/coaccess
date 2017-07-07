package edu.cornell.cs.osmot.coaccess;

import java.util.*;
import java.io.*;

/** An auxiliary class used to scan and interpret a command line that
    may contain both actual arguments to be processed and a dash
    ("-"), which is interpreted as "read arguments from stdin".  

    This Java file has been copied here from package edu.rutgers.axs.indexer;
    (VM, 2017-07-05)

*/
public class ArgvIterator implements Iterator<String> {
    private String savedNext=null;
    private String[] argv;
    private int pos;
    public ArgvIterator(String[] _argv, int _pos) {
	argv = _argv;
	pos = _pos;
    }
    
    private FileIterator fi= null;

    public synchronized     boolean	hasNext() {
	if (fi!=null) {
	    if (fi.hasNext()) return true;
	    else fi=null;
	}
	while (pos<argv.length) {
	    String q = argv[pos];
	    if (!q.equals("-")) return true;
	    pos++;
	    fi = new FileIterator();
	    if (fi.hasNext()) return true;
	}
	return false;
    }

    public  synchronized String next() throws NoSuchElementException {
	if (fi!=null) {
	    String q = fi.hasNext() ? fi.next() : null;
	    if (!fi.hasNext()) fi=null;
	    if (q!=null) return q;
	}
	if (pos >= argv.length) throw new NoSuchElementException();
	String q = argv[pos++];
	if (!q.equals("-")) return q;
	
	fi = new FileIterator();
	if (!fi.hasNext()) throw new NoSuchElementException();
	return fi.next();
    }
    
    public void remove() throws UnsupportedOperationException {
	throw new  UnsupportedOperationException();
    }

    /** Reads all data into a single Vector of Strings */
    Vector<String> readAll() {
	Vector<String> v = new Vector<String>();
	while(hasNext()) {
	    v.add(next());
	}
	return v;
    }

}

