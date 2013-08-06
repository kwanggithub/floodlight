package org.projectfloodlight.os;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.projectfloodlight.os.WrapperOutput.Status;

import com.google.common.base.Joiner;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Some useful utilities for writing configlets
 * @author readams
 */
public class ConfigletUtil {
    public static final String RELEASE = "/etc/floodlight-release";
    private static boolean dryRun = false;
    
    /**
     * Run the specified command, returning a  {@link Status.SUBPROCESS_ERROR}
     * if it exits with nonzero status
     * @param command the command and its arguments
     * @return  a {@link WrapperOutput} with the result of running the command
     */
    public static WrapperOutput run(String... command) {
        return runWithPipe(true, null, null, command);
    }
    /**
     * Run the given command in a subprocess and capture the output.  If
     * the command exits with a nonzero status, it will return an error output.
     * If fatal is false, the error will be a {@link Status.NONFATAL_ERROR},
     * otherwise a {@link Status.SUBPROCESS_ERROR}
     * @param fatal whether to return a fatal error
     * @param command the command and its arguments
     * @return a {@link WrapperOutput} with the result of running the command
     */
    public static WrapperOutput run(boolean fatal, String... command) {
        return runWithPipe(fatal, null, null, command);
    }

    /**
     * Run the given command in a subprocess and capture the output.  If
     * the command exits with a nonzero status, it will return an error output.
     * If fatal is false, the error will be a {@link Status.NONFATAL_ERROR},
     * otherwise a {@link Status.SUBPROCESS_ERROR}
     * @param fatal whether to return a fatal error
     * @param stdin a string to pass as standard input to the command
     * @param command the command and its arguments
     * @return a {@link WrapperOutput} with the result of running the command
     */
    public static WrapperOutput runWithPipe(boolean fatal, String stdin,
                                            String successMessage, String... command) {
        String action = "Executing " + Joiner.on(' ').join(command);
        if (dryRun) {
            return WrapperOutput.success(action, successMessage);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            Process p = pb.start();
            if (stdin != null) {
                try (OutputStreamWriter in =
                        new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) {
                    in.write(stdin);
                }
            }
            String err = CharStreams.
                    toString(new InputStreamReader(p.getErrorStream(), 
                                                   StandardCharsets.UTF_8));
            String out = CharStreams.
                    toString(new InputStreamReader(p.getInputStream(), 
                                                   StandardCharsets.UTF_8));
            int status = p.waitFor();
            if (status != 0) {
                StringBuilder full = new StringBuilder();
                boolean s = false;
                if (!"".equals(out)) {
                    s = true;
                    full.append("Standard Out:\n");
                    full.append(out);
                }
                if (!"".equals(err)) {
                    if (s) full.append("\n\n");
                    full.append("Standard Error:\n");
                    full.append(err);
                }
                return WrapperOutput.error(fatal ? 
                                                  Status.SUBPROCESS_ERROR : 
                                                  Status.NONFATAL_ERROR, 
                                           action,
                                           "Exited with status " + status,
                                           full.toString());

            }
            return WrapperOutput.success(action, successMessage);
        } catch (IOException e) {
            return WrapperOutput.error(Status.SUBPROCESS_ERROR, action, e);
        } catch (InterruptedException e) {
            return WrapperOutput.error(Status.SUBPROCESS_ERROR, action, e);
        }
    }

    /**
     * Write the specified file contents to the given file.  It will first
     * write to a temporary file then move the tempory file to the given 
     * location.
     * @param basePath the base path 
     * @param path the path of the file relative to the base path
     * @param fileContents the file contents to write
     * @return a {@link WrapperOutput} indicating the status of the operation
     */
    public static WrapperOutput writeConfigFile(File basePath, String path,
                                                String fileContents) {
        File fpath = new File(basePath, path);
        String action = "Write to " + fpath.getAbsolutePath();
        return writeConfigFile(basePath, path, fpath, fileContents, action);
    }
    
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    private static WrapperOutput writeConfigFile(File basePath, String path,
                                                File fpath,
                                                String fileContents,
                                                String action) {
        try {
            File tmp = new File(basePath, path + ".tmp");
            tmp.getParentFile().mkdirs();
            try (BufferedWriter fw = 
                    Files.newWriter(tmp, StandardCharsets.UTF_8)) {
                fw.write(fileContents);
            }
            if (!tmp.renameTo(fpath))
                throw new IOException("Could not rename to " + fpath);
        } catch (IOException e) {
            return WrapperOutput.error(Status.IO_ERROR, action, e);
        }
        return WrapperOutput.success(action);
    }
   
