/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.anjocaido.groupmanager.dataholder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.anjocaido.groupmanager.GroupManager;
import org.anjocaido.groupmanager.data.DataUnit;
import org.anjocaido.groupmanager.data.Group;
import org.anjocaido.groupmanager.data.User;
import org.anjocaido.groupmanager.events.GMGroupEvent;
import org.anjocaido.groupmanager.events.GMSystemEvent;
import org.anjocaido.groupmanager.events.GMUserEvent;
import org.anjocaido.groupmanager.events.GMUserEvent.Action;
import org.anjocaido.groupmanager.permissions.AnjoPermissionsHandler;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.reader.UnicodeReader;

/**
 * One instance of this should exist per world/mirror it contains all functions
 * to manage these data sets and points to the relevant users and groups
 * objects.
 *
 * @author gabrielcouto, ElgarL
 */
public class WorldDataHolder {

    /**
     * World name
     */
    protected String name;
    /**
     * The actual groups holder
     */
    protected GroupsDataHolder groups = new GroupsDataHolder();
    /**
     * The actual users holder
     */
    protected UsersDataHolder users = new UsersDataHolder();
    /**
     *
     */
    protected AnjoPermissionsHandler permissionsHandler;

    /**
     * Prevent direct instantiation
     *
     * @param worldName
     */
    public WorldDataHolder(String worldName) {

        name = worldName;
    }

    /**
     * The main constructor for a new WorldDataHolder
     *
     * @param worldName
     * @param groups
     * @param users
     */
    public WorldDataHolder(String worldName, GroupsDataHolder groups, UsersDataHolder users) {

        this.name = worldName;
        this.groups = groups;
        this.users = users;

        // this.defaultGroup = defaultGroup;
    }

    /**
     * update the dataSource to point to this object.
     *
     * This should be called whenever a set of world data is fetched.
     */
    public void updateDataSource() {

        this.groups.setDataSource(this);
        this.users.setDataSource(this);
    }

    /**
     * Search for a user. If it doesn't exist, create a new one with default
     * group.
     *
     * @param userId the UUID String or name of the user
     * @return class that manage that user permission
     */
    public User getUser(String userId) {

        if (getUsers().containsKey(userId.toLowerCase())) {
            return getUsers().get(userId.toLowerCase());
        }

        // Legacy name matching
        if (userId.length() < 36) {

            // Search for a LastName match
            for (User user : getUserList()) {

                if (user.getLastName().equalsIgnoreCase(userId)) {
                    return user;
                }
            }

        }

        // No user account found so create a new one.
        User newUser = createUser(userId);

        return newUser;
    }

    /**
     * *** Internal GM use only *** This is called when a player joins to
     * update/add their UUID.
     *
     * @param uUID the player objects UUID.
     * @param currentName the name they have just logged in with.
     * @return the user object for this player.
     */
    public User getUser(String uUID, String currentName) {

        // Check for a UUID account
        User user = getUsers().get(uUID.toLowerCase());

        if (user != null) {

            user.setLastName(currentName);
            return user;

        }

        // Search for a LastName match
        for (User usr : getUserList()) {

            if (usr.getLastName().equalsIgnoreCase(currentName) && usr.getUUID().equalsIgnoreCase(usr.getLastName())) {

                // Clone this user so we can set it's uUID
                user = usr.clone(uUID, currentName);

                // Delete it and replace with the new clone.
                this.removeUser(usr.getUUID());
                this.addUser(user);

                return getUsers().get(uUID.toLowerCase());
            }

        }

        // No user account found so create a new one.
        User newUser = createUser(uUID);
        newUser.setLastName(currentName);

        return newUser;
    }

    /**
     * Add a user to the list. If it already exists, overwrite the old.
     *
     * @param theUser the user you want to add to the permission list
     */
    public void addUser(User theUser) {

        if (theUser.getDataSource() != this) {
            theUser = theUser.clone(this);
        }
        if (theUser == null) {
            return;
        }
        if ((theUser.getGroup() == null)) {
            theUser.setGroup(groups.getDefaultGroup());
        }
        removeUser(theUser.getUUID());
        getUsers().put(theUser.getUUID().toLowerCase(), theUser);
        setUsersChanged(true);
        if (GroupManager.isLoaded()) {
            GroupManager.getGMEventHandler().callEvent(theUser, Action.USER_ADDED);
        }
    }

    /**
     * Removes the user from the list. (he might become a default user)
     *
     * @param userId the UUID or username for the user to remove
     * @return true if it had something to remove
     */
    public boolean removeUser(String userId) {

        if (getUsers().containsKey(userId.toLowerCase())) {
            getUsers().remove(userId.toLowerCase());
            setUsersChanged(true);
            if (GroupManager.isLoaded()) {
                GroupManager.getGMEventHandler().callEvent(userId, GMUserEvent.Action.USER_REMOVED);
            }
            return true;
        }
        return false;
    }

