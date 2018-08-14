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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Developer;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Scm;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;

import static org.glassfish.build.utils.MavenUtils.getCommaSeparatedList;
import static org.glassfish.build.utils.MavenUtils.readModel;
import static org.glassfish.build.utils.MavenUtils.writePomToOutputStream;

/**
 * Generates a pom from another pom
 *
 * @author Romain Grecourt
 */
@Mojo(name = "generate-pom")
public class GeneratePomMojo extends AbstractMojo {

    private static final String PROPERTY_PREFIX = "generate.pom.";

    /**
     * The maven project.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The output directory where the file is written.
     */
    @Parameter(property = PROPERTY_PREFIX + "outputDirectory",
               defaultValue = "${project.build.directory}")
    protected File outputDirectory;

    /**
     * The input pom file.
     */
    @Parameter(property = PROPERTY_PREFIX + "pomFile",
            defaultValue = "${project.file}")
    protected File pomFile;

    /**
     * The generated pom file groupId.
     */
    @Parameter(property = PROPERTY_PREFIX + "groupId",
            defaultValue = "${project.groupId}",
            required = true)
    protected String groupId;

    /**
     * The generated pom file artifactId.
     */
    @Parameter(property = PROPERTY_PREFIX + "artifactId",
            defaultValue = "${project.artifactId}")
    protected String artifactId;

    /**
     * The generated pom file version.
     */
    @Parameter(property = PROPERTY_PREFIX + "version",
            defaultValue = "${project.version}")
    protected String version;

    /**
     * The generated pom file parent.
     */
    @Parameter(property = PROPERTY_PREFIX + "parent")
    protected Parent parent;

    /**
     * The generated pom file description.
     */
    @Parameter(property = PROPERTY_PREFIX + "description")
    protected String description;

    /**
     * The generated pom file name.
     */
    @Parameter(property = PROPERTY_PREFIX + "name")
    protected String name;

    /**
     * The generated pom file scm.
     */
    @Parameter(property = PROPERTY_PREFIX + "scm",
            defaultValue = "${project.scm}")
    protected Scm scm;

    /**
     * The generated pom file issueManagement.
     */
    @Parameter(property = PROPERTY_PREFIX + "issueManagement",
            defaultValue = "${project.issueManagement}")
    protected IssueManagement issueManagement;

    /**
     * The generated pom file mailingLists.
     */
    @Parameter(property = PROPERTY_PREFIX + "mailingLists",
            defaultValue = "${project.mailingLists}")
    protected List<MailingList> mailingLists;

    /**
     *
     * The generated pom file developers.
     */
    @Parameter(property = PROPERTY_PREFIX + "developers",
            defaultValue = "${project.developers}")
    protected List<Developer> devevelopers;

    /**
     *
     * The generated pom file licenses.
     */
    @Parameter(property = PROPERTY_PREFIX + "licenses",
            defaultValue = "${project.licenses}")
    protected List<License> licenses;

    /**
     *
     * The generated pom file organization.
     */
    @Parameter(property = PROPERTY_PREFIX + "organization",
            defaultValue = "${project.organization}")
    protected Organization organization;

    /**
     * Comma separated list of exclusions for project dependencies in the generated
     * pom file.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeDependencies")
    protected String excludeDependencies;

    /**
     * Comma separated list of scopes to excludes for project dependencies in the
     * generated pom file.
     */
    @Parameter(property = PROPERTY_PREFIX + "excludeDependencyScope",
            defaultValue = "system,test")
    protected String excludeDependencyScopes;

    /**
     * Project dependencies to add to the generated pom file.
     */
    @Parameter(property = PROPERTY_PREFIX + "dependencies",
            defaultValue = "${project.dependencies}")
    protected List<Dependency> dependencies;

    /**
     * Skip this mojo.
     */
    @Parameter(property = PROPERTY_PREFIX + "skip",
            defaultValue = "false")
    protected Boolean skip;

    /**
     * Attach the generated pom to the current project.
     */
    @Parameter(property = PROPERTY_PREFIX + "attach",
            defaultValue = "false")
    protected Boolean attach;

    /**
     * Maven artifact resolver.
     */
    @Component
    protected ArtifactResolver artifactResolver;

    /**
     * Maven repository manager.
     */
    @Component
    protected RemoteRepositoryManager remoteRepositoryManager;

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
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}",
            readonly = true)
    private List<RemoteRepository> projectRepos;

    /**
     * Maven model builder.
     */
    @Component
    protected ModelBuilder modelBuilder;

    private static boolean validateString(String str){
        return str != null && !str.isEmpty();
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if(skip){
            getLog().info("skipping...");
            return;
        }

        Model model = readModel(pomFile);

        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);  
        model.setDevelopers(devevelopers);

        if(parent != null && validateString(parent.getGroupId())
                && validateString(parent.getArtifactId())
                && validateString(parent.getVersion())){
            model.setParent(parent);
        } else {
            model.setParent(null);
        }

        model.setName(name);
        model.setDescription(description);
        model.setScm(scm);
        model.setIssueManagement(issueManagement);
        model.setMailingLists(mailingLists);
        model.setLicenses(licenses);
        model.setOrganization(organization);
        model.setBuild(new Build());

        List<String> artifactIdExclusions = getCommaSeparatedList(excludeDependencies);
        List<String> scopeExclusions =  getCommaSeparatedList(excludeDependencyScopes);

        for (Object o : dependencies.toArray()) {
            Dependency d = (Dependency)o;
            if (artifactIdExclusions.contains(d.getArtifactId())
                    || scopeExclusions.contains(d.getScope())) {
                dependencies.remove(d);
            }
        }

        model.setDependencies(dependencies);

        File newPomFile = new File(outputDirectory, "pom.xml");
        newPomFile.getParentFile().mkdirs();

        FileWriter fw = null;
        try {
            // write comments from base pom
            fw = new FileWriter(newPomFile);
            String line;
            BufferedReader br = new BufferedReader(new FileReader(pomFile));
            while((line = br.readLine()) !=null && !line.startsWith("<project")){
                fw.write(line);
                fw.write('\n');
            }

            // write new pom and skip first line (xml header)
            String pom = writePomToOutputStream(model).toString();
            int ind = pom.indexOf('\n');
            fw.write(pom.substring(ind));
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getMessage(),ex);
        } finally {
            try {
                if (fw != null) {
                    fw.close();
                }
            } catch (IOException ex) {
            }
        }

        if(attach){
            project.setFile(newPomFile);
        }
    }
}