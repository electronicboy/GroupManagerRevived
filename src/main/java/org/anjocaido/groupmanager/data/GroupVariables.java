/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.anjocaido.groupmanager.data;

import java.util.Map;

/**
 *
 * @author gabrielcouto
 */
public class GroupVariables extends Variables implements Cloneable {

    private Group owner;

    public GroupVariables(Group owner) {

        super(owner);
        this.owner = owner;
        addVar("prefix", "");
        addVar("suffix", "");
        addVar("build", false);
    }

    public GroupVariables(Group owner, Map<String, Object> varList) {

        super(owner);
        variables.clear();
        variables.putAll(varList);
        if (variables.get("prefix") == null) {
            variables.put("prefix", "");
            owner.flagAsChanged();
        }
        //thisGrp.prefix = infoNode.get("prefix").toString();

        if (variables.get("suffix") == null) {
            variables.put("suffix", "");
            owner.flagAsChanged();
        }
        //thisGrp.suffix = infoNode.get("suffix").toString();

        if (variables.get("build") == null) {
            variables.put("build", false);
            owner.flagAsChanged();
        }
        this.owner = owner;
    }

    /**
     * A clone of all vars here.
     *
     * @param newOwner
     * @return GroupVariables clone
     */
    protected GroupVariables clone(Group newOwner) {

        GroupVariables clone = new GroupVariables(newOwner);
        synchronized (variables) {
            variables.keySet().forEach((key) -> clone.variables.put(key, variables.get(key)));
        }
        newOwner.flagAsChanged();
        return clone;
    }

    /**
     * Remove a var from the list
     *
     * @param name
     */
    @Override
    public void removeVar(String name) {

        if (this.variables.containsKey(name))
            this.variables.remove(name);

        switch (name) {
            case "prefix":
                addVar("prefix", "");
                break;
            case "suffix":
                addVar("suffix", "");
                break;
            case "build":
                addVar("build", false);
                break;
        }
        owner.flagAsChanged();
    }

    /**
     * @return the owner
     */
    @Override
    public Group getOwner() {

        return owner;
    }
}
