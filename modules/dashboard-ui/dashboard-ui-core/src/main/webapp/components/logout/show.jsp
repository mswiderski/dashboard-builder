<%--

    Copyright (C) 2012 JBoss Inc

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

--%>
<%@ page import="org.jboss.dashboard.LocaleManager"%>
<%@ page import="org.jboss.dashboard.users.UserStatus" %>
<%@ taglib uri="factory.tld" prefix="factory" %>
<%@ taglib prefix="static" uri="static-resources.tld" %>

<%@ taglib uri="http://jakarta.apache.org/taglibs/i18n-1.0" prefix="i18n" %>

<i18n:bundle baseName="org.jboss.dashboard.ui.messages" id="defaultBundle" locale="<%=LocaleManager.currentLocale()%>"/>

<table cellspacing="5px" cellpadding="1px" border="0" class="login-table">
	<tr>
		<td valign="middle" align="left"><i18n:message key="ui.login.loggedAs">!!! Logged as</i18n:message>
			<%= UserStatus.lookup().getUserLogin() %>
		</td>
		<td valign="middle" align="right" >
			<a href="<factory:url action="logout" friendly="true"/>" onclick="return confirm('<i18n:message key="ui.workspace.confirmLogout">!!!Desea desconectarse</i18n:message>')">
				<input type="button" class="skn-button"
					   value="<i18n:message key="ui.logout">!!! Logout</i18n:message>"
					   title="<i18n:message key="ui.logout"/>">
			</a>
		</td>
	</tr>
</table>
