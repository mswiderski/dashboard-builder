/**
 * Copyright (C) 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.dashboard.security;

import org.jboss.dashboard.annotation.Priority;
import org.jboss.dashboard.annotation.Startable;
import org.jboss.dashboard.ui.UIServices;
import org.jboss.dashboard.users.Role;
import org.jboss.dashboard.users.RolesManager;
import org.jboss.dashboard.SecurityServices;
import org.jboss.dashboard.workspace.*;
import org.jboss.dashboard.security.principals.DefaultPrincipal;
import org.jboss.dashboard.security.principals.RolePrincipal;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.dashboard.workspace.*;

import javax.enterprise.context.ApplicationScoped;
import javax.security.auth.Subject;
import java.lang.reflect.Method;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Principal;
import java.util.*;

/**
 * Security policy for the UI.
 */
@ApplicationScoped
public class UIPolicy implements Policy, Startable {
    private static final transient Log log = LogFactory.getLog(UIPolicy.class);

    /**
     * Unspecified principal applies to all users.
     */
    private static final Principal UNSPECIFIED_PRINCIPAL = new DefaultPrincipal("UnspecifiedPrincipal");

    /**
     * Permissions defined for this policy grouped by principal.
     * <p>Each principal has a set of permissions granted.
     * The key of the map is an instance of Principal and the value is the set of Permissions
     * granted to that principal.
     *
     * @link aggregation
     * @clientCardinality 1
     */
    protected Map permissionMap = new HashMap();

    /**
     * Hard-coded permissions. Granted by default.
     */
    private List defaultPermissions = new ArrayList();

    // Buffers containing permissions added or removed.
    // Persistent operations (save, load and delete) flush these buffers.
    private List updateBuffer = new ArrayList();
    private List deleteBuffer = new ArrayList();

    public Priority getPriority() {
        return Priority.HIGH;
    }

    public synchronized void start() throws Exception {
        log.debug("Init policy.");

        // Load state from persistent storage.
        this.load();

        // Grant default permissions
        this.grantDefaultPermissions();

        // Save policy
        this.save();
    }

    /**
     * Generates a name for any resource related with the security subsystem. Currently supported resources
     * are: Workspace, Section, Panel and PanelInstance.
     *
     * @return The resource name has the following format.<ul>
     *         <li>* or &lt;workspaceId&gt; for Workspace instances.
     *         <li>* or &lt;workspaceId&gt;.&lt;sectionId&gt; for Section instances.
     *         <li>* or &lt;workspaceId&gt;.*.&lt;panelInstanceId&gt; for Portet and PanelInstance instances.</ul>
     */
    public String getResourceName(Object resource) {
        String resourceName = "*";
        if (resource != null) {
            if (resource instanceof Workspace) {
                Workspace workspace = (Workspace) resource;
                resourceName = workspace.getId();
            } else if (resource instanceof Section) {
                Section section = (Section) resource;
                resourceName = section.getWorkspace().getId() + "." + section.getId();
            } else if (resource instanceof PanelInstance) {
                PanelInstance panel = (PanelInstance) resource;
                resourceName = panel.getWorkspace().getId() + ".*." + panel.getInstanceId();
            } else if (resource instanceof Panel) {
                Panel panel = (Panel) resource;
                resourceName = panel.getWorkspace().getId() + ".*." + panel.getInstanceId();
            } else {
                throw new IllegalArgumentException("Resource type not supported.");
            }
        }
        return resourceName;
    }

