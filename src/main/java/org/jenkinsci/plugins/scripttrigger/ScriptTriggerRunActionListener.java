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

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

import java.io.IOException;

/**
 * @author Gregory Boissinot
 */
@Extension
public class ScriptTriggerRunActionListener extends RunListener<Run> {

    @Override
    public void onStarted(Run run, TaskListener listener) {
        ScriptTriggerRunAction scriptTriggerRunAction = run.getAction(ScriptTriggerRunAction.class);
        if (scriptTriggerRunAction != null) {
            try {
                String description = scriptTriggerRunAction.getDescription();
                if (description != null) {
                    run.setDescription(description);
                }
            } catch (IOException ioe) {
                listener.getLogger().println("[ScriptTrigger] - Error to set the descriptor.");
            }
        }
    }
}
