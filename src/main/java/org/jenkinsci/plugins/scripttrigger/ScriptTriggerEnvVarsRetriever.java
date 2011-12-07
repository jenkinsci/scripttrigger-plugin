package org.jenkinsci.plugins.scripttrigger;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Run;
import hudson.remoting.Callable;
import org.jenkinsci.plugins.envinject.EnvInjectAction;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

/**
 * @author Gregory Boissinot
 */
public class ScriptTriggerEnvVarsRetriever implements Serializable {

    public Map<String, String> getEnvVars(AbstractProject project, Node node, ScriptTriggerLog log) throws ScriptTriggerException {
        Run lastBuild = project.getLastBuild();
        if (lastBuild != null) {
            EnvInjectAction envInjectAction = lastBuild.getAction(EnvInjectAction.class);
            if (envInjectAction != null) {
                return envInjectAction.getEnvMap();
            }
        }
        return getDefaultEnvVarsJob(project, node);
    }

    private Map<String, String> getDefaultEnvVarsJob(AbstractProject project, Node node) throws ScriptTriggerException {
        Map<String, String> result = computeEnvVarsMaster(project);
        if (node != null) {
            result.putAll(computeEnvVarsNode(project, node));
        }
        return result;
    }

    private Map<String, String> computeEnvVarsMaster(AbstractProject project) throws ScriptTriggerException {
        EnvVars env = new EnvVars();
        env.put("JENKINS_SERVER_COOKIE", Util.getDigestOf("ServerID:" + Hudson.getInstance().getSecretKey()));
        env.put("HUDSON_SERVER_COOKIE", Util.getDigestOf("ServerID:" + Hudson.getInstance().getSecretKey())); // Legacy compatibility
        env.put("JOB_NAME", project.getFullName());
        String rootUrl = Hudson.getInstance().getRootUrl();
        if (rootUrl != null) {
            env.put("JENKINS_URL", rootUrl);
            env.put("HUDSON_URL", rootUrl);
            env.put("JOB_URL", rootUrl + project.getUrl());
        }
        env.put("JENKINS_HOME", Hudson.getInstance().getRootDir().getPath());
        env.put("HUDSON_HOME", Hudson.getInstance().getRootDir().getPath());   // legacy compatibility

        return env;
    }

    private Map<String, String> computeEnvVarsNode(AbstractProject project, Node node) throws ScriptTriggerException {
        assert node != null;
        assert node.getRootPath() != null;
        try {
            Map<String, String> envVars = node.getRootPath().act(new Callable<Map<String, String>, ScriptTriggerException>() {
                public Map<String, String> call() throws ScriptTriggerException {
                    return EnvVars.masterEnvVars;
                }
            });

            envVars.put("NODE_NAME", node.getNodeName());
            envVars.put("NODE_LABELS", Util.join(node.getAssignedLabels(), " "));
            FilePath wFilePath = project.getSomeWorkspace();
            if (wFilePath != null) {
                envVars.put("WORKSPACE", wFilePath.getRemote());
            }

            return envVars;

        } catch (IOException ioe) {
            throw new ScriptTriggerException(ioe);
        } catch (InterruptedException ie) {
            throw new ScriptTriggerException(ie);
        }
    }
}
