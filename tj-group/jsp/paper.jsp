<%@ page import="javax.servlet.*" import="javax.servlet.http.*"
	import="java.io.*" import="java.util.regex.Matcher"
	import="edu.cornell.cs.osmot.searcher.*"
	import="java.io.PrintWriter"
 	import="java.io.StringWriter"
	import="java.net.InetSocketAddress" import="java.net.URL"
	import="java.net.URLConnection"%>
<%
	int MAX_LENGTH = 20000;
	byte[] bytes = new byte[MAX_LENGTH];
	DataInputStream dis = null;
	URL url;
	URLConnection connection;
	String contents, allContents = "";
	
	SearchBean bean = SearchBean.get(application);
	
	String result = request.getParameter("r");
	if (result == null || result.length() < 3) {
		response.sendRedirect(bean.getOption("SEARCHER_URL"));
	}
	
	String ip = request.getRemoteAddr();
	HttpSession s = session;
	String qid = request.getParameter("qid");
	String queryString = request.getParameter("qs");
	String byDateStr = request.getParameter("byDate");
	String subGroup = request.getParameter("in"); // The category to search
													// in
	// Remove grp_ prefix from subgroup since that is how arXiv passes things
	// along now. In the future, we may want to change our category names
	// to match the new arXiv way.
	if (subGroup != null) {
		subGroup = subGroup.replaceFirst("grp_", "");
	} else {
		subGroup = "";
	}
	
	String subGroupQ = "";
	if (!subGroup.isEmpty()) {
		subGroupQ = "&amp;in=" + subGroup;
	}
	
	boolean byDate;
	if (byDateStr == null || !byDateStr.equals("1"))
		byDate = false;
	else
		byDate = true;
	
	String finalURL = bean.getOption("SEARCHER_BASE_VISIBLE_URL") + result;
	String searcherURL = bean.getOption("SEARCHER_URL");
	
	// Log the access
	bean.logClick(request, s, qid, result, byDate, queryString, subGroup);
	
	// Get the URL
	url = new URL(finalURL);
	
	connection = url.openConnection();
	connection.setRequestProperty("X-Forwarded-For", request.getRemoteAddr());
	
	// Pass on the user agent.
	String ua = request.getHeader("User-Agent");
	if (ua == null) {
		ua = "Mozilla/4.0 (unknown UA from arXiv search)";
	}
	connection.setRequestProperty("User-Agent", ua);
	try {
		dis = new DataInputStream(connection.getInputStream());
	
		int n = 1;
		int c = 0;
		while (n > 0 && c < 100) {
			n = dis.read(bytes);
			c++;
	
			// Make sure we dont loop too long.
			if (n > 0) {
				contents = new String(bytes);
				if (n < contents.length()) {
					contents = contents.substring(0, n);
				}
				allContents += contents;
			}
		}
	
		// Rewrite the format links
		String urlText = searcherURL + "details.jsp?qid=" + qid + "&amp;r=";
		if (byDate) {
			urlText = searcherURL + "details.jsp?qid=" + qid + "&amp;byDate=1&amp;r=";
		}
		
		allContents = allContents.replaceAll("\"/ps/",
				Matcher.quoteReplacement("\"" + urlText + "ps/"));
		allContents = allContents.replaceAll("\"/pdf/",
				Matcher.quoteReplacement("\"" + urlText + "pdf/"));
		allContents = allContents.replaceAll("\"/format/",
				Matcher.quoteReplacement("\"" + urlText + "format/"));
	
		// Rewrite other non-absolute references
		allContents = allContents.replaceAll("[Hh][Rr][Ee][Ff]=\"/",
				Matcher.quoteReplacement("href=\"http://arxiv.org/"));
		allContents = allContents.replaceAll("[Aa][Cc][Tt][Ii][Oo][Nn]=\"/",
				Matcher.quoteReplacement("action=\"http://arxiv.org/"));
	
		// Insert the long snippet
		String longSnippet = "";
		try {
			longSnippet = bean.getPaperSnippet(bean.getDoc(result), queryString);
		} catch (Exception e) {
			// Ignore
		}
		allContents = allContents.replaceFirst(
				"<!--CONTEXT-->",
				Matcher.quoteReplacement("<blockquote><p>" + longSnippet
						+ "</p></blockquote>\n"));
	
		// Return the contents to the user
		out.print(allContents);
	} catch (Exception e) {
	    %>
		<jsp:include page="header.jsp" />
		<h2>arXiv.org Error</h2>
		We're sorry, but there has been an error while trying to retrieve the requested document. Please try again later or
		contact the site administrator.
		<jsp:include page="footer.jsp" />
		<%
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		String exceptionAsString = sw.toString();
		bean.log("Error in paper.jsp:" + exceptionAsString);
	}
%>