    /**
     *
     * @param userId
     * @return true if we have data for this player.
     */
    public boolean isUserDeclared(String userId) {

        return getUsers().containsKey(userId.toLowerCase());
    }

    /**
     * Change the default group of the file.
     *
     * @param group the group you want make default.
     */
    public void setDefaultGroup(Group group) {

        if (!getGroups().containsKey(group.getName().toLowerCase()) || (group.getDataSource() != this)) {
            addGroup(group);
        }
        groups.setDefaultGroup(getGroup(group.getName()));
        setGroupsChanged(true);
        if (GroupManager.isLoaded()) {
            GroupManager.getGMEventHandler().callEvent(GMSystemEvent.Action.DEFAULT_GROUP_CHANGED);
        }
    }

    /**
     * Returns the default group of the file
     *
     * @return the default group
     */
    public Group getDefaultGroup() {

        return groups.getDefaultGroup();
    }

    /**
     * Returns a group of the given name
     *
     * @param groupName the name of the group
     * @return a group if it is found. null if not found.
     */
    public Group getGroup(String groupName) {

        if (groupName.toLowerCase().startsWith("g:")) {
            return GroupManager.getGlobalGroups().getGroup(groupName);
        } else {
            return getGroups().get(groupName.toLowerCase());
        }
    }

    /**
     * Check if a group exists. Its the same of getGroup, but check if it is
     * null.
     *
     * @param groupName the name of the group
     * @return true if exists. false if not.
     */
    public boolean groupExists(String groupName) {

        if (groupName.toLowerCase().startsWith("g:")) {
            return GroupManager.getGlobalGroups().hasGroup(groupName);
        } else {
            return getGroups().containsKey(groupName.toLowerCase());
        }
    }

    /**
     * Add a group to the list
     *
     * @param groupToAdd
     */
    public void addGroup(Group groupToAdd) {

        if (groupToAdd.getName().toLowerCase().startsWith("g:")) {
            GroupManager.getGlobalGroups().addGroup(groupToAdd);
            GroupManager.getGMEventHandler().callEvent(groupToAdd, GMGroupEvent.Action.GROUP_ADDED);
            return;
        }

        if (groupToAdd.getDataSource() != this) {
            groupToAdd = groupToAdd.clone(this);
        }
        removeGroup(groupToAdd.getName());
        getGroups().put(groupToAdd.getName().toLowerCase(), groupToAdd);
        setGroupsChanged(true);
        if (GroupManager.isLoaded()) {
            GroupManager.getGMEventHandler().callEvent(groupToAdd, GMGroupEvent.Action.GROUP_ADDED);
        }
    }

    /**
     * Remove the group from the list
     *
     * @param groupName
     * @return true if had something to remove. false the group was default or
     * non-existant
     */
    public boolean removeGroup(String groupName) {

        if (groupName.toLowerCase().startsWith("g:")) {
            return GroupManager.getGlobalGroups().removeGroup(groupName);
        }

        if (getDefaultGroup() != null && groupName.equalsIgnoreCase(getDefaultGroup().getName())) {
            return false;
        }
        if (getGroups().containsKey(groupName.toLowerCase())) {
            getGroups().remove(groupName.toLowerCase());
            setGroupsChanged(true);
            if (GroupManager.isLoaded()) {
                GroupManager.getGMEventHandler().callEvent(groupName.toLowerCase(), GMGroupEvent.Action.GROUP_REMOVED);
            }
            return true;
        }
        return false;

    }

    /**
     * Creates a new User with the given name and adds it to this holder.
     *
     * @param userId the UUID or username you want
     * @return null if user already exists. or new User
     */
    public User createUser(String userId) {

        if (getUsers().containsKey(userId.toLowerCase())) {
            return null;
        }
        User newUser = new User(this, userId);
        newUser.setGroup(groups.getDefaultGroup(), false);
        addUser(newUser);
        setUsersChanged(true);
        return newUser;
    }

    /**
     * Creates a new Group with the given name and adds it to this holder
     *
     * @param groupName the groupname you want
     * @return null if group already exists. or new Group
     */
    public Group createGroup(String groupName) {

        if (groupName.toLowerCase().startsWith("g:")) {
            Group newGroup = new Group(groupName);
            return GroupManager.getGlobalGroups().newGroup(newGroup);
        }

        if (getGroups().containsKey(groupName.toLowerCase())) {
            return null;
        }

        Group newGroup = new Group(this, groupName);
        addGroup(newGroup);
        setGroupsChanged(true);
        return newGroup;
    }

    /**
     *
     * @return a collection of the groups
     */
    public Collection<Group> getGroupList() {

        synchronized (getGroups()) {
            return new ArrayList<>(getGroups().values());
        }
    }

    /**
     *
     * @return a collection of the users
     */
    public Collection<User> getUserList() {

        synchronized (getUsers()) {
            return new ArrayList<>(getUsers().values());
        }
    }