    /**
     * Write to a config file based on an existing template compiled 
     * as a resource
     * @param basePath the base path
     * @param templatePath the path for the template resource
     * @param destPath the destination path for the config file
     * @param vars a map of substitutions
     * @return a {@link WrapperOutput} indicating the status of the operation
     */
    public static WrapperOutput writeTemplate(File basePath, 
                                              String templatePath,
                                              String destPath,
                                              Map<String,String> vars) {
        File fpath = new File(basePath, destPath);
        String action = "Write template " + templatePath + 
                        " to " + fpath.getAbsolutePath();
        try {
            String fileContents = templateSubst(templatePath, vars);
            return writeConfigFile(basePath, destPath, fpath, 
                                   fileContents, action);
        } catch (IOException e) {
            return WrapperOutput.error(Status.IO_ERROR, action, e);
        }
    }
    
    /**
     * Substitute the given variables in the provided compiled template
     * resource 
     * @param templatePath the path to the template resource
     * @param vars the variables to substitute in the template
     * @return the string containing the template
     * @throws IOException
     */
    public static String templateSubst(String templatePath, 
                                       Map<String,String> vars) 
                                               throws IOException {
        InputStream is = 
                ConfigletUtil.class.getClassLoader().
                getResourceAsStream(templatePath);
        StringBuilder template = new StringBuilder();
        try (BufferedReader br = 
                new BufferedReader(new InputStreamReader(is, 
                                                         StandardCharsets.UTF_8))) {
            String line;
            while (null != (line = br.readLine())) {
                template.append(line);
                template.append('\n');
            }            
        }
        for (Entry<String,String> e : vars.entrySet()) {
            int i;
            while ((i = template.indexOf(e.getKey())) >= 0) {
                template.replace(i, i+e.getKey().length(), e.getValue());
            }
        }
        return template.toString();
    }
    
    public static final Pattern ETC_DEFAULT_PATTERN = 
            Pattern.compile("(\\w+)=(.*)"); 
    
    /**
     * Edit a defaults file in /etc/defaults that defines a sequence of 
     * key/value variables.  Any variables that appear in the vars
     * section will have their values substituted into the defaults file, with
     * proper quoting
     * @param basePath the base path
     * @param path the path of the file to modify
     * @param vars variables value to be substituted.
     * @return a {@link WrapperOutput} indicating the status of the operation
     */
    public static WrapperOutput editDefaults(File basePath,
                                             String path,
                                             Map<String,String> vars) {
        String action="Edit defaults file " + path;
        File dfile = new File(basePath, path);
        StringBuilder sb = new StringBuilder();
        try {
            try (BufferedReader br = 
                    Files.newReader(dfile, StandardCharsets.UTF_8)) {
                String line;
                while (null != (line = br.readLine())) {
                    Matcher m = ETC_DEFAULT_PATTERN.matcher(line);
                    if (m.matches() && vars.containsKey(m.group(1))) {
                        sb.append(m.group(1));
                        sb.append("='");
                        sb.append(vars.get(m.group(1)).replace("'", "'\\''"));
                        sb.append("'\n");
                    } else {
                        sb.append(line);
                        sb.append('\n');
                    }
                }
            } 
            
            return writeConfigFile(basePath, path, dfile, 
                                   sb.toString(), action);
        } catch (FileNotFoundException e) {
            for (Entry<String,String> entry : vars.entrySet()) {
                sb.append(entry.getKey());
                sb.append("='");
                sb.append(entry.getValue().replace("'", "'\\''"));
                sb.append("'\n");
            }
            return writeConfigFile(basePath, path, dfile, 
                    sb.toString(), action);
        } catch (IOException e) {
            return WrapperOutput.error(Status.IO_ERROR, action, e);
        }  
    }
    
    /**
     * Add a warning not to edit a generated file, preceded by the provided
     * comment start string
     * @param buffer the stringbuffer to write to
     * @param commentStr
     */
    public static void addEditWarning(StringBuilder buffer, String commentStr) {
        buffer.append(commentStr);
        buffer.append(" WARNING: Automanaged file.  Do not edit\n\n");
    }
    
    public static String getVersionStr(File basePath) {
        File release = new File(basePath, RELEASE);
        try {
            return Files.readFirstLine(release, Charset.defaultCharset());
        } catch (IOException e) {
            return "Version Unknown";
        }
    }
    
    public static void setDryRun() {
        dryRun = true;
    }
}
