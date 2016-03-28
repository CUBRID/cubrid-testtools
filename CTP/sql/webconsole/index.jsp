<%@ page contentType="text/html;charset=utf-8"%>
<%@ page import="com.navercorp.cubridqa.cqt.webconsole.WebModel" %>
<%

WebModel web = new WebModel((String)application.getAttribute("DAILYQA_ROOT"));
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<title>
CQT Web Console - Home
</title>
</head>
<body>
<%=web.showTopTestList()%>
<br>
<br>
<br>
</body>
</html>