    /**
     * reads the file again
     */
    public void reload() {

        try {
            reloadGroups();
            reloadUsers();
        } catch (Exception ex) {
            Logger.getLogger(WorldDataHolder.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Refresh Group data from file
     */
    public void reloadGroups() {

        GroupManager.setLoaded(false);
        try {
            // temporary holder in case the load fails.
            WorldDataHolder ph = new WorldDataHolder(this.getName());

            loadGroups(ph, getGroupsFile());
            // transfer new data
            resetGroups();
            for (Group tempGroup : ph.getGroupList()) {
                tempGroup.clone(this);
            }
            this.setDefaultGroup(getGroup(ph.getDefaultGroup().getName()));
            this.removeGroupsChangedFlag();
            this.setTimeStampGroups(getGroupsFile().lastModified());

            ph = null;
        } catch (Exception ex) {
            Logger.getLogger(WorldDataHolder.class.getName()).log(Level.WARNING, null, ex);
        }
        GroupManager.setLoaded(true);
        GroupManager.getGMEventHandler().callEvent(GMSystemEvent.Action.RELOADED);
    }

    /**
     * Refresh Users data from file
     */
    public void reloadUsers() {

        GroupManager.setLoaded(false);
        try {
            // temporary holder in case the load fails.
            WorldDataHolder ph = new WorldDataHolder(this.getName());
            // copy groups for reference
            for (Group tempGroup : this.getGroupList()) {
                tempGroup.clone(ph);
            }
            // setup the default group before loading user data.
            ph.setDefaultGroup(ph.getGroup(getDefaultGroup().getName()));
            loadUsers(ph, getUsersFile());
            // transfer new data
            resetUsers();
            for (User tempUser : ph.getUserList()) {
                tempUser.clone(this);
            }
            this.removeUsersChangedFlag();
            this.setTimeStampUsers(getUsersFile().lastModified());

            ph = null;
        } catch (Exception ex) {
            Logger.getLogger(WorldDataHolder.class.getName()).log(Level.WARNING, null, ex);
        }
        GroupManager.setLoaded(true);
        GroupManager.getGMEventHandler().callEvent(GMSystemEvent.Action.RELOADED);
    }

    public void loadGroups(File groupsFile) {

        GroupManager.setLoaded(false);
        try {
            setGroupsFile(groupsFile);
            loadGroups(this, groupsFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("The file which should contain groups does not exist!\n" + groupsFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error accessing the groups file!\n" + groupsFile.getPath());
        }

        GroupManager.setLoaded(true);
    }

    public void loadUsers(File usersFile) {

        GroupManager.setLoaded(false);
        try {
            setUsersFile(usersFile);
            loadUsers(this, usersFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("The file which should contain users does not exist!\n" + usersFile.getPath());
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error accessing the users file!\n" + usersFile.getPath());
        }

        GroupManager.setLoaded(true);
    }

    /**
     * Returns a NEW data holder containing data read from the files
     *
     * @param worldName
     * @param groupsFile
     * @param usersFile
     * @return
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static WorldDataHolder load(String worldName, File groupsFile, File usersFile) throws FileNotFoundException, IOException {

        WorldDataHolder ph = new WorldDataHolder(worldName);

        GroupManager.setLoaded(false);
        if (groupsFile != null) {
            loadGroups(ph, groupsFile);
        }
        if (usersFile != null) {
            loadUsers(ph, usersFile);
        }
        GroupManager.setLoaded(true);

        return ph;
    }

    /**
     * Updates the WorldDataHolder from the Groups file
     *
     * @param ph
     * @param groupsFile
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected static void loadGroups(WorldDataHolder ph, File groupsFile) throws FileNotFoundException, IOException {

		// READ GROUPS FILE
        Yaml yamlGroups = new Yaml(new SafeConstructor());
        Map<String, Object> groupsRootDataNode;

        if (!groupsFile.exists()) {
            throw new IllegalArgumentException("The file which should contain groups does not exist!\n" + groupsFile.getPath());
        }
        try (FileInputStream groupsInputStream = new FileInputStream(groupsFile)) {
            groupsRootDataNode = (Map<String, Object>) yamlGroups.load(new UnicodeReader(groupsInputStream));
            if (groupsRootDataNode == null) {
                throw new NullPointerException();
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("The following file couldn't pass on Parser.\n" + groupsFile.getPath(), ex);
        }

		// PROCESS GROUPS FILE
        Map<String, List<String>> inheritance = new HashMap<>();
        Map<String, Object> allGroupsNode;

        /*
         * Fetch all groups under the 'groups' entry.
         */
        try {
            allGroupsNode = (Map<String, Object>) groupsRootDataNode.get("groups");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Your " + groupsFile.getPath() + " file is invalid. See console for details.", ex);
        }

        if (allGroupsNode == null) {
            throw new IllegalArgumentException("You have no groups in " + groupsFile.getPath() + ".");
        }

        Iterator<String> groupItr = allGroupsNode.keySet().iterator();
        String groupKey;
        Integer groupCount = 0;

        /*
         * loop each group entry and process it's data.
         */
        while (groupItr.hasNext()) {

            try {
                groupCount++;
                // Attempt to fetch the next group name.
                groupKey = groupItr.next();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid group name for group entry (" + groupCount + ") in file: " + groupsFile.getPath(), ex);
            }

            /*
             * Fetch this groups child nodes
             */
            Map<String, Object> thisGroupNode;

            try {
                thisGroupNode = (Map<String, Object>) allGroupsNode.get(groupKey);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid child nodes for group '" + groupKey + "' in file: " + groupsFile.getPath(), ex);
            }

            /*
             * Create a new group with this name in the assigned data source.
             */
            Group thisGrp = ph.createGroup(groupKey);

            if (thisGrp == null) {
                throw new IllegalArgumentException("I think this Group was declared more than once: " + groupKey + " in file: " + groupsFile.getPath());
            }

			// DEFAULT NODE
            Object nodeData = null;
            try {
                nodeData = thisGroupNode.get("default");
            } catch (Exception ex) {
                throw new IllegalArgumentException("Bad format found in 'permissions' for group: " + groupKey + " in file: " + groupsFile.getPath());
            }

            if (nodeData == null) {
                /*
                 * If no 'default' node is found do nothing.
                 */
            } else if ((Boolean.parseBoolean(nodeData.toString()))) {
                /*
                 * Set this as the default group. Warn if some other group has
                 * already claimed that position.
                 */
                if (ph.getDefaultGroup() != null) {
                    GroupManager.logger.log(Level.WARNING, "The group ''{0}'' is claiming to be default where ''{1}'' already was.", new Object[]{thisGrp.getName(), ph.getDefaultGroup().getName()});
                    GroupManager.logger.log(Level.WARNING, "Overriding first default request in file: {0}", groupsFile.getPath());
                }
                ph.setDefaultGroup(thisGrp);
            }

			// PERMISSIONS NODE
            try {
                nodeData = thisGroupNode.get("permissions");
            } catch (Exception ex) {
                throw new IllegalArgumentException("Bad format found in 'permissions' for '" + groupKey + "' in file: " + groupsFile.getPath());
            }

            if (nodeData == null) {
                /*
                 * If no permissions node is found, or it's empty do nothing.
                 */
            } else {
                /*
                 * There is a permission list Which seems to hold some data
                 */
                if (nodeData instanceof List) {
                    /*
                     * Check each entry and add it as a new permission.
                     */
                    try {
                        for (Object o : ((List) nodeData)) {
                            try {
                                /*
                                 * Only add this permission if it's not empty.
                                 */
                                if (!o.toString().isEmpty()) {
                                    thisGrp.addPermission(o.toString());
                                }

                            } catch (NullPointerException ex) {
								// Ignore this entry as it's null. It can be
                                // safely dropped
                            }
                        }
                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Invalid formatting found in 'permissions' section for group: " + thisGrp.getName() + " in file: " + groupsFile.getPath(), ex);
                    }

                } else if (nodeData instanceof String) {
                    /*
                     * Only add this permission if it's not empty.
                     */
                    if (!nodeData.toString().isEmpty()) {
                        thisGrp.addPermission((String) nodeData);
                    }

                } else {
                    throw new IllegalArgumentException("Unknown type of 'permissions' node(Should be String or List<String>) for group:  " + thisGrp.getName() + " in file: " + groupsFile.getPath());
                }
                /*
                 * Sort all permissions so they are in the correct order for
                 * checking.
                 */
                thisGrp.sortPermissions();
            }

			// INFO NODE
            try {
                nodeData = thisGroupNode.get("info");
            } catch (Exception ex) {
                throw new IllegalArgumentException("Bad format found in 'info' section for group: " + groupKey + " in file: " + groupsFile.getPath());
            }

            if (nodeData == null) {
                /*
                 * No info section was found, so leave all variables as
                 * defaults.
                 */
                GroupManager.logger.log(Level.WARNING, "The group ''{0}'' has no ''info'' section!", thisGrp.getName());
                GroupManager.logger.log(Level.WARNING, "Using default values: {0}", groupsFile.getPath());


            } else if (nodeData instanceof Map) {
                try {
                    thisGrp.setVariables((Map<String, Object>) nodeData);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Invalid formatting found in 'info' section for group: " + thisGrp.getName() + " in file: " + groupsFile.getPath(), ex);
                }

            } else {
                throw new IllegalArgumentException("Unknown entry found in 'info' section for group: " + thisGrp.getName() + " in file: " + groupsFile.getPath());
            }

			// INHERITANCE NODE
            try {
                nodeData = thisGroupNode.get("inheritance");
            } catch (Exception ex) {
                throw new IllegalArgumentException("Bad format found in 'inheritance' section for group: " + groupKey + " in file: " + groupsFile.getPath());
            }

            if (nodeData == null || nodeData instanceof List) {
                if (nodeData == null) {
                    /*
                     * If no inheritance node is found, or it's empty do
                     * nothing.
                     */
                } else {

                    try {
                        for (String grp : (List<String>) nodeData) {
                            inheritance.putIfAbsent(groupKey, new ArrayList<>());
                            inheritance.get(groupKey).add(grp);
                        }

                    } catch (Exception ex) {
                        throw new IllegalArgumentException("Invalid formatting found in 'inheritance' section for group: " + thisGrp.getName() + " in file: " + groupsFile.getPath(), ex);
                    }

                }
            } else {
                throw new IllegalArgumentException("Unknown entry found in 'inheritance' section for group: " + thisGrp.getName() + " in file: " + groupsFile.getPath());
            }

			// END GROUP
        }

        if (ph.getDefaultGroup() == null) {
            throw new IllegalArgumentException("There was no Default Group declared in file: " + groupsFile.getPath());
        }

        /*
         * Build the inheritance map and recored any errors
         */
        inheritance.keySet().forEach((group) -> {
            List<String> inheritedList = inheritance.get(group);
            Group thisGroup = ph.getGroup(group);
            if (thisGroup != null) {
                inheritedList.stream().filter((inheritedKey) -> (inheritedKey != null)).forEach((inheritedKey) -> {
                    Group inheritedGroup = ph.getGroup(inheritedKey);
                    if (inheritedGroup != null) {
                        thisGroup.addInherits(inheritedGroup);
                    } else {
                        GroupManager.logger.log(Level.WARNING, "Inherited group ''{0}'' not found for group {1}. Ignoring entry in file: {2}", new Object[]{inheritedKey, thisGroup.getName(), groupsFile.getPath()});
                    }
                });
            }
        });

        ph.removeGroupsChangedFlag();
        // Update the LastModified time.
        ph.setGroupsFile(groupsFile);
        ph.setTimeStampGroups(groupsFile.lastModified());

        // return ph;
    }

    /**
     * Updates the WorldDataHolder from the Users file
     *
     * @param ph
     * @param usersFile
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected static void loadUsers(WorldDataHolder ph, File usersFile) throws FileNotFoundException, IOException {

        // READ USERS FILE
        Yaml yamlUsers = new Yaml(new SafeConstructor());
        Map<String, Object> usersRootDataNode;
        if (!usersFile.exists()) {
            throw new IllegalArgumentException("The file which should contain users does not exist!\n" + usersFile.getPath());
        }
        try (FileInputStream usersInputStream = new FileInputStream(usersFile)) {
            usersRootDataNode = (Map<String, Object>) yamlUsers.load(new UnicodeReader(usersInputStream));
            if (usersRootDataNode == null) {
                throw new NullPointerException();
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("The following file couldn't pass on Parser.\n" + usersFile.getPath(), ex);
        }

		// PROCESS USERS FILE
        Map<String, Object> allUsersNode;

        /*
         * Fetch all child nodes under the 'users' entry.
         */
        try {
            allUsersNode = (Map<String, Object>) usersRootDataNode.get("users");
        } catch (Exception ex) {
            throw new IllegalArgumentException("Your " + usersFile.getPath() + " file is invalid. See console for details.", ex);
        }

		// Load users if the file is NOT empty
        if (allUsersNode != null) {

            Iterator<String> usersItr = allUsersNode.keySet().iterator();
            String usersKey;
            Object node;
            Integer userCount = 0;

            while (usersItr.hasNext()) {
                try {
                    userCount++;
                    // Attempt to fetch the next user name.
                    node = usersItr.next();
                    if (node instanceof Integer) {
                        usersKey = Integer.toString((Integer) node);
                    } else {
                        usersKey = node.toString();
                    }

                } catch (Exception ex) {
                    throw new IllegalArgumentException("Invalid node type for user entry (" + userCount + ") in file: " + usersFile.getPath(), ex);
                }

                Map<String, Object> thisUserNode;
                try {
                    thisUserNode = (Map<String, Object>) allUsersNode.get(node);
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Bad format found for user: " + usersKey + " in file: " + usersFile.getPath());
                }

                User thisUser = ph.createUser(usersKey);
                if (thisUser == null) {
                    throw new IllegalArgumentException("I think this user was declared more than once: " + usersKey + " in file: " + usersFile.getPath());
                }

				// LASTNAME NODES
                Object nodeData;
                try {

                    nodeData = thisUserNode.get("lastname");

                } catch (Exception ex) {
                    throw new IllegalArgumentException("Bad format found in 'subgroups' for user: " + usersKey + " in file: " + usersFile.getPath());
                }

                if ((nodeData != null) && (nodeData instanceof String)) {

                    thisUser.setLastName((String) nodeData);

                }

				// USER PERMISSIONS NODES
                try {
                    nodeData = thisUserNode.get("permissions");
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Bad format found in 'permissions' for user: " + usersKey + " in file: " + usersFile.getPath());
                }

                if (nodeData == null) {
                    /*
                     * If no permissions node is found, or it's empty do
                     * nothing.
                     */
                } else {
                    try {
                        if (nodeData instanceof List) {

                            ((List) nodeData).stream().filter(o -> !o.toString().isEmpty()).forEach(o -> thisUser.addPermission(o.toString()));

                        } else if (nodeData instanceof String) {

                            /*
                             * Only add this permission if it's not empty
                             */
                            if (!nodeData.toString().isEmpty()) {
                                thisUser.addPermission(nodeData.toString());
                            }

                        }
                    } catch (NullPointerException e) {
                        // Ignore this entry as it's null.
                    }
                    thisUser.sortPermissions();
                }

				// SUBGROUPS NODES
                try {
                    nodeData = thisUserNode.get("subgroups");
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Bad format found in 'subgroups' for user: " + usersKey + " in file: " + usersFile.getPath());
                }

                if (nodeData == null) {
                    /*
                     * If no subgroups node is found, or it's empty do nothing.
                     */
                } else if (nodeData instanceof List) {
                    for (Object o : ((List) nodeData)) {
                        if (o == null) {
                            GroupManager.logger.log(Level.WARNING, "Invalid Subgroup data for user: {0}. Ignoring entry in file: {1}", new Object[]{thisUser.getLastName(), usersFile.getPath()});
                        } else {
                            Group subGrp = ph.getGroup(o.toString());
                            if (subGrp != null) {
                                thisUser.addSubGroup(subGrp);
                            } else {
                                GroupManager.logger.log(Level.WARNING, "Subgroup ''{0}'' not found for user: {1}. Ignoring entry in file: {2}", new Object[]{o.toString(), thisUser.getLastName(), usersFile.getPath()});
                            }
                        }
                    }
                } else if (nodeData instanceof String) {
                    Group subGrp = ph.getGroup(nodeData.toString());
                    if (subGrp != null) {
                        thisUser.addSubGroup(subGrp);
                    } else {
                        GroupManager.logger.log(Level.WARNING, "Subgroup ''{0}'' not found for user: {1}. Ignoring entry in file: {2}", new Object[]{nodeData.toString(), thisUser.getLastName(), usersFile.getPath()});
                    }
                }

				// USER INFO NODE
                try {
                    nodeData = thisUserNode.get("info");
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Bad format found in 'info' section for user: " + usersKey + " in file: " + usersFile.getPath());
                }

                if (nodeData == null) {
                    /*
                     * If no info node is found, or it's empty do nothing.
                     */
                } else if (nodeData instanceof Map) {
                    thisUser.setVariables((Map<String, Object>) nodeData);

                } else {
                    throw new IllegalArgumentException("Unknown entry found in 'info' section for user: " + thisUser.getLastName() + " in file: " + usersFile.getPath());
                }

				// END INFO NODE
				// PRIMARY GROUP
                try {
                    nodeData = thisUserNode.get("group");
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Bad format found in 'group' section for user: " + usersKey + " in file: " + usersFile.getPath());
                }

                if (nodeData != null) {
                    Group hisGroup = ph.getGroup(nodeData.toString());
                    if (hisGroup == null) {
                        GroupManager.logger.log(Level.WARNING, "There is no group {0}, as stated for player {1}: Set to ''{2}'' for file: {3}", new Object[]{thisUserNode.get("group").toString(), thisUser.getLastName(), ph.getDefaultGroup().getName(), usersFile.getPath()});
                        hisGroup = ph.getDefaultGroup();
                    }
                    thisUser.setGroup(hisGroup);
                } else {
                    thisUser.setGroup(ph.getDefaultGroup());
                }
            }
        }

        ph.removeUsersChangedFlag();
        // Update the LastModified time.
        ph.setUsersFile(usersFile);
        ph.setTimeStampUsers(usersFile.lastModified());
    }

    /**
     * Write a dataHolder in a specified file
     *
     * @param ph
     * @param groupsFile
     */
    public static void writeGroups(WorldDataHolder ph, File groupsFile) {

        Map<String, Object> root = new HashMap<>();

        Map<String, Object> groupsMap = new HashMap<>();

        root.put("groups", groupsMap);
        synchronized (ph.getGroups()) {
            ph.getGroups().keySet().stream().map((groupKey) -> ph.getGroups().get(groupKey)).forEach((group) -> {
                Map<String, Object> aGroupMap = new HashMap<>();
                groupsMap.put(group.getName(), aGroupMap);

                if (ph.getDefaultGroup() == null) {
                    GroupManager.logger.log(Level.SEVERE, "There is no default group for world: {0}", ph.getName());
                }
                aGroupMap.put("default", group.equals(ph.getDefaultGroup()));

                Map<String, Object> infoMap = new HashMap<>();
                aGroupMap.put("info", infoMap);

                for (String infoKey : group.getVariables().getVarKeyList()) {
                    infoMap.put(infoKey, group.getVariables().getVarObject(infoKey));
                }

                aGroupMap.put("inheritance", group.getInherits());

                aGroupMap.put("permissions", group.getPermissionList());
            });
        }

        if (!root.isEmpty()) {
            DumperOptions opt = new DumperOptions();
            opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            final Yaml yaml = new Yaml(opt);
            try {
                try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(groupsFile), "UTF-8")) {
                    String newLine = System.getProperty("line.separator");

                    out.write("# Group inheritance" + newLine);
                    out.write("#" + newLine);
                    out.write("# Any inherited groups prefixed with a g: are global groups" + newLine);
                    out.write("# and are inherited from the GlobalGroups.yml." + newLine);
                    out.write("#" + newLine);
                    out.write("# Groups without the g: prefix are groups local to this world" + newLine);
                    out.write("# and are defined in the this groups.yml file." + newLine);
                    out.write("#" + newLine);
                    out.write("# Local group inheritances define your promotion tree when using 'manpromote/mandemote'" + newLine);
                    out.write(newLine);

                    yaml.dump(root, out);
                }
            } catch (IOException ignored) {
            }
        }

        // Update the LastModified time.
        ph.setGroupsFile(groupsFile);
        ph.setTimeStampGroups(groupsFile.lastModified());
        ph.removeGroupsChangedFlag();

        if (GroupManager.isLoaded()) {
            GroupManager.getGMEventHandler().callEvent(GMSystemEvent.Action.SAVED);
        }

        /*
         * FileWriter tx = null; try { tx = new FileWriter(groupsFile, false);
         * tx.write(yaml.dump(root)); tx.flush(); } catch (Exception e) { }
         * finally { try { tx.close(); } catch (IOException ex) { } }
         */
    }

    /**
     * Write a dataHolder in a specified file
     *
     * @param ph
     * @param usersFile
     */
    public static void writeUsers(WorldDataHolder ph, File usersFile) {

        Map<String, Object> root = new HashMap<>();
        LinkedHashMap<String, Object> usersMap = new LinkedHashMap<>();

        root.put("users", usersMap);
        synchronized (ph.getUsers()) {

            // A sorted list of users.
            new TreeSet<>(ph.getUsers().keySet()).stream().map((userKey) -> ph.getUsers().get(userKey)).filter((user) -> !((user.getGroup() == null || user.getGroup().equals(ph.getDefaultGroup())) && user.getPermissionList().isEmpty() && user.getVariables().isEmpty() && user.isSubGroupsEmpty())).forEach((user) -> {
                LinkedHashMap<String, Object> aUserMap = new LinkedHashMap<>();
                usersMap.put(user.getUUID(), aUserMap);
                if (!user.getUUID().equalsIgnoreCase(user.getLastName())) {
                    aUserMap.put("lastname", user.getLastName());
                }
                // GROUP NODE
                if (user.getGroup() == null) {
                    aUserMap.put("group", ph.getDefaultGroup().getName());
                } else {
                    aUserMap.put("group", user.getGroup().getName());
                }
                // SUBGROUPS NODE
                aUserMap.put("subgroups", user.subGroupListStringCopy());
                // PERMISSIONS NODE
                aUserMap.put("permissions", user.getPermissionList());

                // USER INFO NODE - BETA
                if (user.getVariables().getSize() > 0) {
                    Map<String, Object> infoMap = new HashMap<>();
                    aUserMap.put("info", infoMap);
                    for (String infoKey : user.getVariables().getVarKeyList()) {
                        infoMap.put(infoKey, user.getVariables().getVarObject(infoKey));
                    }
                }
                // END USER INFO NODE - BETA
                if (user.getVariables().getSize() > 0) {
                    Map<String, Object> infoMap = new HashMap<>();
                    aUserMap.put("info", infoMap);
                    for (String infoKey : user.getVariables().getVarKeyList()) {
                        infoMap.put(infoKey, user.getVariables().getVarObject(infoKey));
                    }
                }
            });
        }

        if (!root.isEmpty()) {
            DumperOptions opt = new DumperOptions();
            opt.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            final Yaml yaml = new Yaml(opt);
            try {
                try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(usersFile), "UTF-8")) {
                    yaml.dump(root, out);
                }
            } catch (IOException ignored) {
            }
        }

        // Update the LastModified time.
        ph.setUsersFile(usersFile);
        ph.setTimeStampUsers(usersFile.lastModified());
        ph.removeUsersChangedFlag();

        if (GroupManager.isLoaded()) {
            GroupManager.getGMEventHandler().callEvent(GMSystemEvent.Action.SAVED);
        }

        /*
         * FileWriter tx = null; try { tx = new FileWriter(usersFile, false);
         * tx.write(yaml.dump(root)); tx.flush(); } catch (Exception e) { }
         * finally { try { tx.close(); } catch (IOException ex) { } }
         */
    }

