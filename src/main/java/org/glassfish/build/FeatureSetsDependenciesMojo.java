/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.glassfish.build.utils.MavenUtils;

/**
 * Resolves and unpack corresponding sources of project's dependencies
 *
 * @goal featuresets-dependencies
 * @requiresDependencyResolution compile
 * @phase process-resources
 * @requiresProject
 *
 * @author Romain Grecourt
 */
public class FeatureSetsDependenciesMojo extends AbstractMojo {
    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    private RepositorySystem repoSystem;
    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;
    /**
     * The project's remote repositories to use for the resolution of plugins
     * and their dependencies.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;
    /**
     * To look up Archiver/UnArchiver implementations
     *
     * @component
     */
    protected ArchiverManager archiverManager;
    /**
     * The maven project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;
    /**
     * The directory where the files will be copied
     *
     * @parameter expression="${gfbuild.featuresets.dependencies.stageDirectory}" default-value="${project.build.directory}/stage"
     */
    protected File stageDirectory;
    /**
     * Comma separated list of file extensions to include for copy
     *
     * @parameter expression="${gfbuild.featuresets.dependencies.copyTypes}" default-value="jar,war,rar"
     */
    protected String copyTypes;
    /**
     * Comma separated list of file extensions to include for unpack
     *
     * @parameter expression="${gfbuild.featuresets.dependencies.unpackTypes}" default-value="zip"
     */
    protected String unpackTypes;
    /**
     * @parameter expression="${gfbuild.featuresets.dependencies.includes}"
     */
    private String includes;
    /**
     * @parameter expression="${gfbuild.featuresets.dependencies.excludes}"
     */
    private String excludes;
    /**
     * Scope to include. An Empty string indicates all scopes.
     *
     * @parameter expression="${gfbuild.featuresets.dependencies.includeScope}" default-value="compile"
     * @optional
     */
    protected String includeScope;
    /**
     * Scope to exclude. An Empty string indicates no scopes.
     *
     * @parameter expression="${gfbuild.featuresets.dependencies.excludeScope}" default-value="test,system"
     * @optional
     */
    protected String excludeScope;
    /**
     * The groupId of the feature sets to include.
     *
     * @parameter expression="${gfbuild.featuresets.dependencies.featureset.groupid.includes}" default-value=""
     */
    protected String featureSetGroupIdIncludes;
    /**
     * @parameter 
     * expression="${gfbuild.featuresets.dependencies.silent}"
     * default-value="true"
     */
    private boolean silent;

    /**
     * @parameter 
     * expression="${gfbuild.featuresets.dependencies.skip}"
     * default-value="false"
     */    
    private boolean skip;

    private static List<String> stringAsList(String str, String c) {
        if (str != null && !str.isEmpty()) {
            return Arrays.asList(str.split(c));
        }
        return Collections.EMPTY_LIST;
    }

    private boolean isScopeIncluded(String str) {
        return includeScope.contains(str) && !excludeScope.contains(str);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping copy-dependency");
            return;
        }

        List<String> includeScope_l = stringAsList(includeScope, ",");
        List<String> excludeScope_l = stringAsList(excludeScope, ",");
        List<String> featureSetGroupIdIncludes_l = stringAsList(featureSetGroupIdIncludes, ",");
        List<String> copyTypes_l = stringAsList(copyTypes,",");
        List<String> unpackTypes_l = stringAsList(unpackTypes,",");

        // get all direct featureset dependencies's direct dependencies
        final Set<Dependency> dependencies = new HashSet<Dependency>();
        for (Object _a : project.getArtifacts()) {
            org.apache.maven.artifact.Artifact artifact = (org.apache.maven.artifact.Artifact) _a;
            if (featureSetGroupIdIncludes_l.contains(artifact.getGroupId())) {
                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                descriptorRequest.setArtifact(new org.eclipse.aether.artifact.DefaultArtifact(
                        artifact.getGroupId(),
                        artifact.getArtifactId(),
                        artifact.getClassifier(),
                        artifact.getType(),
                        artifact.getVersion()));
                descriptorRequest.setRepositories(remoteRepos);
                try {
                    ArtifactDescriptorResult result = repoSystem.readArtifactDescriptor(repoSession, descriptorRequest);
                    dependencies.addAll(result.getDependencies());
                } catch (ArtifactDescriptorException ex) {
                    throw new MojoExecutionException(ex.getMessage(), ex);
                }
            }
        }

        // build a request to resolve all dependencies
        Set<ArtifactRequest> dependenciesRequest = new HashSet<ArtifactRequest>();
        for(Dependency dependency : dependencies){
            String depScope = dependency.getScope();
            if(includeScope_l.contains(depScope) && !excludeScope_l.contains(depScope)){
                ArtifactRequest request = new ArtifactRequest();
                request.setArtifact(dependency.getArtifact());
                request.setRepositories(remoteRepos);
                dependenciesRequest.add(request);
            }
        }

        // add project direct dependency
        for(Object _directDependency : project.getDependencies()){
            if(_directDependency instanceof org.apache.maven.model.Dependency){
                org.apache.maven.model.Dependency directDependency = (org.apache.maven.model.Dependency) _directDependency;
                // if the dependency is a feature set
                // or not of proper scope
                // skip
                if(featureSetGroupIdIncludes_l.contains(directDependency.getGroupId())
                    || !isScopeIncluded(directDependency.getScope())){
                    continue;
                }
                ArtifactRequest request = new ArtifactRequest();
                request.setArtifact(new org.eclipse.aether.artifact.DefaultArtifact(
                        directDependency.getGroupId(),
                        directDependency.getArtifactId(),
                        directDependency.getClassifier(),
                        directDependency.getType(),
                        directDependency.getVersion()));
                request.setRepositories(remoteRepos);
                dependenciesRequest.add(request);
            }
        }

        // resolve all
        List<ArtifactResult> resolvedDependencies;
        try {
            resolvedDependencies = repoSystem.resolveArtifacts(repoSession, dependenciesRequest);
        } catch (ArtifactResolutionException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }

        stageDirectory.mkdir();

        for (ArtifactResult dependency : resolvedDependencies) {
            getLog().info(dependency.toString());

            File sourceFile = dependency.getArtifact().getFile();
            if (sourceFile == null) {
                getLog().error("dependency "+dependency.getArtifact().toString()+", file is null");
                continue;
            }

            if (copyTypes_l.contains(dependency.getArtifact().getExtension())) {
                if (sourceFile.getName().isEmpty()) {
                    getLog().info("Skipping " + dependency.toString() + ": empty file name");
                }
                File destFile = new File(stageDirectory, 
                        dependency.getArtifact().getArtifactId()+"."+dependency.getArtifact().getExtension());
                if (!silent) {
                    getLog().info("copying " + sourceFile.getPath() + " to " + destFile.getPath());
                }
                try {
                    FileUtils.copyFile(sourceFile, destFile);
                } catch (IOException ex) {
                    getLog().error(ex.getMessage(), ex);
                }
            } else if (unpackTypes_l.contains(dependency.getArtifact().getExtension())) {
                MavenUtils.unpack(
                        sourceFile,
                        stageDirectory,
                        includes,
                        excludes,
                        silent,
                        getLog(),
                        archiverManager);
            } else {
                getLog().error("extension not handled for: " + dependency.toString());
            }
        }
    }
}
