package org.jenkinsci.plugins.scripttrigger;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public class LabelRestrictionClass {

    private String triggerLabel;

    @DataBoundConstructor
    public LabelRestrictionClass(String triggerLabel) {
        this.triggerLabel = triggerLabel;
    }

    public String getTriggerLabel() {
        return triggerLabel;
    }
}
