<%@ page 
  import="javax.servlet.*"
  import="javax.servlet.http.*"
  import="java.io.*"
  import="java.text.DateFormat"
  import="java.util.Date"
  import="java.util.Random"
  import="java.net.URLEncoder"
  import="org.apache.lucene.analysis.*"
  import="org.apache.lucene.document.*"
  import="org.apache.lucene.index.*"
  import="org.apache.lucene.search.*"
  import="org.apache.lucene.queryParser.*"
  import="edu.cornell.cs.osmot.searcher.*"
  import="edu.cornell.cs.osmot.options.Options"
  import="edu.cornell.cs.osmot.logger.Logger"
%><jsp:include page="header.jsp"/>
<script language="JavaScript" src="motionpack.js"></script>
<%

/* Unimplemented ideas:
  - "Related documents" link
  - Page #s at the bottom of the results page
*/


boolean error = false;                 
String errorMessage = "";
RerankedHits results_baseline;
RerankedHits results;

int resultsPerPage = 10;
int maxIndex = 0; // Number shown on this page (<= resultsPerPage)

SearchBean bean = SearchBean.get(application);
String query = SearchBean.sanitizeQuery(request.getParameter("query"));
String startVal = request.getParameter("startat");  // The index of the first document shown
String subGroup = request.getParameter("in");       // The category to search in
String byDateStr = request.getParameter("byDate");   // Order by date if set
String qid = request.getParameter("qid");
HttpSession s   = session;

// Remove grp_ prefix from subgroup since that is how arXiv passes things
// along now. In the future, we may want to change our category names
// to match the new arXiv way.
// Remove grp_ prefix from subgroup since that is how arXiv passes things
// along now. In the future, we may want to change our category names
// to match the new arXiv way.
if (subGroup != null) {
    subGroup = subGroup.replaceFirst("grp_","");
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
} catch (Exception e) { } // Ignore any errors, leave at default

