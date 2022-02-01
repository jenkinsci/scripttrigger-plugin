package org.jenkinsci.plugins.scripttrigger.groovy;

import antlr.ANTLRException;
import hudson.scheduler.CronTabList;
import org.apache.commons.lang.StringUtils;

import java.util.Calendar;
import java.util.Properties;

public class ParameterParser {

    private static final String PARAMETER_ANNOTATION = "@param";

    public Properties findParameters(String cronTabSpec, Calendar calendar) throws ANTLRException {
        Properties parameters = new Properties();
        for (String line : cronTabSpec.split(System.lineSeparator())) {
            if (line.length() == 0) {
                continue;
            }
            if (line.startsWith("#")) {
                if (line.substring(1).startsWith(PARAMETER_ANNOTATION)) {
                    parameters.putAll(parseParameters(line.substring(1 + PARAMETER_ANNOTATION.length())));
                }
            } else {
                if (CronTabList.create(line).check(calendar)) {
                    break;
                }
                parameters.clear();
            }
        }
        return parameters;
    }

    private Properties parseParameters(String parameterStr) {
        Properties parameters = new Properties();
        if (StringUtils.isBlank(parameterStr)) {
            return parameters;
        }
        parameterStr = parameterStr.trim();
        int separatorInd = parameterStr.indexOf(' ');
        if (separatorInd < 0) {
            parameters.setProperty(parameterStr, "");
        } else {
            parameters.setProperty(parameterStr.substring(0, separatorInd), parameterStr.substring(separatorInd).trim());
        }
        return parameters;
    }
}
