/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017-2018 Oracle and/or its affiliates. All rights reserved.
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
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
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

import static org.glassfish.build.utils.MavenUtils.unpack;

/**
 * Resolves and unpack corresponding sources of project dependencies.
 *
 * @author Romain Grecourt
 */
@Mojo(name = "featuresets-dependencies",
      requiresProject = true,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class FeatureSetsDependenciesMojo extends AbstractMojo {

    private static final String PROPERTY_PREFIX = "gfbuild.featuresets.dependencies.";

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
     * Manager used to look up Archiver/UnArchiver implementations.
     */
    @Component
    protected ArchiverManager archiverManager;

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The directory where the files will be copied.
     */
    @Parameter(property = PROPERTY_PREFIX + "stageDirectory",
               defaultValue = "${project.build.directory}/stage")
    protected File stageDirectory;

    /**
     * Comma separated list of file extensions to include for copy.
     */
    @Parameter(property = PROPERTY_PREFIX + "copyTypes",
               defaultValue = "jar,war,rar")
    protected String copyTypes;

    /**
     * Comma separated list of (g:)a(:v) to excludes for unpack.
     */
    @Parameter(property = PROPERTY_PREFIX + "copyExcludes",
            defaultValue = "")
    protected String copyExcludes;

    /**
     * Comma separated list of file extensions to include for unpack.
     */
    @Parameter(property = PROPERTY_PREFIX + "unpackTypes",
            defaultValue = "zip")
    protected String unpackTypes;

    /**
     * Comma separated list of (g:)a(:v) to excludes for unpack.
     */
    @Parameter(property = PROPERTY_PREFIX + "unpackExcludes",
            defaultValue = "")
    protected String unpackExcludes;

    /**
     * Comma separated list of include patterns.
     */
    @Parameter(property = PROPERTY_PREFIX + "includes",
            defaultValue = "")
    private String includes;

    /**
     * Comma separated list of exclude patterns.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludes",
            defaultValue = "")
    private String excludes;

    /**
     * Scope to include.
     * An Empty string indicates all scopes.
     */
    @Parameter(property = PROPERTY_PREFIX + "includeScope",
            defaultValue = "compile",
            required = false)
    protected String includeScope;

    /**
     * Scope to exclude.
     * An Empty string indicates no scopes.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeScope",
            defaultValue = "test,system")
    protected String excludeScope;

    /**
     * The groupId of the feature sets to include.
     */
    @Parameter(property = PROPERTY_PREFIX + "featureset.groupid.includes",
            defaultValue = "")
    protected String featureSetGroupIdIncludes;

    /**
     * Skip this mojo.
     */
    @Parameter(property = PROPERTY_PREFIX + "skip",
            defaultValue = "false")
    private boolean skip;

    /**
     * @parameter
     */    
    protected List<DependencyMapping> mappings;

    public static class DependencyMapping {

        private String groupId;
        private String artifactId;
        private String name;

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private String getMapping(org.eclipse.aether.artifact.Artifact artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("artifact must be non null");
        }
        if (mappings != null && !mappings.isEmpty()) {
            for (DependencyMapping mapping : mappings) {
                // if groupId is supplied, filter groupId
                if(mapping.getGroupId() != null && !mapping.getGroupId().isEmpty()){
                    if(!artifact.getGroupId().equals(mapping.getGroupId())){
                        continue;
                    }
                }
                if (artifact.getArtifactId().equals(mapping.getArtifactId())
                        && mapping.getName() != null
                        && !mapping.getName().isEmpty()) {
                    return mapping.getName();
                }
            }
        }
        return artifact.getArtifactId();
    }

    private static List<String> stringAsList(String str, String c) {
        if (str != null && !str.isEmpty()) {
            return Arrays.asList(str.split(c));
        }
        return Collections.EMPTY_LIST;
    }

    private boolean isScopeIncluded(String str) {
        return includeScope.contains(str) && !excludeScope.contains(str);
    }

    private boolean isArtifactExcluded(List<String> excludes,
                                       org.eclipse.aether.artifact.Artifact artifact){

        for(String exclude : excludes){
            String[] gav = exclude.split(":");
            if(gav == null || gav.length == 0){
                continue;
            }
            switch(gav.length){
                // gav == artifactId
                case 1:
                    if(artifact.getArtifactId().equals(gav[0])){
                        return true;
                    }
                    break;
                // gav == groupId:artifactId
                case 2:
                    if(artifact.getGroupId().equals(gav[0]) 
                            && artifact.getArtifactId().equals(gav[1])){
                        return true;
                    }
                    break;
                // gav == groupId:artifactId:version
                case 3:
                    if(artifact.getGroupId().equals(gav[0]) 
                            && artifact.getArtifactId().equals(gav[1])
                            && artifact.getVersion().equals(gav[2])){
                        return true;
                    }
                    break;
            }
        }
        return false;
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
        List<String> unpackExcludes_l = stringAsList(unpackExcludes, ",");
        List<String> copyExcludes_l = stringAsList(copyExcludes, ",");

        // get all direct featureset dependencies's direct dependencies
        final Set<Dependency> dependencies = new HashSet<Dependency>();
        for (Object _a : project.getArtifacts()) {
            org.apache.maven.artifact.Artifact artifact = (org.apache.maven.artifact.Artifact) _a;
            if (featureSetGroupIdIncludes_l.contains(artifact.getGroupId())) {
                ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
                descriptorRequest.setArtifact(new org.eclipse.aether.artifact.DefaultArtifact(
                        artifact.getGroupId(), artifact.getArtifactId(),
                        artifact.getClassifier(), artifact.getType(),
                        artifact.getVersion()));
                descriptorRequest.setRepositories(remoteRepos);
                try {
                    ArtifactDescriptorResult result = repoSystem.
                            readArtifactDescriptor(repoSession, descriptorRequest);
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
                org.apache.maven.model.Dependency directDependency =
                        (org.apache.maven.model.Dependency) _directDependency;

                // if the dependency is a feature set
                // or not of proper scope
                // skip
                if(featureSetGroupIdIncludes_l.contains(directDependency.getGroupId())
                    || !isScopeIncluded(directDependency.getScope())){
                    continue;
                }

                ArtifactRequest request = new ArtifactRequest();
                request.setArtifact(new org.eclipse.aether.artifact.DefaultArtifact(
                        directDependency.getGroupId(), directDependency.getArtifactId(),
                        directDependency.getClassifier(), directDependency.getType(),
                        directDependency.getVersion()));
                request.setRepositories(remoteRepos);
                dependenciesRequest.add(request);
            }
        }

        // resolve all
        List<ArtifactResult> resolvedDependencies;
        try {
            resolvedDependencies = repoSystem.resolveArtifacts(repoSession,
                    dependenciesRequest);
        } catch (ArtifactResolutionException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }

        stageDirectory.mkdir();

        for (ArtifactResult dependency : resolvedDependencies) {

            File sourceFile = dependency.getArtifact().getFile();
            if (sourceFile == null) {
                getLog().error("dependency " + dependency.getArtifact().toString()
                        + ", file is null");
                continue;
            }

            if (sourceFile.getName().isEmpty()) {
                getLog().info("dependency " + dependency.getArtifact().toString()
                        + ": empty file name");
                continue;
            }

            boolean doCopy = copyTypes_l.contains(dependency.getArtifact().getExtension());
            boolean doUnpack = unpackTypes_l.contains(dependency.getArtifact().getExtension());
            if(doCopy && doUnpack){
                boolean isUnpackExcluded = isArtifactExcluded(unpackExcludes_l, dependency.getArtifact());
                boolean isCopyExcluded = isArtifactExcluded(copyExcludes_l, dependency.getArtifact());
                if(isUnpackExcluded && isCopyExcluded){
                    // if both are included, do nothing
                    getLog().warn("Excluded: "+dependency.getArtifact().toString());
                    doCopy = false;
                    doUnpack = false;
                } else if (isCopyExcluded && isUnpackExcluded){
                    // not excluded, copy trumps
                    doCopy = true;
                    doUnpack = false;
                } else if(isCopyExcluded){
                    doCopy = false;
                    doUnpack = true;
                } else {
                    doCopy = true;
                    doUnpack = false;
                }
            }

            if (doCopy) {
                String mapping = getMapping(dependency.getArtifact());
                File destFile = new File(stageDirectory,
                        mapping + "." + dependency.getArtifact().getExtension());
                String relativeDestFile = destFile.getPath().substring(
                        project.getBasedir().getPath().length()+1);
                getLog().info("Copying " + dependency.getArtifact() + " to "
                        + relativeDestFile);
                try {
                    FileUtils.copyFile(sourceFile, destFile);
                } catch (IOException ex) {
                    getLog().error(ex.getMessage(), ex);
                }
            }

            if (doUnpack) {
                String mapping = getMapping(dependency.getArtifact());
                File destDir = new File(stageDirectory,mapping);
                String relativeDestDir = destDir.getPath()
                        .substring(project.getBasedir().getPath().length() + 1);
                getLog().info("Unpacking " + dependency.getArtifact() + " to "
                        + relativeDestDir);
                unpack(sourceFile, destDir, includes, excludes, /* silent */ true,
                        getLog(), archiverManager);
            }
        }
    }
}