    /**
     * Retrieves a resource instance from its resource security name.
     *
     * @param resourceName    The resource name used to identify resource within the security subsystem.
     * @param permissionClass The permission class of the resource.
     * @throws Exception If any error occurs when retrieving resource.
     * @see <i>getResourceName</i> method explains the resource naming format.
     */
    public Object getResource(Class permissionClass, String resourceName) throws Exception {
        if (permissionClass.equals(WorkspacePermission.class)) {
            // All workspace
            if (resourceName.equals("*")) return null;

            // Concrete workspace
            return UIServices.lookup().getWorkspacesManager().getWorkspace(resourceName);
        } else if (permissionClass.equals(SectionPermission.class)) {
            // All sections
            if (resourceName.equals("*")) return null;

            // All workspace's sections
            int dot = resourceName.indexOf(".");
            if (dot == -1) return UIServices.lookup().getWorkspacesManager().getWorkspace(resourceName);
            String workspaceId = resourceName.substring(0, dot);
            String sectionId = resourceName.substring(dot + 1);
            if (sectionId.endsWith("*")) return UIServices.lookup().getWorkspacesManager().getWorkspace(workspaceId);

            // Concrete section
            return UIServices.lookup().getWorkspacesManager().getWorkspace(workspaceId).getSection(new Long(sectionId));
        } else if (permissionClass.equals(PanelPermission.class)) {
            // All panels
            if (resourceName.equals("*")) return null;

            // All workspace's panels
            int dot = resourceName.indexOf(".");
            if (dot == -1) return UIServices.lookup().getWorkspacesManager().getWorkspace(resourceName);
            String workspaceId = resourceName.substring(0, dot);
            dot = resourceName.indexOf(".", dot + 1);
            String panelInstanceId = resourceName.substring(dot + 1);
            if (panelInstanceId.endsWith("*")) return UIServices.lookup().getWorkspacesManager().getWorkspace(workspaceId);

            // Concrete panel
            return ((WorkspaceImpl) UIServices.lookup().getWorkspacesManager().getWorkspace(workspaceId)).getPanelInstance(new Long(panelInstanceId));
        } else {
            throw new IllegalArgumentException("Resource class not supported.");
        }
    }

    /**
     * Below is a list of permissions granted by default.
     */
    public synchronized void grantDefaultPermissions() {
        log.debug("Grant default permissions.");

        RolesManager rolesManager = SecurityServices.lookup().getRolesManager();
        WorkspacesManager workspacesManager = UIServices.lookup().getWorkspacesManager();
        SectionPermission sectionPerm = new SectionPermission("*", SectionPermission.ACTION_VIEW);
        PanelPermission panelPerm = new PanelPermission("*", PanelPermission.ACTION_VIEW);

        // All roles can view all sections and panels
        for (Role role : rolesManager.getAllRoles()) {
            RolePrincipal rolePrincipal = new RolePrincipal(role);
            defaultPermissions.add(new Object[] {rolePrincipal, sectionPerm});
            defaultPermissions.add(new Object[] {rolePrincipal, panelPerm});

            // Give users with pure role "admin" some global permissions
            if (role.getName().equals(Role.ADMIN)) {
                BackOfficePermission bPerm = new BackOfficePermission(BackOfficePermission.getResourceName(null), null);
                bPerm.grantAction(BackOfficePermission.ACTION_USE_GRAPHIC_RESOURCES);
                bPerm.grantAction(BackOfficePermission.ACTION_CREATE_WORKSPACE);
                defaultPermissions.add(new Object[]{rolePrincipal, bPerm});

                for (int i = 0; i < workspacesManager.getWorkspaces().length; i++) {
                    WorkspaceImpl workspace = workspacesManager.getWorkspaces()[i];

                    WorkspacePermission workspacePerm = new WorkspacePermission(getResourceName(workspace), null);
                    workspacePerm.grantAllActions();
                    defaultPermissions.add(new Object[]{rolePrincipal, workspacePerm});

                    SectionPermission adminSectionPerm = new SectionPermission(getResourceName(workspace) + ".*", null);
                    adminSectionPerm.grantAllActions();
                    defaultPermissions.add(new Object[]{rolePrincipal, adminSectionPerm});

                    PanelPermission adminPanelPerm = new PanelPermission(getResourceName(workspace) + ".*", null);
                    adminPanelPerm.grantAllActions();
                    defaultPermissions.add(new Object[]{rolePrincipal, adminPanelPerm});
                }
            }
        }

        for (int i = 0; i < defaultPermissions.size(); i++) {
            Object[] objects = (Object[]) defaultPermissions.get(i);
            this.addPermission((Principal) objects[0], (Permission) objects[1], Boolean.TRUE);
        }
    }

    public boolean isPermissionGrantedByDefault(PermissionDescriptor permissionDescriptor) {
        for (int i = 0; i < defaultPermissions.size(); i++) {
            Object[] objects = (Object[]) defaultPermissions.get(i);
            try {
                if (objects[0].equals(permissionDescriptor.getPrincipal())) {
                    if ((objects[1]).getClass().getName().equals(permissionDescriptor.getPermissionClass())) {
                        if (((Permission) objects[1]).getName().equals(permissionDescriptor.getPermissionResource())) {
                            return true;
                        }
                    }
                }
            } catch (InstantiationException e) {
                log.error("Error: ", e);
            }
        }
        return false;
    }

