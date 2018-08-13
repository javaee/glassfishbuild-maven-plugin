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
package org.glassfish.build.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.artifact.filter.collection.*;
import org.apache.tools.ant.types.ZipFileSet;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 *
 * @author Romain Grecourt
 */
public class MavenUtils {

    /**
     * Reads a given model
     * @param pom the pom File
     * @return an instance of Model
     * @throws MojoExecutionException
     */
    public static Model readModel(File pom) throws MojoExecutionException{
        try {
           return new DefaultModelReader().read(pom, null);
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage(),ex);
        }
    }

    private static String getFinalName(Model model){
        Build build = model.getBuild();
        String finalName;
        if(build != null && build.getFinalName() !=null){
            finalName = build.getFinalName();
        } else {
            String version = model.getVersion() != null ?
                    model.getVersion() :
                    model.getParent().getVersion();
            finalName = model.getArtifactId() + "-" + version;
        }
        return finalName;
    }

    /**
     * Create a list of attached artifacts and their associated files by searching
     * for <code>target/${project.build.finalName}-*.*</code>.
     * @param dir ${project.build.directory}
     * @param artifact the main artifact for which corresponding attached artifacts
     * will be searched
     * @param model an instance of model
     * @return the list of attached artifacts
     * @throws MojoExecutionException
     */
    public static List<Artifact> createAttachedArtifacts(String dir,
                                                         Artifact artifact,
                                                         Model model)
            throws MojoExecutionException {

        if(dir == null || dir.isEmpty()){
            throw new IllegalArgumentException("dir is null or empty");
        }
        if (artifact == null) {
            throw new IllegalArgumentException("artifact is null");
        }
        if (model == null) {
            throw new IllegalArgumentException("model is null");
        }

        // compute finalName
        String artifactName = "";
        String finalName;
        if(artifact.getFile() != null && artifact.getFile().exists()){
            artifactName = artifact.getFile().getName();
            finalName = artifactName.substring(0, artifactName.lastIndexOf('.'));
        } else {
            finalName = getFinalName(model);
        }

        List<File> attachedFiles = getFiles(dir, finalName + "*.*", artifactName);
        List<Artifact> attachedArtifacts = new ArrayList<Artifact>();
        if(!attachedFiles.isEmpty()){
            for(File attached : attachedFiles){
                String tokens = attached.getName().substring(finalName.length());

                // pom is not an attached artifact
                if(tokens.endsWith(".pom")){
                    continue;
                }

                String type;
                if(tokens.endsWith(".asc")){
                    // compute type as xxx.asc
                    type = tokens.substring(
                           tokens.substring(0,tokens.length()-4).lastIndexOf('.') + 1,
                           tokens.length());
                } else {
                    type = tokens.substring(
                            tokens.lastIndexOf('.')+1,
                            tokens.length());
                }

                String classifier;
                if(tokens.endsWith(".pom.asc")){
                    // pom.asc does not have any classifier
                    classifier = "";
                } else {
                    // classifier = tokens - type
                    classifier = tokens.substring(
                            tokens.lastIndexOf('-') + 1,
                            tokens.length() - (type.length() + 1));

                    if (classifier.contains(artifact.getVersion())) {
                        classifier = classifier.substring(
                                classifier.indexOf(artifact.getVersion() + 1,
                                classifier.length() - (artifact.getVersion().length())));
                    }
                }

                Artifact attachedArtifact = createArtifact(model,type,classifier);
                attachedArtifact.setFile(attached);
                attachedArtifacts.add(attachedArtifact);
            }
        }
        return attachedArtifacts;
    }

    private static Artifact getArtifactFile(String dir,
                                            String finalName,
                                            Model model)
            throws MojoExecutionException{

        if(dir == null || dir.isEmpty()){
            throw new IllegalArgumentException("dir is null or empty");
        }
        if (finalName == null || finalName.isEmpty()) {
            throw new IllegalArgumentException("finalName is null");
        }
        if (model == null) {
            throw new IllegalArgumentException("model is null");
        }

        List<File> files = getFiles(dir,finalName + ".*",finalName+"-*.");
        Map<String, File> extensionMap = new HashMap<String, File>(files.size());
        for (File f : files) {
            extensionMap.put(f.getName().substring(finalName.length() + 1), f);
        }

        // 1. guess the extension from the packaging
        File artifactFile = extensionMap.get(model.getPackaging());
        if(artifactFile != null){
            Artifact artifact = createArtifact(model);
            artifact.setFile(artifactFile);
            return artifact;
        }

        // 2. take what's available
        for(String ext : extensionMap.keySet()){
            if(!ext.equals("pom") && !ext.endsWith(".asc")){
                // packaging does not match the type
                // hence we provide type = ext
                Artifact artifact = createArtifact(model,ext,null);
                artifact.setFile(extensionMap.get(ext));
                return artifact;
            }
        }
        return null;
    }

    /**
     * Create an artifact and its associated file by searching for
     * <code>target/${project.build.finalName}.${project.packaging}</code>.
     * @param dir ${project.build.directory}
     * @param model an instance of model
     * @return
     * @throws MojoExecutionException
     */
    public static Artifact createArtifact(String dir, Model model)
            throws MojoExecutionException {

        // resolving using finalName
        Artifact artifact = getArtifactFile(dir,getFinalName(model),model);
        if(artifact == null){
            // resolving using artifactId
            artifact = getArtifactFile(dir,model.getArtifactId(),model);
        }
        return artifact;
    }

    /**
     * Returns the pom installed in target or null if not found
     * @param dir ${project.build.directory}
     * @return an instance of the pom file or null if not found
     * @throws MojoExecutionException
     */
    public static File getPomInTarget(String dir) throws MojoExecutionException {
        // check for an existing .pom
         List<File> poms = getFiles(dir, "*.pom","");
         if(!poms.isEmpty()){
            return poms.get(0);
         }
         return null;
    }

    /**
     * Return the files contained in the directory, using inclusion and exclusion
     * ant patterns.
     * @param dir the directory to scan
     * @param includes the includes pattern, comma separated
     * @param excludes the excludes pattern, comma separated
     * @return
     * @throws MojoExecutionException if an IO exception occurred
     */
    public static List<File> getFiles(String dir,
                                      String includes,
                                      String excludes)
            throws MojoExecutionException{

        if(dir == null || dir.isEmpty()){
            throw new IllegalArgumentException("dir is null or empty");
        }

        File f = new File(dir);
        if (f.exists() && f.isDirectory()) {
            try {
                return FileUtils.getFiles(f, includes, excludes);
            } catch (IOException ex) {
                throw new MojoExecutionException(ex.getMessage(), ex);
            }
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * Creates an artifact instance for the supplied model.
     * @param m the model
     * @return the created artifact
     */
    public static Artifact createArtifact(Model m) {
        return createArtifact(m,m.getPackaging(),null);
    }

    /**
     * Creates an artifact instance for the supplied model.
     * @param model the model
     * @param type the type of the artifact
     * @param classifier the classifier to use
     * @return the created artifact
     */
    public static Artifact createArtifact(Model model,
                                          String type,
                                          String classifier) {

        String groupId = model.getGroupId();
        groupId = (groupId == null ? model.getParent().getGroupId() : groupId);
        String version = model.getVersion();
        version = (version == null ? model.getParent().getVersion(): version);

        return new DefaultArtifact(groupId, model.getArtifactId(),
                VersionRange.createFromVersion(version), /* scope */ "runtime",
                type, classifier, new DefaultArtifactHandler(type));
    }

    /**
     * Creates an artifact instance for the supplied coordinates.
     * @param groupId The groupId
     * @param artifactId The artifactId
     * @param version The version
     * @param type The type of the artifact. e.g "jar", "war" or "zip"
     * @param classifier The classifier
     * @return the created artifact
     */
    public static Artifact createArtifact(String groupId,
                                          String artifactId,
                                          String version,
                                          String type,
                                          String classifier) {

        return new DefaultArtifact(groupId, artifactId,
                VersionRange.createFromVersion(version), /* scope */ "runtime",
                type, classifier, new DefaultArtifactHandler(type));
    }

    /**
     * Creates an artifact instance for the supplied coordinates
     * @param groupId The groupId
     * @param artifactId The artifactId
     * @param version The version
     * @param type The type of the artifact. e.g "jar", "war" or "zip"
     * @return the created artifact
     */
    public static Artifact createArtifact(String groupId,
                                          String artifactId,
                                          String version,
                                          String type) {

        return createArtifact(groupId, artifactId, version, type,
                /* classifier */ null);
    }

    /**
     * Creates an artifact instance from a dependency object.
     * 
     * @param dep the dependency object
     * @return the created artifact
     */
    public static Artifact createArtifact(Dependency dep) {
        return createArtifact(dep.getGroupId(), dep.getArtifactId(),
                dep.getVersion(), dep.getType(), dep.getClassifier());
    }

    /**
     * Write the model to the buildDir/${project.build.finalName}.pom
     * @param model an instance of model
     * @param buildDir the directory in which to write the pom
     * @throws IOException
     */
    public static void writePom(Model model, File buildDir) throws IOException {
        writePom(model, buildDir, null);
    }

    /**
     * Write the model to the buildDir/${project.build.finalName}.pom
     * @param model an instance of model
     * @param buildDir the directory in which to write the pom
     * @param pomFileName the name of the written pom
     * @throws IOException
     */
    public static void writePom(Model model, File buildDir, String pomFileName)
            throws IOException {
        
        if (pomFileName == null) {
            if (model.getBuild() != null
                    && model.getBuild().getFinalName() != null) {
                pomFileName = model.getBuild().getFinalName() + ".pom";
            } else {
                pomFileName = "pom.xml";
            }
        }
        File pomFile = new File(buildDir, pomFileName);
        new DefaultModelWriter().write(pomFile, null, model);
        
        model.setPomFile(pomFile);
    }

    /**
     * Write the model to the <code>buildDir/${project.build.finalName}.pom</code>
     * @param model an instance of model
     * @return
     * @throws org.apache.maven.plugin.MojoExecutionException if an IOException is caught
     */
    public static String modelAsString(Model model) throws MojoExecutionException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try{
            new DefaultModelWriter().write(baos, null, model);
            return new String(baos.toByteArray());
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }

    /**
     * Write the model to the <code>buildDir/${project.build.finalName}.pom</code>
     * @param model an instance of model
     * @return
     * @throws IOException
     */
    public static ByteArrayOutputStream writePomToOutputStream(Model model) 
            throws IOException {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new DefaultModelWriter().write(baos, null, model);
        return baos;
    }

    /**
     * Filters a set of artifacts
     * @param artifacts the set of artifacts to filter
     * @param dependencyArtifacts the set of artifact representing direct dependencies
     * @param excludeTransitive exclude transitive dependencies
     * @param includeScope the scopes to include, comma separated, can be null
     * @param excludeScope the scopes to exclude, comma separated, can be null
     * @param excludeTypes the types to include, comma separated, can be null
     * @param includeTypes the types to exclude, comma separated, can be null
     * @return the set of filtered artifacts
     * @throws MojoExecutionException
     */
    public static Set<Artifact> filterArtifacts(Set<Artifact> artifacts,
                                                Set<Artifact> dependencyArtifacts,
                                                boolean excludeTransitive,
                                                String includeScope,
                                                String excludeScope,
                                                String excludeTypes,
                                                String includeTypes)
            throws MojoExecutionException {

        return filterArtifacts(artifacts, dependencyArtifacts, excludeTransitive,
                includeScope, excludeScope, includeTypes, excludeTypes,
                /* includeClassifiers */ null, /* excludeClassifiers */ null,
                /* includeGroupIds */ null, /* excludeGroupIds */ null,
                /* includeArtifactIds */ null, /* excludeArtifactIds */ null);
    }

    /**
     * Filters a set of artifacts.
     * @param artifacts the set of artifacts to filter
     * @param dependencyArtifacts the set of artifact representing direct dependencies
     * @return the set of filtered artifacts* 
     * @throws MojoExecutionException
     */
    public static Set<Artifact> excludeTransitive(Set<Artifact> artifacts,
                                                  Set<Artifact> dependencyArtifacts)
            throws MojoExecutionException{

        return filterArtifacts(artifacts, dependencyArtifacts,
                /* excludeTransitive */ true, /* includeScope */ null,
                /* excludeScope */ null, /* includeTypes */ null,
                /* excludeTypes */ null, /* includeClassifiers */ null,
                /* excludeClassifiers */ null, /* includeGroupIds */ null,
                /* excludeGroupIds */ null, /* includeArtifactIds */ null,
                /* excludeArtifactIds */ null);
    }

    /**
     * Filters a set of artifacts.
     * @param artifacts the set of artifacts to filter
     * @param dependencyArtifacts the set of artifact representing direct dependencies
     * @param excludeTransitive exclude transitive dependencies
     * @param includeScope the scopes to include, comma separated, can be null
     * @param excludeScope the scopes to exclude, comma separated, can be null
     * @param excludeTypes the types to exclude, comma separated, can be null
     * @param includeTypes the types to include, comma separated, can be null
     * @param includeClassifiers the classifiers to include, comma separated, can be null
     * @param excludeClassifiers the classifiers to exclude, comma separated, can be null
     * @param includeGroupIds the groupIds to include, comma separated, can be null
     * @param excludeGroupIds the groupIds to exclude, comma separated, can be null
     * @param includeArtifactIds the artifactIds to include, comma separated, can be null
     * @param excludeArtifactIds the artifactIds to exclude, comma separated, can be null
     * @return the set of filtered artifacts* 
     * @throws MojoExecutionException
     */
    public static Set<Artifact> filterArtifacts(Set<Artifact> artifacts,
                                                Set<Artifact> dependencyArtifacts,
                                                boolean excludeTransitive,
                                                String includeScope,
                                                String excludeScope,
                                                String excludeTypes,
                                                String includeTypes,
                                                String includeClassifiers,
                                                String excludeClassifiers,
                                                String includeGroupIds,
                                                String excludeGroupIds,
                                                String includeArtifactIds,
                                                String excludeArtifactIds)
            throws MojoExecutionException {

        // init all params with empty string if null
        includeScope = (includeScope == null? "":includeScope);
        excludeScope = (excludeScope == null? "":excludeScope);
        includeTypes = (includeTypes == null? "":includeTypes);
        excludeTypes = (excludeTypes == null? "":excludeTypes);
        includeClassifiers = (includeClassifiers == null? "":includeClassifiers);
        excludeClassifiers = (excludeClassifiers == null? "":excludeClassifiers);
        includeGroupIds = (includeGroupIds == null? "":includeGroupIds);
        excludeGroupIds = (excludeGroupIds == null? "":excludeGroupIds);
        includeArtifactIds = (includeArtifactIds == null? "":includeArtifactIds);
        excludeArtifactIds = (excludeArtifactIds == null? "":excludeArtifactIds);

        FilterArtifacts filter = new FilterArtifacts();

        filter.addFilter(new ProjectTransitivityFilter(
                dependencyArtifacts,
                excludeTransitive));

        filter.addFilter(new ScopeFilter(
                cleanToBeTokenizedString(includeScope),
                cleanToBeTokenizedString(excludeScope)));

        filter.addFilter(new TypeFilter(
                cleanToBeTokenizedString(includeTypes),
                cleanToBeTokenizedString(excludeTypes)));

        filter.addFilter(new ClassifierFilter(
                cleanToBeTokenizedString(includeClassifiers),
                cleanToBeTokenizedString(excludeClassifiers)));

        filter.addFilter(new GroupIdFilter(
                cleanToBeTokenizedString(includeGroupIds),
                cleanToBeTokenizedString(excludeGroupIds)));

        filter.addFilter(new ArtifactIdFilter(
                cleanToBeTokenizedString(includeArtifactIds),
                cleanToBeTokenizedString(excludeArtifactIds)));

        try {
            artifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        return artifacts;
    }

    /**
     * Unpacks a given file.
     * @param file the file to unpack
     * @param location the directory where to unpack
     * @param includes includes pattern for the files to unpack
     * @param excludes exclude pattern for the files to unpack
     * @param silent log unpack or not
     * @param log the Maven logger instance, can be null
     * @param archiverManager an instance of ArchiveManager
     * @throws MojoExecutionException
     */
    public static void unpack(File file,
                              File location,
                              String includes,
                              String excludes,
                              boolean silent,
                              Log log,
                              ArchiverManager archiverManager)
            throws MojoExecutionException {

        if (log != null && log.isInfoEnabled() && !silent) {
            log.info(logUnpack(file, location, includes, excludes));
        }

        location.mkdirs();

        try {
            UnArchiver unArchiver = archiverManager.getUnArchiver(file);
            unArchiver.setSourceFile(file);
            unArchiver.setDestDirectory(location);

            if (StringUtils.isNotEmpty(excludes)
                    || StringUtils.isNotEmpty(includes)) {

                IncludeExcludeFileSelector[] selectors =
                        new IncludeExcludeFileSelector[]{
                    new IncludeExcludeFileSelector()
                };

                if (StringUtils.isNotEmpty(excludes)) {
                    selectors[0].setExcludes(excludes.split(","));
                }
                if (StringUtils.isNotEmpty(includes)) {
                    selectors[0].setIncludes(includes.split(","));
                }
                unArchiver.setFileSelectors(selectors);
            }

            unArchiver.extract();
        } catch (NoSuchArchiverException e) {
            throw new MojoExecutionException("Unknown archiver type", e);
        } catch (ArchiverException e) {
            throw new MojoExecutionException(
                    "Error unpacking file: " + file + " to: " + location + "\r\n"
                    + e.toString(), e);
        }
    }

    private static String logUnpack(File file,
                                    File location,
                                    String includes,
                                    String excludes) {

        StringBuilder msg = new StringBuilder();
        msg.append("Unpacking ");
        msg.append(file);
        msg.append(" to ");
        msg.append(location);

        if (includes != null && excludes != null) {
            msg.append(" with includes \"");
            msg.append(includes);
            msg.append("\" and excludes \"");
            msg.append(excludes);
            msg.append("\"");
        } else if (includes != null) {
            msg.append(" with includes \"");
            msg.append(includes);
            msg.append("\"");
        } else if (excludes != null) {
            msg.append(" with excludes \"");
            msg.append(excludes);
            msg.append("\"");
        }

        return msg.toString();
    }

    public static ArtifactResult resolveArtifact(String groupId,
                                                 String artifactId,
                                                 String classifier,
                                                 String type,
                                                 String version,
                                                 RepositorySystem repoSystem,
                                                 RepositorySystemSession repoSession,
                                                 List<RemoteRepository> remoteRepos)
            throws MojoExecutionException {

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new org.eclipse.aether.artifact.DefaultArtifact(
                groupId, artifactId, classifier, type, version));
        request.setRepositories(remoteRepos);

        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        return result;
    }

    /**
     * Clean the pattern string for future regexp usage.
     * @param str the string to cleanup
     * @return the cleaned string
     */
    public static String cleanToBeTokenizedString(String str) {
        String ret = "";
        if (!StringUtils.isEmpty(str)) {
            ret = str.trim().replaceAll("[\\s]*,[\\s]*", ",");
        }
        return ret;
    }

    /**
     * Write the given input to a file.
     * @param outfile the file to write to
     * @param input the input to write
     * @throws IOException if an error occurs while writing to the file
     */
    public static void writeFile(File outfile, StringBuilder input)
            throws IOException {

        Writer writer = WriterFactory.newXmlWriter(outfile);
        try {
            IOUtil.copy(input.toString(), writer);
        } finally {
            IOUtil.close(writer);
        }
    }

    /**
     * Convert a comma separated string into a list.
     * @param list the string containing items separated by comma(s)
     * @return list
     */
    public static List<String> getCommaSeparatedList(String list){
        if (list != null) {
            String[] listArray = list.split(",");
            if (listArray != null) {
                return Arrays.asList(listArray);
            }
        }
        return Collections.EMPTY_LIST;
    }

    private static String listToString(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i));
            if (i < list.size() - 1) {
                sb.append(',');
            }
        }
        return sb.toString();
    }

    public static ZipFileSet createZipFileSet(File dir,
                                              List<String> includes,
                                              List<String> excludes) {

        return createZipFileSet(dir, listToString(includes),
                listToString(excludes));
    }

    public static ZipFileSet createZipFileSet(File dir,
                                              String includes,
                                              String excludes) {

        ZipFileSet fset = new ZipFileSet();
        fset.setDir(dir);
        fset.setIncludes(includes);
        fset.setExcludes(excludes);
        fset.setDescription(String.format(
                "file set: %s ( excludes: [ %s ], includes: [ %s ])",
                dir.getAbsolutePath(),
                excludes == null ? "" : excludes,
                includes == null ? "" : includes));
        return fset;
    }

    public static File createZip(Properties props,
                                 Log log,
                                 String duplicate,
                                 List<ZipFileSet> fsets,
                                 File target) {

        ZipHelper.getInstance().zip(props, log, duplicate, fsets, target);
        return target;
    }
}
