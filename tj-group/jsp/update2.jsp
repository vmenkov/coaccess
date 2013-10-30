<%@ page 
  import="javax.servlet.*"
  import="javax.servlet.http.*"
  import="java.io.*"
  import="edu.cornell.cs.osmot.indexer.*"
  import="org.apache.lucene.analysis.*"
  import="org.apache.lucene.document.*"
  import="org.apache.lucene.index.*"
  import="org.apache.lucene.search.*"
  import="org.apache.lucene.queryParser.*"
%>

<%

  // Process this document
  IndexBean bean = IndexBean.get(application);

  String paper = "";
  
  try {
    paper = bean.reIndex(request);
  } catch (Exception e) {
    out.print("<p>Update failed:</p>");
    out.print("<p>"+e.toString()+"</p>");

    out.print("<p>");
    StackTraceElement st[] = e.getStackTrace();
    for (int i=0; i<st.length; i++) {
      out.print(st[i]+"<br />");
    }
    out.print("</p>");
    paper = "";
  }
  
  if (paper != "") {
    out.print("<p>Successfully updated paper "+paper+"</p>");
  }

%>
