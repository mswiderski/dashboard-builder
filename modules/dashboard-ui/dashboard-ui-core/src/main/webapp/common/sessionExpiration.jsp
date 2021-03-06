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
<%@ page import="org.jboss.dashboard.ui.SessionManager" %>
<%@ taglib uri="mvc_taglib.tld" prefix="mvc" %>
<%@ taglib uri="http://jakarta.apache.org/taglibs/i18n-1.0" prefix="i18n" %>
<i18n:bundle id="bundle" baseName="org.jboss.dashboard.ui.messages" locale="<%=SessionManager.getCurrentLocale()%>"/>

<script type="text/javascript" language="javascript">
    <%
        int expirationTime = session.getMaxInactiveInterval();
        int expirationTimeAlert = expirationTime -30;
    %>
    function checkPageExpirationAbort() {
        if (confirm("<i18n:message key="ui.expiration.confirmMessage"/>")) {
            document.location.href = '<mvc:link handler="workspaces" action="show-current-screen"/>'
        }
        window.status = '<i18n:message key="ui.expiration.expirationInminent"/>';
    };

    if (IE || NS)
        setTimeout('checkPageExpirationAbort()', <%=expirationTimeAlert*1000%>);

</script>
