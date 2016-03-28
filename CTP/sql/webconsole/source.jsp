<%@ page contentType="text/html;charset=utf-8"%>
<%@ page import="com.navercorp.cubridqa.cqt.webconsole.WebModel" %>
<%

WebModel web = new WebModel((String)application.getAttribute("DAILYQA_ROOT"));
String p=(String)request.getParameter("p");
%>

<%=web.showSource(p)%>
