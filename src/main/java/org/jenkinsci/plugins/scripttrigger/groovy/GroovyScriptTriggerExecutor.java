package org.jenkinsci.plugins.scripttrigger.groovy;

import groovy.lang.GroovyShell;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerExecutor;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerLog;

import java.io.IOException;

/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTriggerExecutor extends ScriptTriggerExecutor {

    public GroovyScriptTriggerExecutor(FilePath executionNodeRootPath, TaskListener listener, ScriptTriggerLog log) {
        super(executionNodeRootPath, listener, log);
    }

    public boolean evaluateGroovyScript(final String scriptContent) throws ScriptTriggerException {

        if (scriptContent == null) {
            throw new NullPointerException("The script content object must be set.");
        }
        try {
            return executionNodeRootPath.act(new Callable<Boolean, ScriptTriggerException>() {
                public Boolean call() throws ScriptTriggerException {
                    final String groovyExpressionResolved = Util.replaceMacro(scriptContent, EnvVars.masterEnvVars);
                    log.info(String.format("Evaluating the groovy script: \n %s", scriptContent));
                    GroovyShell shell = new GroovyShell();
                    //Evaluate the new script content
                    Object result = shell.evaluate(groovyExpressionResolved);
                    //Return the evaluated result
                    return Boolean.valueOf(String.valueOf(result));
                }
            });
        } catch (IOException ioe) {
            throw new ScriptTriggerException(ioe);
        } catch (InterruptedException ie) {
            throw new ScriptTriggerException(ie);
        }
    }

    public boolean evaluateGroovyScriptFilePath(final String scriptFilePath) throws ScriptTriggerException {
        if (scriptFilePath == null) {
            throw new NullPointerException("The scriptFilePath object must be set.");
        }

        if (!existsScript(scriptFilePath)) {
            return false;
        }

        String scriptContent = getStringContent(scriptFilePath);
        return evaluateGroovyScript(scriptContent);
    }

}
