package org.jenkinsci.plugins.scripttrigger.groovy;

import groovy.lang.GroovyShell;
import hudson.EnvVars;
import hudson.Util;
import hudson.model.Node;
import hudson.remoting.Callable;
import org.jenkinsci.lib.xtrigger.XTriggerLog;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerException;
import org.jenkinsci.plugins.scripttrigger.ScriptTriggerExecutor;

import java.io.IOException;
import java.util.Map;

/**
 * @author Gregory Boissinot
 */
public class GroovyScriptTriggerExecutor extends ScriptTriggerExecutor {

    public GroovyScriptTriggerExecutor(XTriggerLog log) {
        super(log);
    }

    public boolean evaluateGroovyScript(Node executingNode, final String scriptContent, final Map<String, String> envVars) throws ScriptTriggerException {

        if (scriptContent == null) {
            throw new NullPointerException("The script content object must be set.");
        }
        try {
            return executingNode.getRootPath().act(new Callable<Boolean, ScriptTriggerException>() {
                public Boolean call() throws ScriptTriggerException {
                    log.info("Replacing script vars with: " + envVars);
                    final String groovyExpressionResolved = Util.replaceMacro(scriptContent, envVars);
                    log.info(String.format("Evaluating the groovy script: \n %s", groovyExpressionResolved));
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

    public boolean evaluateGroovyScriptFilePath(Node executingNode, String scriptFilePath, final Map<String, String> envVars) throws ScriptTriggerException {
        if (scriptFilePath == null) {
            throw new NullPointerException("The scriptFilePath object must be set.");
        }

        if (!existsScript(executingNode, scriptFilePath)) {
            return false;
        }

        String scriptContent = getStringContent(executingNode, scriptFilePath);
        return evaluateGroovyScript(executingNode, scriptContent, envVars);
    }

}
