<%@ page import="javax.servlet.*" import="javax.servlet.http.*"
	import="java.io.*" import="edu.cornell.cs.osmot.searcher.*"
	import="java.net.InetSocketAddress" import="java.net.URL"
	import="java.net.URLConnection"%>
<%
	int MAX_LENGTH = 20000;
	byte[] bytes = new byte[MAX_LENGTH];
	DataInputStream dis;
	URL url;
	URLConnection connection;
	String contents, allContents = "";
	
	SearchBean bean = SearchBean.get(application);
	
	String result = request.getParameter("r");
	
	String ip = request.getRemoteAddr();
	HttpSession s = session;
	String qid = request.getParameter("qid");
	String byDateStr = request.getParameter("byDate");
	boolean byDate;
	if (byDateStr == null || !byDateStr.equals("1"))
		byDate = false;
	else
		byDate = true;
	
	String prefix = bean.getOption("SEARCHER_BASE_VISIBLE_URL");
	String format = "abs";
	
	// Work out the format, if its present
	// This is unique to the arXiv search engine, so you might want
	// to disable it.
	if (result.startsWith("pdf/")) {
		format = "pdf";
		result = result.substring(4);
	} else if (result.startsWith("ps/")) {
		format = "ps";
		result = result.substring(3);
	} else if (result.startsWith("format/")) {
		format = "format";
		result = result.substring(7);
	}
	if (!format.equals("abs"))
		prefix = prefix.replaceFirst("abs", format);
	// End of arXiv specific stuff
	
	// Log the access
	if (result == null || result.length() < 3) {
		response.sendRedirect(bean.getOption("SEARCHER_URL"));
	}
	bean.logClick(request, session, qid, result, format, byDate, "");
	
	String newUrl = prefix + result;
	response.sendRedirect(newUrl);
%>
