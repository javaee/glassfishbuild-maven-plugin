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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import org.apache.tools.ant.types.ZipFileSet;

import static org.glassfish.build.utils.MavenHelper.createZip;
import static org.glassfish.build.utils.MavenHelper.createZipFileSet;

/**
 * Creates a zip file.
 */
@Mojo(name = "zip",
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresProject = true)
public class ZipMojo extends AbstractMojo {

    private static final String PROPERTY_PREFIX = "gfzip.outputDirectory";

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The directory where the zip will be created.
     */
    @Parameter(property = PROPERTY_PREFIX + "outputDirectory",
            defaultValue = "${project.build.directory}")
    protected File outputDirectory;

    /**
     * The file name of the created zip.
     */
    @Parameter(property = PROPERTY_PREFIX + "finalName",
            defaultValue = "${project.build.finalName}")
    protected String finalName;

    /**
     * behavior when a duplicate file is found.
     * Valid values are "add", "preserve", and "fail" ; default value is "add"
     */
    @Parameter(property = PROPERTY_PREFIX + "duplicate",
            defaultValue = "add")
    protected String duplicate;

    /**
     * Content to include in the zip.
     */
    @Parameter(property = PROPERTY_PREFIX + "filesets")
    protected ZipFileSet[] filesets;

    /**
     * The root directory of the default FileSet.
     * Only when no fileset(s) provided.
     */
    @Parameter(property = PROPERTY_PREFIX + "dir",
            defaultValue = "${project.build.directory}")
    protected File dir;

    /**
     * Comma- or space-separated list of patterns of files that must be included.
     * all files are included when omitted ; Only when no fileset(s) provided.
     */
    @Parameter(property = PROPERTY_PREFIX + "includes")
    protected String includes;

    /**
     * Comma- or space-separated list of patterns of files that must be included.
     * all files are included when omitted ; Only when no fileset(s) provided.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludes")
    protected String excludes;
    
    /**
     * The extension of the generated file.
     */
    @Parameter(property = PROPERTY_PREFIX + "extension",
            defaultValue = "zip")
    protected String extension;
    
    /**
     * Attach the produced artifact.
     */
    @Parameter(property = PROPERTY_PREFIX + "attach",
            defaultValue = "true")
    protected Boolean attach;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        this.project.addCompileSourceRoot(null);
        List<ZipFileSet> fsets;
        if(filesets != null && filesets.length >0){
            fsets = Arrays.asList(filesets);
        } else {
            fsets = new ArrayList<ZipFileSet>();
            fsets.add(createZipFileSet(dir, includes, excludes));
        }

        File target = createZip(project.getProperties(), getLog(),
                duplicate, fsets, new File(outputDirectory, finalName + '.' + extension));

        if (attach) {
            project.getArtifact().setFile(target);
            project.getArtifact().setArtifactHandler(
                    new DistributionArtifactHandler(extension, project.getPackaging()));
        }
    }

    private static class DistributionArtifactHandler implements ArtifactHandler {

        private final String extension;
        private final String packaging;

        public DistributionArtifactHandler() {
            extension = "zip";
            packaging = "glassfish-distribution";
        }

        public DistributionArtifactHandler(String extension, String packaging) {
            this.extension = extension;
            this.packaging = packaging;
        }

        @Override
        public String getExtension() {
            return extension;
        }

        @Override
        public String getDirectory() {
            return null;
        }

        @Override
        public String getClassifier() {
            return null;
        }

        @Override
        public String getPackaging() {
            return packaging;
        }

        @Override
        public boolean isIncludesDependencies() {
            return false;
        }

        @Override
        public String getLanguage() {
            return "java";
        }

        @Override
        public boolean isAddedToClasspath() {
            return false;
        }
    }
}
