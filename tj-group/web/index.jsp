<%@page contentType="text/html; charset=UTF-8" %> 
<%@ page import="java.io.*" %>
<%@ page import="edu.cornell.cs.osmot.coaccess.*" %>

<html>
<head>
<title>
Coaccess data service
</title>
<body>
<h1>
Coaccess data service
</h1>

<p>
This service provides access to the coaccess data stored at the Lucene index at 
<%= CoaccessServlet.indexDir %>

<p><form method="post" action="CoaccessServlet">
<table>
<tr><td>
arxiv_id <td>: <input name="arxiv_id" type="text" size="10" value="0704.0001">
</tr>
<tr><td>
<input name="raw" type="radio" size="10" value="true"> Raw Data (use this for troubleshooting, too!)
<td>
<input name="raw" type="radio" size="10" value="false" checked>Aggregate Data
</tr>
<tr><td>
Max number of results <td>: <input name="maxlen" type="text" size="10" value="10">
</tr>
<tr><td colspan=2 align="center">
<input type="Submit">
</tr>
</table>
</form>

</body>
</head>
</html>
