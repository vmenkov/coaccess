<%@ page import="javax.servlet.*" import="javax.servlet.http.*"
	import="java.io.*" import="java.text.DateFormat"
	import="java.util.Date" import="java.util.Random"
	import="java.net.URLEncoder" import="org.apache.lucene.analysis.*"
	import="org.apache.lucene.document.*"
	import="org.apache.lucene.index.*" import="org.apache.lucene.search.*"
	import="org.apache.lucene.queryParser.*"
	import="edu.cornell.cs.osmot.searcher.*"
	import="edu.cornell.cs.osmot.options.Options"
	import="edu.cornell.cs.osmot.logger.Logger"%><jsp:include
	page="header.jsp" />

<%

/* Unimplemented ideas:
- "Related documents" link
- Page #s at the bottom of the results page
 */


boolean error = false;
String errorMessage = "";
RerankedHits results;

int resultsPerPage = 10;
int maxIndex = 0; // Number shown on this page (<= resultsPerPage)

SearchBean bean = SearchBean.get(application);
String query = SearchBean.sanitizeQuery(request.getParameter("query"));
String startVal = request.getParameter("startat");
// The index of the first document shown
String subGroup = request.getParameter("in"); // The category to search in
String byDateStr = request.getParameter("byDate"); // Order by date if set
String qid = request.getParameter("qid");
HttpSession s = session;

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

boolean byDate = false;
if (byDateStr != null && byDateStr.equals("1"))
  byDate = true;


int startIndex = 0;
try {
  startIndex = Integer.parseInt(startVal);
} catch (Exception e){}
// Ignore any errors, leave at default

if (query != null && query.length() != 0) {

  out.print("<h2>arXiv.org Full Text Search Results</h2>");


  // Restrict to sub-group if selected, but only on the query we
  // use for searching, not the query we use for highlighting below
  String searchQuery = query;

  // Run the query, return no results on an error.
  long starttime = new Date().getTime();
  boolean log = true;
  if (startIndex != 0)
    log = false;
  // try {
  // Null means to pick the mode automatically
  results = bean.searchFast(searchQuery, s, request, null, log, byDate,
    subGroup);
  out.print("\n<!-- " + request.getAttribute("qid") + " -->\n");
  out.print("\n<!-- Mode " + results.getMode() + " -->\n");
  out.print("\n<!-- " + s + " -->\n");

  /* } catch (ParseException e) {
  results = new RerankedHits(null, null);
  error = true;
  errorMessage = "Error parsing your query.";
  } catch (Exception e) {
  //results = new RerankedHits(null, null);
  results = null;
  error = true;
  errorMessage = "There was an error running your query. It is possible that our server is down for maintenance. Please try again in a few minutes, and email arxiv-search-l@cs.cornell.edu if the problem persists. Please include the following details in your email <p>" +e.toString() + "</p>";
  Logger.log("Error running query >>"+searchQuery+"<<: "+e.toString());
  }*/

  long duration = new Date().getTime() - starttime;

  if (error == true) {
    out.print("<p>" + errorMessage + "</p>");

  } else if (results == null || results.length() == 0) {
    out.print("<p>No Results.</p>");

  } else {

    try {
      int id = results.id(startIndex);
    } catch (IOException e) {
      //out.print("New upper bound: "+results.lengthUB());
      startIndex = results.lengthUB() - (results.lengthUB() % 10);
    }
    maxIndex = Math.min(startIndex + resultsPerPage, results.length());

    try {
      qid = request.getAttribute("qid").toString();
    } catch (Exception e) {
      qid = request.getParameter("qid");
    }

    String params = "?query=" + URLEncoder.encode(query) + subGroupQ +
      "&amp;qid=" + qid;
    String reorder = "";
    if (!byDate) {
      reorder = "<a href=\"" + params + "&amp;byDate=1\">Reorder by date</a>.";
    } else {
      reorder = "<a href=\"" + params + "\">Reorder by score</a>.";
    }

    out.print("<p class=\"results\">Displaying hits " + (startIndex + 1) +
      " to " + maxIndex + " of ");
    out.print(results.length() + ". " + reorder + "</p>\n");

    // Get the snippets, titles and paper names

    String mo = "onMouseOver=\"window.status='";
    String mo2 =
      "'; return true;\" onMouseOut=\"window.status=''; return true;\"";

    //out.print("<table border=\"1\">\n");

    DateFormat df = null;
    if (byDate) {
      df = DateFormat.getDateInstance(DateFormat.MEDIUM);
    }

    // Loop over results we want to show
    String mode = results.getMode();
    for (int i = startIndex; i < maxIndex; i++) {
      ScoredDocument d = results.doc(i);

      String url = d.getLink() + "&amp;qid=" + qid + "&amp;qs=" +
        URLEncoder.encode(query) + subGroupQ;
      if (byDate) {
        url += "&amp;byDate=1";
      }
      String vUrl = d.getVisibleLink();
      String title = bean.boldify(bean.toHtml(d), query, mode);
      if (title.equals(""))
        title = "(No Title)";
      String snippet = bean.getSnippet(d, query, mode);
      String dStr = "";
      if (byDate) {
        try {
          dStr = "; Indexed " + df.format(d.getDate());
        } catch (Exception e) {
          dStr = "";
        }
      }
	%>
	<table cellpadding=0 cellspacing=0 border=0>
		<tr>
			<td class="snipp"><a href="<%=url%>" class="title"><%=title%></a><br />
				<span class="snippet"><%=snippet%></span><br /> <a href="<%=url%>"
				class="url"><%=vUrl%></a><span class="age"><%=dStr%></span></td>
		</tr>
	</table>
	<br />
	<%

} // End of loop over results

out.print("<p align=\"center\">");

// Print out next/prev links
String moreUrl = "?query=" + URLEncoder.encode(query) + subGroupQ + "&amp;qid="
  + qid;
if (byDate) {
  moreUrl += "&amp;byDate=1";
}
moreUrl += "&amp;startat=";

if (startIndex > 1) {
  int prevpage = startIndex - resultsPerPage;
  if (prevpage < 0) {
    prevpage = 0;
  }
  out.print("<a href=\"" + moreUrl + prevpage +
    "\">&lt;&lt; Prev</a>&nbsp;&nbsp;&nbsp;");
}

if (startIndex > 1 && startIndex + resultsPerPage < results.lengthUB()) {
  out.print(" | &nbsp;&nbsp;&nbsp;");
}

if ((startIndex + resultsPerPage) < results.lengthUB()) {
  out.print("<a href=\"" + moreUrl + (startIndex + resultsPerPage) +
    "\">Next &gt;&gt;</a>");
}

out.print("</p>");

} // End of error being false

} else {
  // Search is null, display the help

  out.print("<h2>arXiv.org Full Text Search</h2>");

%>
<jsp:include page="help.jsp" />
<%

} // End of search is null

%>

<jsp:include page="footer.jsp" />