if (query != null && query.length() != 0) {

    out.print("<h2>arXiv.org Full Text Search Results</h2>");


    // Restrict to sub-group if selected, but only on the query we
    // use for searching, not the query we use for highlighting below
    String searchQuery = query;

    // Run the query, return no results on an error.
    long starttime = new Date().getTime();
    boolean log = true;
        // Null means to pick the mode automatically
		results = bean.searchDebug(searchQuery, "9a", subGroup);
        results_baseline = bean.searchDebug(searchQuery, "baseline", subGroup);
        out.print("\n<!-- " + request.getAttribute("qid") + " -->\n"); 
        out.print("\n<!-- Mode " + results.getMode() + " -->\n"); 
		out.print("\n<!-- " + s + " -->\n");

  

    long duration = new Date().getTime() - starttime;

    if (results == null || results.length() == 0) {                      
      	out.print("<p>No Results.</p>");
    } else {
 
 		try {
 			int id = results.id(startIndex);
 		} catch (IOException e) {
 			//out.print("New upper bound: "+results.lengthUB());
 			startIndex = results.lengthUB() - (results.lengthUB()%10);
		}
        maxIndex = Math.min(startIndex + resultsPerPage, results.length());


        try{
            qid = request.getAttribute("qid").toString();
        } catch (Exception e) {
            qid = request.getParameter("qid");
        }

		String params = "?query="+URLEncoder.encode(query)+subGroupQ+"&amp;qid="+qid;

        out.print("<p class=\"results\">Displaying hits "+(startIndex+1)+" to "+ maxIndex +" of ");
        out.print(results.length()+"</p>\n");

	    // Get the snippets, titles and paper names

        String mo = "onMouseOver=\"window.status='";
        String mo2 = "'; return true;\" onMouseOut=\"window.status=''; return true;\"";

        //out.print("<table border=\"1\">\n");

        DateFormat df = null;

        // Loop over results we want to show
        String mode = "debug";
        for (int i = startIndex; i < maxIndex; i++) {  
          	ScoredDocument d = results.doc(i);
          	ScoredDocument d2 = results_baseline.doc(i);
          	String url = d.getLink()+"&amp;mode=debug&amp;qid="+qid+"&amp;qs="+URLEncoder.encode(query);
          	String vUrl = d.getVisibleLink();
          	String title = bean.boldify(bean.toHtml(d),query, mode) + " (learned)";
          	String debugScore = "Total " + d.getScore() + ". " + bean.getDebugScore(d, "9a", ", ");
          	if (title.equals(""))
          		title = "(No Title)";
          	String snippet = bean.getSnippet(d, query, mode);
            String dStr = "";
           String featureVector = "<b>Mapped features</b><br />" + d.getFeatureVectorExplanation("<br />") + 
        		   "<br /><br /><b>Raw input values:</b><br />" + d.getRawFeatureValues("<br />");
            
            String url2 = d2.getLink()+"&amp;mode=debug&amp;qid="+qid+"&amp;qs="+URLEncoder.encode(query);
          	String vUrl2 = d2.getVisibleLink();
          	String title2 = bean.boldify(bean.toHtml(d2),query, mode) + "(baseline)";
          	String debugScore2 = "Total " + d2.getScore() + ". " + bean.getDebugScore(d2, "baseline", ", ");
          	if (title2.equals(""))
          		title2 = "(No Title)";
          	String snippet2 = bean.getSnippet(d2, query, mode);
            String dStr2 = "";
           String featureVector2 = "<b>Mapped features</b><br />" + d2.getFeatureVectorExplanation("<br />") + 
        		   "<br /><br /><b>Raw input values:</b><br />" + d2.getRawFeatureValues("<br />");
	  		
%>
  <table cellpadding=0 cellspacing=0 border=0><tr><td  valign="top" class="snipp"><a href="<%=url%>" class="title"><%=title%></a><br />
   <span class="snippet"><%=snippet%></span><br />
   <a href="<%=url%>" class="url"><%=vUrl%></a><span class="age"><%=dStr%></span><br /><i><%=debugScore%></i><br />
   <a href="javascript:;" onmousedown="toggleSlide('features_<%=i%>');">Show/Hide raw features</a>
   <div id="features_<%=i%>" style="display:none; overflow:hidden; height:100%"><%=featureVector%></div></td>
   
   <td valign="top" class="snipp"><a href="<%=url2%>" class="title"><%=title2%></a><br />
   <span class="snippet"><%=snippet2%></span><br />
   <a href="<%=url2%>" class="url"><%=vUrl2%></a><span class="age"><%=dStr2%></span><br /><i><%=debugScore2%></i><br />
   <a href="javascript:;" onmousedown="toggleSlide('features2_<%=i%>');">Show/Hide raw features</a>
   <div id="features2_<%=i%>" style="display:none; overflow:hidden; height:100%"><%=featureVector2%></div></td>

   </tr></table>
  <br />
<%

        } // End of loop over results

        out.print("<p align=\"center\">");

        // Print out next/prev links
        String moreUrl = "?query="+URLEncoder.encode(query)+subGroupQ+"&amp;qid="+qid;
        if (byDate) {
        	moreUrl += "&amp;byDate=1";
        }
        moreUrl += "&amp;startat=";

        if (startIndex > 1) {
          	int prevpage = startIndex - resultsPerPage;
          	if (prevpage < 0) {
            	prevpage = 0;
          	}
          	out.print("<a href=\""+moreUrl+prevpage+"\">&lt;&lt; Prev</a>&nbsp;&nbsp;&nbsp;");
        }

        if (startIndex > 1 && startIndex + resultsPerPage < results.lengthUB()) {
          	out.print(" | &nbsp;&nbsp;&nbsp;");
        }

        if ((startIndex + resultsPerPage) < results.lengthUB()) {   
 	  		out.print("<a href=\""+moreUrl+(startIndex+resultsPerPage)+"\">Next &gt;&gt;</a>");
        }

        out.print("</p>");

    } // End of error being false

} else { // Search is null, display the help 

    out.print("<h2>arXiv.org Full Text Search</h2>");
    
%>
<jsp:include page="help.jsp"/>
<%

} // End of search is null

%>

<jsp:include page="footer.jsp"/>