    /**
     * Don't use this. Unless you want to make this plugin to interact with
     * original Nijikokun Permissions This method is supposed to make the
     * original one reload the file, and propagate the changes made here.
     *
     * Prefer to use the AnjoCaido's fake version of Nijikokun's Permission
     * plugin. The AnjoCaido's Permission can propagate the changes made on this
     * plugin instantly, without need to save the file.
     *
     * @param server the server that holds the plugin
     * @deprecated it is not used anymore... unless if you use original
     * Permissions
     */
    @Deprecated
    public static void reloadOldPlugins(Server server) {

        // Only reload permissions
        PluginManager pm = server.getPluginManager();
        Plugin[] plugins = pm.getPlugins();
        for (Plugin plugin : plugins) {
            // plugins[i].getConfiguration().load();
            try {
                plugin.getClass().getMethod("setupPermissions").invoke(plugin);
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ignored) {
            }
        }
    }

    /**
     * @return the permissionsHandler
     */
    public AnjoPermissionsHandler getPermissionsHandler() {

        if (permissionsHandler == null) {
            permissionsHandler = new AnjoPermissionsHandler(this);
        }
        return permissionsHandler;
    }

    /**
     * @param haveUsersChanged the haveUsersChanged to set
     */
    public void setUsersChanged(boolean haveUsersChanged) {

        users.setUsersChanged(haveUsersChanged);
    }

