/**
 * The MIT License
 * Copyright (c) 2015 Gregory Boissinot and all contributors
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.scripttrigger;

import antlr.ANTLRException;
import com.google.common.base.Charsets;
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
            String scriptContent = Util.loadFile(getLogFile(), Charsets.UTF_8);
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
            scriptContent = Util.loadFile(getLogFile(), Charsets.UTF_8);
        } catch (IOException e) {
            return new Action[0];
        }

        List<Action> actionList = new ArrayList<>();
        String description = extractDescription(scriptContent);
        if (description != null) {
            actionList.add(new ScriptTriggerRunAction(description));
        }
        return actionList.toArray(new Action[actionList.size()]);
    }


    /**
     * Extracts the latest description value between the <description></description> section.
     * @param content the script log trigger plugin current log
     * @return the latest description found or null if any
     */
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
