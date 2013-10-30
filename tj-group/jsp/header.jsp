<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" lang="en">
<%@ page 
  import="java.net.URLEncoder"
  import="edu.cornell.cs.osmot.searcher.*"
%>
<% 
String q = SearchBean.sanitizeQuery(request.getParameter("query"));
if (q != null) {
    q = q.replaceAll("\"","&quot;");
} else { 
    q = "";
}

String searchtype = request.getParameter("searchtype");
if (searchtype == null) { 
    String category = request.getParameter("in");
    if (category != null) {
	if (category.equals("all")) {
	    searchtype = "ft";
        } else {
            searchtype = "ft-"+category;
        }
    } else {
        searchtype = "ft";
    }
}
%>
<head>
<title>arXiv.org Full Text Search</title>
<link rel="stylesheet" type="text/css" media="screen" href="http://arXiv.org/css/arXiv.css" />
<link rel="stylesheet" type="text/css" media="screen" href="style.css" />
</head>
<body>
<div id="header">
<h1><a href="http://arxiv.org/">arXiv.org</a> &gt; full text search</h1>
<form id="search" method="post" action="http://arxiv.org/search">
<div class="search-for">Search for</div>
<div class="links">(<a href="/search.jsp">Help</a> | <a href="http://arxiv.org/find">Advanced search</a>)</div>
<input type="text" name="query" value="<%=q%>" size="14" maxlength="64" />
<select name="searchtype">
<% 
  String[] desc = new String[] {"All articles", "- Physics", "- Mathematics", "- Nonlinear Sciences",
                       "- Computer Science", "- Quantitative Biology", "- Statistics",
                       "Titles/Authors/Abstracts", "- Titles", "- Authors", "- Abstracts", 
                       "Help pages"};
  String[] names = new String[] {"ft", "ft-grp_physics", "ft-grp_math", "ft-grp_nlin",
                        "ft-grp_cs", "ft-grp_q-bio", "ft-grp_stat",
                        "all", "ti", "au", "abs",
                        "help"};
  int i;

  for (i=0; i<desc.length; i++) {
    out.print("<option value=\""+names[i]+"\"");
    if (searchtype.equals(names[i])) { out.print(" selected=\"selected\""); }
    out.print(">"+desc[i]+"</option>\n");
  }
%>
</select>
<input type="submit" value="Go!" /><br />
</form>
</div>
<div id="content">