    /**
     *
     * @return true if any user data has changed
     */
    public boolean haveUsersChanged() {

        if (users.HaveUsersChanged()) {
            return true;
        }
        synchronized (users.getUsers()) {
            if (users.getUsers().values().stream().anyMatch((u) -> (u.isChanged()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param setGroupsChanged the haveGroupsChanged to set
     */
    public void setGroupsChanged(boolean setGroupsChanged) {

        groups.setGroupsChanged(setGroupsChanged);
    }

    /**
     *
     * @return true if any group data has changed.
     */
    public boolean haveGroupsChanged() {

        if (groups.HaveGroupsChanged()) {
            return true;
        }
        synchronized (groups.getGroups()) {
            if (groups.getGroups().values().stream().anyMatch((g) -> (g.isChanged()))) {
                return true;
            }
        }
        return false;
    }

    /**
     *
     */
    public void removeUsersChangedFlag() {

        setUsersChanged(false);
        synchronized (getUsers()) {
            getUsers().values().forEach(DataUnit::flagAsSaved);
        }
    }

    /**
     *
     */
    public void removeGroupsChangedFlag() {

        setGroupsChanged(false);
        synchronized (getGroups()) {
            getGroups().values().forEach(DataUnit::flagAsSaved);
        }
    }

    /**
     * @return the usersFile
     */
    public File getUsersFile() {

        return users.getUsersFile();
    }

    /**
     * @param file the usersFile to set
     */
    public void setUsersFile(File file) {

        users.setUsersFile(file);
    }

    /**
     * @return the groupsFile
     */
    public File getGroupsFile() {

        return groups.getGroupsFile();
    }

    /**
     * @param file the groupsFile to set
     */
    public void setGroupsFile(File file) {

        groups.setGroupsFile(file);
    }

    /**
     * @return the name
     */
    public String getName() {

        return name;
    }

    /**
     * Resets Groups.
     */
    public void resetGroups() {

        // setDefaultGroup(null);
        groups.resetGroups();
    }

    /**
     * Resets Users
     */
    public void resetUsers() {

        users.resetUsers();
    }

    /**
     * Note: Iteration over this object has to be synchronized!
     *
     * @return the groups
     */
    public Map<String, Group> getGroups() {

        return groups.getGroups();
    }

    /**
     * Note: Iteration over this object has to be synchronized!
     *
     * @return the users
     */
    public Map<String, User> getUsers() {

        return users.getUsers();
    }

    /**
     * @return the groups
     */
    public GroupsDataHolder getGroupsObject() {

        return groups;
    }

    /**
     * @param groupsDataHolder the GroupsDataHolder to set
     */
    public void setGroupsObject(GroupsDataHolder groupsDataHolder) {

        groups = groupsDataHolder;
    }

    /**
     * @return the users
     */
    public UsersDataHolder getUsersObject() {

        return users;
    }

    /**
     * @param usersDataHolder the UsersDataHolder to set
     */
    public void setUsersObject(UsersDataHolder usersDataHolder) {

        users = usersDataHolder;
    }

    /**
     * @return the timeStampGroups
     */
    public long getTimeStampGroups() {

        return groups.getTimeStampGroups();
    }

    /**
     * @return the timeStampUsers
     */
    public long getTimeStampUsers() {

        return users.getTimeStampUsers();
    }

    /**
     * @param timeStampGroups the timeStampGroups to set
     */
    protected void setTimeStampGroups(long timeStampGroups) {

        groups.setTimeStampGroups(timeStampGroups);
    }

    /**
     * @param timeStampUsers the timeStampUsers to set
     */
    protected void setTimeStampUsers(long timeStampUsers) {

        users.setTimeStampUsers(timeStampUsers);
    }

    public void setTimeStamps() {

        if (getGroupsFile() != null) {
            setTimeStampGroups(getGroupsFile().lastModified());
        }
        if (getUsersFile() != null) {
            setTimeStampUsers(getUsersFile().lastModified());
        }
    }

}
