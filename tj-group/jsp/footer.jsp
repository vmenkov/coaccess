</div>
<div id="footer">

<script language="javascript">

function installSearch() {
 if (window.external && ("AddSearchProvider" in window.external)) {
   // Firefox 2 and IE 7, OpenSearch
   window.external.AddSearchProvider("http://search.arxiv.org:8081/plugin.xml");
 } else if (window.sidebar && ("addSearchEngine" in window.sidebar)) {
   // Firefox <= 1.5, Sherlock
   window.sidebar.addSearchEngine("http://search.arxiv.org:8081/plugin.src",
                                  "http://search.arxiv.org:8081/plugin.png",
                                  "arXiv Search", "");
 } else {
   // No search engine support (IE 6, Opera, etc).
   alert("Your browser doesn't support search engine plugins.");
 }
}

</script>

<hr />
<p>Link back to: <a href="http://arXiv.org/">arXiv</a>, <a href="http://arXiv.org/form">form interface</a>, 
<a href="search.jsp">Help</a>, <a href="mailto:arxiv-search-l&#64;cs.cornell.edu">Send Feedback</a>
<br>
<a href="#" onclick="installSearch();">Install arXiv full text search in your browser</a></p>
<hr />

</div>
</body>
</html>
