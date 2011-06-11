package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.FilePath;
import hudson.model.*;
import hudson.triggers.Trigger;

import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;

/**
 * @author Gregory Boissinot
 */
public abstract class AbstractTrigger extends Trigger<BuildableItem> implements Serializable {

    public AbstractTrigger(String cronTabSpec) throws ANTLRException {
        super(cronTabSpec);
    }

    public abstract File getLogFile();

    protected FilePath getOneRootNode() {
        AbstractProject p = (AbstractProject) job;
        Label label = p.getAssignedLabel();
        if (label == null) {
            return Hudson.getInstance().getRootPath();
        } else {
            Set<Node> nodes = label.getNodes();
            Node node;
            for (Iterator<Node> it = nodes.iterator(); it.hasNext();) {
                node = it.next();
                FilePath nodePath = node.getRootPath();
                if (nodePath != null) {
                    return nodePath;
                }
            }
            return null;
        }
    }

}