    public String describeActionName(String permissionClass, String action, Locale locale) {
        try {
            ResourceBundle messages = ResourceBundle.getBundle("org.jboss.dashboard.security.messages", locale);
            return messages.getString("action." + permissionClass + "." + action.replace(' ', '_'));
        }
        catch (MissingResourceException mre) {
            log.warn("Can't find description for " + action + " in locale " + locale);
            return action;
        }
    }

    public void addPermission(Permission newPerm) {
        this.addPermission(null, newPerm);
    }

    public void addPermission(Principal prpal, Permission perm) {
        this.addPermission(prpal, perm, Boolean.FALSE);
    }

    public synchronized void addPermission(Principal prpal, Permission perm, Boolean readonly) {
        try {

            // No principal specified then use unspecified principal
            Principal key = prpal;
            if (key == null) key = UNSPECIFIED_PRINCIPAL;

            log.debug("Adding permission " + perm + " for principal " + prpal);
            Permissions prpalPermissions = (Permissions) permissionMap.get(key);
            if (prpalPermissions == null) {
                prpalPermissions = new Permissions();
                permissionMap.put(key, prpalPermissions);
            }
            // If the permission is already granted then the new permission will be ignored when calling the following method,
            // So we don't have to implement any redundancy control.
            prpalPermissions.add(perm);

            // Update the persistent descriptor.
            PermissionDescriptor pd = PermissionManager.lookup().find(key, perm);
            if (pd == null) pd = PermissionManager.lookup().createNewItem();
            pd.setPrincipal(key);
            pd.setPermission(perm);
            pd.setReadonly(readonly);

            // If the update buffer already contains the permission descriptor then remove it.
            int pos = updateBuffer.indexOf(pd);
            if (pos != -1) updateBuffer.remove(pos);
            updateBuffer.add(pd);
        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }

    public void removePermissions(Principal p, String resourceName) {

        Permissions prpalPermissions = (Permissions)permissionMap.get(p);
        if (prpalPermissions != null && resourceName != null) {

            // Search for permissions related with the specified resource.
            List toRemove = new ArrayList();
            Enumeration en = prpalPermissions.elements();
            DefaultPermission resPerm = new DefaultPermission(resourceName, null);
            DefaultPermission regPerm = new DefaultPermission(resourceName, null);
            while (en.hasMoreElements()) {
                Permission permission = (Permission) en.nextElement();
                regPerm.setResourceName(permission.getName());
                if (resPerm.implies(regPerm)) toRemove.add(permission);
            }

            // Remove permissions
            Iterator it = toRemove.iterator();
            while (it.hasNext()) this.removePermission(p, (Permission)it.next());
        }
    }

    public void removePermissions(String resourceName) {
        Iterator it = permissionMap.keySet().iterator();
        while (it.hasNext()) {
            Principal targetPrpal = (Principal) it.next();
            this.removePermissions(targetPrpal, resourceName);
        }
    }

    public synchronized void removePermission(Principal p, Permission perm) {
        // Update buffers
        PermissionDescriptor pd = PermissionManager.lookup().find(p, perm);
        if (pd != null && !pd.isReadonly()) {
            int pos = updateBuffer.indexOf(pd);
            if (pos != -1) updateBuffer.remove(pos);
            pos = deleteBuffer.indexOf(pd);
            if (pos == -1) deleteBuffer.add(pd);

            // Remove the permission from memory
            if (log.isDebugEnabled()) log.debug("Removing permission " + perm + " for principal " + p);
            Permissions prpalPermissions = (Permissions)permissionMap.get(p);
            if (prpalPermissions != null) {
                Permissions newPermissions = new Permissions();
                Enumeration en = prpalPermissions.elements();
                while (en.hasMoreElements()) {
                    Permission permission = (Permission) en.nextElement();
                    if (!perm.equals(permission)) newPermissions.add(permission);
                }
                permissionMap.put(p, newPermissions);
            }
        }
    }

    public void removePermission(Permission oldPerm) {
        Iterator it = permissionMap.keySet().iterator();
        while (it.hasNext()) {
            Principal targetPrpal = (Principal) it.next();
            this.removePermission(targetPrpal, oldPerm);
        }
    }

    public PermissionCollection getPermissions(Subject usr) {
        Permissions userPermissions = new Permissions();
        Iterator it = usr.getPrincipals().iterator();
        while (it.hasNext()) {
            Principal principal = (Principal) it.next();
            Permissions permissions = (Permissions)permissionMap.get(principal);
            if (permissions != null) {
                Enumeration permEnum = permissions.elements();
                while (permEnum.hasMoreElements()) {
                    Permission perm = (Permission)permEnum.nextElement();
                    userPermissions.add(perm);
                }
            }
        }

        // Also retrieve permission assigned to the unspecified principal
        Permissions permissions = (Permissions)permissionMap.get(UNSPECIFIED_PRINCIPAL);
        if (permissions != null) {
            Enumeration permEnum = permissions.elements();
            while (permEnum.hasMoreElements()) {
                Permission perm = (Permission)permEnum.nextElement();
                userPermissions.add(perm);
            }
        }

        return userPermissions;
    }

    public PermissionCollection getPermissions(Principal prpal) {
        Principal principal = prpal;
        if (principal == null) principal = UNSPECIFIED_PRINCIPAL;
        return (Permissions)permissionMap.get(principal);
    }

    public Permission getPermission(Principal prpal, Class permClass, String permName) {
        PermissionCollection permCollection = getPermissions(prpal);
        if (permCollection != null) {
            Enumeration en = permCollection.elements();
            while (en.hasMoreElements()) {
                Permission perm = (Permission)en.nextElement();
                if (perm.getName().equals(permName) && perm.getClass().getName().equals(permClass.getName())) {
                    return perm;
                }
            }
        }
        return null;
    }

    public Map getPermissions(Object resource, Class permClass) throws Exception {
        final Map results = new HashMap();

        Method getResName = permClass.getMethod("getResourceName", new Class[]{Object.class});
        String resourceName = (String) getResName.invoke(permClass, new Object[]{resource});

        for (Iterator it = permissionMap.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            Permissions perms = (Permissions) entry.getValue();
            for (Enumeration en = perms.elements(); en.hasMoreElements();) {
                Permission perm = (Permission) en.nextElement();
                if (perm.getName().equals(resourceName) && permClass.equals(perm.getClass())) {
                    results.put(entry.getKey(), perm);
                }
            }
        }
        return results;
    }

    public synchronized void removePermissions(final Object resource) throws Exception {
        // Retrieve permission related with resource.
        final String resourceName = getResourceName(resource);
        log.debug("Removing all permissions for resource named " + resourceName);
        final List results = PermissionManager.lookup().find(resourceName);
        // Update buffers
        Iterator it = results.iterator();
        while (it.hasNext()) {
            PermissionDescriptor pd = (PermissionDescriptor) it.next();
            int pos = updateBuffer.indexOf(pd);
            if (pos != -1) updateBuffer.remove(pos);
            pos = deleteBuffer.indexOf(pd);
            if (pos == -1) deleteBuffer.add(pd);
        }

        // Remove all resource-related permissions from policy
        removePermissions(resourceName);
        removePermissions(resourceName + ".*");
    }

    public synchronized void clear() {
        permissionMap.clear();
        updateBuffer.clear();
        deleteBuffer.clear();
    }

    // Persistent interface implementation
    //

    public boolean isPersistent() {
        return true;
    }

    public synchronized void save() throws Exception {
        if (log.isDebugEnabled()) log.debug("Save policy with updateBuffer=" + updateBuffer);
        if (!updateBuffer.isEmpty() || !deleteBuffer.isEmpty()) {

            // Flush update buffer
            Iterator it = updateBuffer.iterator();
            while (it.hasNext()) {
                PermissionDescriptor descriptor = (PermissionDescriptor) it.next();
                descriptor.save();
            }

            // Flush delete buffer
            it = deleteBuffer.iterator();
            while (it.hasNext()) {
                PermissionDescriptor descriptor = (PermissionDescriptor) it.next();
                descriptor.delete();
            }

            // Clear the buffers and notify to the cluster the policy changes.
            updateBuffer.clear();
            deleteBuffer.clear();
        }
    }

    public void update() throws Exception {
        this.save();
    }

    public synchronized void load() throws Exception {
        // Load permission descriptors from persistent storage
        log.debug("Load policy.");
        List results = PermissionManager.lookup().getAllInstances();

        // Initialize policy
        clear();
        Iterator it = results.iterator();
        while (it.hasNext()) {
            PermissionDescriptor pd = (PermissionDescriptor) it.next();
            if (pd != null)
                try {
                    if (log.isDebugEnabled()) log.debug("Adding permission " + pd.getPermission() + " for principal " + pd.getPrincipal());
                    Principal prpal = pd.getPrincipal();
                    Permission perm = pd.getPermission();
                    addPermission(prpal, perm);
                }
                catch (InstantiationException ie) {
                    log.error("Ignoring permission descriptor " + pd);
                }
        }
    }

    public synchronized void delete() throws Exception {
        clear();
    }
}
