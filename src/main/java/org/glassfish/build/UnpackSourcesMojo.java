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
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import org.codehaus.plexus.archiver.manager.ArchiverManager;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;

import static org.glassfish.build.utils.MavenUtils.cleanToBeTokenizedString;
import static org.glassfish.build.utils.MavenUtils.filterArtifacts;
import static org.glassfish.build.utils.MavenUtils.resolveArtifact;
import static org.glassfish.build.utils.MavenUtils.unpack;

/**
 * Resolves and unpack corresponding sources of project dependencies.
 *
 * @author Romain Grecourt
 */
@Mojo(name = "unpack-sources",
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresProject = true)
public class UnpackSourcesMojo extends AbstractMojo {

    private static final String PROPERTY_PREFIX = "gfbuild.unpack";

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The entry point to Aether.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}",
            readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * The project remote repositories to use.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;

    /**
     * To look up Archiver/UnArchiver implementations.
     */
    @Component
    protected ArchiverManager archiverManager;

    /**
     * Comma separated list of include patterns.
     */
    @Parameter(property = PROPERTY_PREFIX + "includes")
    private String includes;

    /**
     * Comma separated list of include patterns.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludes")
    private String excludes;

    /**
     * If we should exclude transitive dependencies.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeTransitive",
            defaultValue = "",
            required = false)
    protected boolean excludeTransitive;

    /**
     * Comma Separated list of Types to include.
     * Empty String indicates include everything (default).
     */
    @Parameter(property = PROPERTY_PREFIX + "includeTypes",
            defaultValue = "",
            required = false)
    protected String includeTypes;

    /**
     * Comma Separated list of Types to exclude.
     * Empty String indicates don't exclude anything (default).
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeTypes",
            defaultValue = "",
            required = false)
    protected String excludeTypes;

    /**
     * Scope to include.
     * An Empty string indicates all scopes (default).
     */
    @Parameter(property = PROPERTY_PREFIX + "includeScope",
            defaultValue = "",
            required = false)
    protected String includeScope;

    /**
     * Scope to exclude.
     * An Empty string indicates no scopes (default).
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeScope",
            defaultValue = "",
            required = false)
    protected String excludeScope;

    /**
     * Comma Separated list of Classifiers to include.
     * Empty String indicates include everything (default).
     */
    @Parameter(property = PROPERTY_PREFIX + "includeClassifiers",
            defaultValue = "",
            required = false)
    protected String includeClassifiers;

    /**
     * Comma Separated list of Classifiers to exclude.
     * Empty String indicates don't exclude anything (default).
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeClassifiers",
            defaultValue = "",
            required = false)
    protected String excludeClassifiers;

    /**
     * Comma separated list of Artifact names to exclude.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeArtifactIds",
            defaultValue = "",
            required = false)
    protected String excludeArtifactIds;

    /**
     * Comma separated list of Artifact names to include.
     */
    @Parameter(property = PROPERTY_PREFIX + "includeArtifactIds",
            defaultValue = "")
    protected String includeArtifactIds;

    /**
     * Comma separated list of GroupId Names to exclude.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeGroupIds",
            defaultValue = "")
    protected String excludeGroupIds;

    /**
     * Comma separated list of GroupIds to include.
     */
    @Parameter(property = PROPERTY_PREFIX + "includeGroupIds",
            defaultValue = "")
    protected String includeGroupIds;

    /**
     * Directory where the sources artifacts are unpacked.
     */
    @Parameter(property = PROPERTY_PREFIX + "outputDirectory",
            defaultValue = "${project.build.directory}/sources-dependency")
    private File outputDirectory;

    /**
     * Verbosity.
     */
    @Parameter(property = PROPERTY_PREFIX + "silent",
            defaultValue = "false")
    private boolean silent;

    /**
     * Attach the generated artifact to the maven project.
     */
    @Parameter(property = PROPERTY_PREFIX + "attach-sources",
            defaultValue = "false")
    private boolean attachSources;

    /**
     * Skip this mojo.
     */
    @Parameter(property = PROPERTY_PREFIX + "skip",
            defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping unpack-sources");
            return;
        }

        // get dependencies
        Set<Artifact> filteredDependencies = filterArtifacts(
                project.getArtifacts(), project.getDependencyArtifacts(),
                excludeTransitive, includeScope, excludeScope, excludeTypes,
                includeTypes, includeClassifiers, excludeClassifiers,
                includeGroupIds, excludeGroupIds, includeArtifactIds,
                excludeArtifactIds);

        for (Artifact artifact : filteredDependencies) {

            // resolve sources.jar
            ArtifactResult result = resolveArtifact(artifact.getGroupId(),
                    artifact.getArtifactId(), /* classifier */ "sources",
                    /* type */ "jar", artifact.getVersion(), repoSystem,
                    repoSession, remoteRepos);

            // unpack
            unpack(result.getArtifact().getFile(), outputDirectory,
                    cleanToBeTokenizedString(this.includes),
                    cleanToBeTokenizedString(this.excludes), silent, getLog(),
                    archiverManager);
        }

        if (attachSources) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        }
    }
}
