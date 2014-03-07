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
arxiv_id : <input name="arxiv_id" type="text" size="10" value="0704.0001">
<input type="Submit">
</form>

</body>
</head>
</html>
