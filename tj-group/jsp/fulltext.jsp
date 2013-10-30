<%@ page
  import="javax.servlet.*"
  import="javax.servlet.http.*"
  import="java.io.*"

  import="arxiv.searcher.*"
%>
<%
  int abort = 0;
  String paper = request.getParameter("p");
  String format = paper.replaceAll("/.*","");
  String url = "http://arxiv.org/"+paper;
  if (paper.indexOf("/") >= 0) {
    paper = paper.substring(paper.indexOf("/")+1,paper.length());
  } else {
    abort = 1;
  }

  String ip  = request.getRemoteAddr();
  String s   = session.getId();
  String qid = request.getParameter("qid");
  String byDateStr = request.getParameter("byDate");
  boolean byDate;
  if (byDateStr == null || !byDateStr.equals("1"))
      byDate = false;
  else
      byDate = true;

  if (abort == 0) {

    // Log the access
    SearchBean bean = SearchBean.get(application);

    bean.logClick(request, s, qid, paper, format, byDate);

    // Redirect the person
    response.sendRedirect(url);
  } else {
    response.sendRedirect("http://search.arxiv.org/");
  }
%>
