<%@ page 
  import="javax.servlet.*"
  import="javax.servlet.http.*"
  import="java.io.*"
  import="java.util.Date"
%>

<jsp:include page="header.jsp"/>

<h2>Pick a mode</h2>

<%
if (request.getMethod().equalsIgnoreCase("POST")) {
	if (request.getParameter("mode").equals("auto")) {
		session.setAttribute("mode", null);
	} else {
		session.setAttribute("mode", request.getParameter("mode"));
	}
	out.print("<p>Set new mode successfully to: <b>" + request.getParameter("mode") + "</b></p>");
} else {
	out.print("<p>The default selection reflects the current mode.</p>");
}
String mode = (String) session.getAttribute("mode");
if (mode == null)
	mode = "";
%>
<form method="post" action="setmode.jsp">
<p>
  <input type="radio" name="mode" value="auto" <% if (mode.isEmpty()) out.print("CHECKED");%>>
  Random
</p>
<p>
  <input type="radio" name="mode" value="9a" <% if (mode.equals("9a")) out.print("CHECKED");%>>
  Learning mode
</p>
<p>
  <input type="radio" name="mode" value="mix" <% if (mode.equals("mix")) out.print("CHECKED");%>>
  Evaluation mode (Interleaving results)
</p>
<p>
<Input type = "Submit" Name = "Submit1" Value = "Submit">
</p>
</form>  
<jsp:include page="footer.jsp"/>
