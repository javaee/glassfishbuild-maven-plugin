/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2018 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.build;

import java.io.File;
import java.util.Iterator;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.taskdefs.condition.Os;

/**
 * Execute a command.
 */
@Mojo(name = "exec",
      requiresProject = true,
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public final class ExecMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Executable to execute.
     */
    @Parameter(property = "executable")
    private String executable;

    /**
     * Working dir.
     */
    @Parameter(property = "workingDir",
            defaultValue = "${project.build.directory}")
    private File workingDir;

    /**
     * Command line argument.
     */
    @Parameter(property = "commandlineArgs")
    private String commandlineArgs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        Project antProject = new Project();
        antProject.addBuildListener(new AntBuildListener());

        Properties mavenProperties = project.getProperties();
        Iterator it = mavenProperties.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
            antProject.setProperty(key, mavenProperties.getProperty(key));
        }
        ExecTask exec = new ExecTask();
        exec.setProject(antProject);
        exec.setDir(workingDir);

        if (new Os("Windows").eval()
                && !executable.endsWith(".bat")
                && new File(executable + ".bat").exists()) {
            executable += ".bat";
        }

        exec.setExecutable(executable);
        getLog().info("executable: " + executable);
        exec.createArg().setLine(commandlineArgs);
        getLog().info("commandLineArgs: " + commandlineArgs);
        exec.execute();
    }

    /**
     * {@code BuilderListener} implementation to log Ant events.
     */
    private class AntBuildListener implements BuildListener {

        /**
         * Maximum Event priority that is logged.
         */
        private static final int MAX_EVENT_PRIORITY = 3;

        @Override
        public void buildStarted(final BuildEvent event) {
        }

        @Override
        public void buildFinished(final BuildEvent event) {
        }

        @Override
        public void targetStarted(final BuildEvent event) {
        }

        @Override
        public void targetFinished(final BuildEvent event) {
        }

        @Override
        public void taskStarted(final BuildEvent event) {
        }

        @Override
        public void taskFinished(final BuildEvent event) {
        }

        @Override
        public void messageLogged(final BuildEvent event) {
            if (event.getPriority() < MAX_EVENT_PRIORITY) {
                getLog().info("[exec] " + event.getMessage());
            } else {
                getLog().debug("[exec] " + event.getMessage());
            }
        }
    }
}
