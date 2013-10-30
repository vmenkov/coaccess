<%@ page 
  import="javax.servlet.*"
  import="javax.servlet.http.*"
  import="java.io.*"
  import="java.util.Date"
  import="java.util.Random"
  import="java.util.Arrays"
  import="java.net.URLEncoder"
  import="java.util.ArrayList"
  import="java.util.TreeMap"
  import="java.util.Map"
  import="org.apache.lucene.analysis.*"
  import="org.apache.lucene.document.*"
  import="org.apache.lucene.index.*"
  import="org.apache.lucene.search.*"
  import="org.apache.lucene.queryParser.*"
  import="edu.cornell.cs.osmot.logger.*"
%>

<jsp:include page="header.jsp"/>

<h2>Interleaving results</h2>
<h3>Per query</h3>
<%
  AnalyzerBean bean = AnalyzerBean.get(application);
  TreeMap<String, ArrayList<Integer>> table = bean.getEvaluation();

 
  out.print("<table><tr><td><b>Date</b></td><td><b>Prefer Baseline</b></td><td><b>Prefer Learned</b></td>");
  out.print("<td><b>Neither</b></td></tr>\n");
  
  for (Map.Entry<String, ArrayList<Integer>> entry : table.entrySet()) {
	    String key = entry.getKey();
	    ArrayList<Integer> value = entry.getValue();
	    out.print("<tr>");
	    out.print("<td>" + key+ "</td>");
	    out.print("<td>" + value.get(0) + "</td>");
	    out.print("<td>" + value.get(1) + "</td>");
	    out.print("<td>" + value.get(2) + "</td>");
	    out.print("</tr>\n");
  }
  out.print("</table>");

%>

<h3>Per click</h3>
<%
  
  table = bean.getEvaluationClicks();

 
  out.print("<table><tr><td><b>Date</b></td><td><b>Prefer Baseline</b></td><td><b>Prefer Learned</b></td>");
  out.print("<td><b>Neither</b></td></tr>\n");
  
  for (Map.Entry<String, ArrayList<Integer>> entry : table.entrySet()) {
	    String key = entry.getKey();
	    ArrayList<Integer> value = entry.getValue();
	    out.print("<tr>");
	    out.print("<td>" + key+ "</td>");
	    out.print("<td>" + value.get(0) + "</td>");
	    out.print("<td>" + value.get(1) + "</td>");
	    out.print("<td>" + value.get(2) + "</td>");
	    out.print("</tr>\n");
  }
  out.print("</table>");

%>

<h3>Explanation</h3>
<%
  
  ArrayList<ArrayList<String>> results = bean.getEvaluationExplained();

 
  out.print("<table><tr><td><b>Date</b></td><td><b>Query</b></td><td><b>Query ID</b></td>");
  out.print("<td><b>Clicks (A = Baseline, B = Perceptron)</b></td></tr>\n");
  
  for (ArrayList<String> row : results) {
	    out.print("<tr>");
	    out.print("<td>" + row.get(0) + "</td>");
	    out.print("<td>" + row.get(1) + "</td>");
	    out.print("<td>" + row.get(2) + "</td>");
	    out.print("<td>" + row.get(3).replaceAll(",", ", ") + "</td>");
	    out.print("</tr>\n");
  }
  out.print("</table>");

%>
  
<jsp:include page="footer.jsp"/>
