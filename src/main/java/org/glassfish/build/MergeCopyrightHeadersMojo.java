/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Merges two property files properly.
 */
@Mojo(name = "merge-copyright-headers")
public class MergeCopyrightHeadersMojo extends AbstractMojo {
 
    private static final String LINE_SEP = System.getProperty("line.separator");
    private static final String PROPERTY_PREFIX = "merge.copyright.headers.outputFile";

    /**
     * The merged file.
     */
    @Parameter(property = PROPERTY_PREFIX + "outputFile",
            defaultValue = "${project.build.directory}/merged.properties")
    protected File outputFile;

    /**
     * The files to merge.
     */
    @Parameter(property = PROPERTY_PREFIX + "inputFiles")
    protected File[] inputFiles;

    /**
     * Skip this mojo.
     */
    @Parameter(property = PROPERTY_PREFIX + "skip",
            defaultValue = "false")
    protected Boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if(skip != null && skip){
            getLog().info("Skipping file merge ...");
            return;
        }

        BufferedReader br1=null, br2=null;
        BufferedWriter writer=null;

        try {
            String line;
            StringBuilder sb = new StringBuilder();

            if (inputFiles != null && inputFiles.length > 1) {

                // Get contents of file
                try {
                    File file = inputFiles[0];
                    getLog().info("Reading input file:" + file.getAbsolutePath());
                    br1 = new BufferedReader(new FileReader(file));
                    while ((line = br1.readLine()) != null) {
                        sb.append(line);
                        sb.append(LINE_SEP);
                    }
                } finally {
                    if (br1 != null) {
                        br1.close();
                    }
                }

                for (int i=1; i<inputFiles.length; i++) {
                    File file = inputFiles[i];
                    getLog().info("Reading input file:" + file.getAbsolutePath());
                    // Get contents of input file and skip the comments
                    try {
                        br2 = new BufferedReader(new FileReader(file));
                        while ((line = br2.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("#")) {
                                continue;
                            }
                            sb.append(line);
                            sb.append(LINE_SEP);
                        }
                    } finally {
                        if (br2 != null) {
                            br2.close();
                        }
                    }
                }

                // Initialize the writer and write the merged contents
                try {
                    writer = new BufferedWriter(new FileWriter(outputFile));
                    writer.write(sb.toString());
                    writer.flush();
                } finally {
                    if (writer != null) {
                        writer.close();
                    }
                }
            }
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        } 
    }
}
