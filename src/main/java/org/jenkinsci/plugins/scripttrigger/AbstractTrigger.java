package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import hudson.Util;
import hudson.model.Action;
import hudson.model.BuildableItem;
import hudson.model.Node;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.xtrigger.XTriggerLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Gregory Boissinot
 */
public abstract class AbstractTrigger extends org.jenkinsci.lib.xtrigger.AbstractTrigger {

    protected boolean labelRestriction;

    protected boolean enableConcurrentBuild;

    public AbstractTrigger(String cronTabSpec, boolean labelRestriction, String triggerLabel, boolean enableConcurrentBuild) throws ANTLRException {
        super(cronTabSpec, triggerLabel, enableConcurrentBuild);
        this.labelRestriction = labelRestriction;
        this.enableConcurrentBuild = enableConcurrentBuild;
    }

    @SuppressWarnings("unused")
    public boolean isEnableConcurrentBuild() {
        return enableConcurrentBuild;
    }

    @SuppressWarnings("unused")
    public boolean isLabelRestriction() {
        return labelRestriction;
    }

    @Override
    protected void start(Node pollingNode, BuildableItem project, boolean newInstance, XTriggerLog log) {
    }

    @Override
    protected String getName() {
        return "ScriptTrigger";
    }

    @Override
    protected String getCause() {
        try {
            String scriptContent = Util.loadFile(getLogFile());
            String cause = extractRootCause(scriptContent);
            if (cause == null) {
                return getDefaultMessageCause();
            }
            return cause;
        } catch (IOException e) {
            return getDefaultMessageCause();
        }
    }

    protected abstract String getDefaultMessageCause();

    private String extractRootCause(String content) {
        return StringUtils.substringBetween(content, "<cause>", "</cause>");
    }

    @Override
    protected Action[] getScheduledActions(Node pollingNode, XTriggerLog log) {
        String scriptContent;
        try {
            scriptContent = Util.loadFile(getLogFile());
        } catch (IOException e) {
            return new Action[0];
        }

        List<Action> actionList = new ArrayList<Action>();
        String description = extractDescription(scriptContent);
        if (description != null) {
            actionList.add(new ScriptTriggerRunAction(description));
        }
        return actionList.toArray(new Action[actionList.size()]);
    }

    private String extractDescription(String content) {
        String [] des =  StringUtils.substringsBetween(content, "<description>", "</description>");
        if (des != null && des.length >=1 ) {
            return des[des.length - 1];
        }
        return null;
    }

    protected boolean requiresWorkspaceForPolling() {
        return false;
    }

}
