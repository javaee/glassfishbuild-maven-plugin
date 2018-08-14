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
package org.glassfish.build.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.artifact.DefaultArtifact;

/**
 * A model resolver that can resolve remote artifacts during model resolution.
 */
final class MavenModelResolver implements ModelResolver {

    /**
     * List of remote repositories.
     */
    private final List<RemoteRepository> repositories;

    /**
     * The repository IDs of the remote repositories.
     */
    private final Set<String> repositoryIds;

    /**
     * The repository system component.
     */
    private final RepositorySystem system;

    /**
     * The repository session component.
     */
    private final RepositorySystemSession session;

    /**
     * Create a new {@code MavenModelResolver} instance.
     * @param repoSystem repository system component
     * @param repoSession repository session component
     * @param remoteRepos remote repositories to use
     */
    MavenModelResolver(final RepositorySystem repoSystem,
            final RepositorySystemSession repoSession,
            final List<RemoteRepository> remoteRepos) {

        this.system = repoSystem;
        this.session = repoSession;
        this.repositories = new ArrayList<RemoteRepository>(remoteRepos);
        this.repositoryIds = new HashSet<String>();
        for (RemoteRepository repository : repositories) {
            repositoryIds.add(repository.getId());
        }
    }

    /**
     * Copy constructor.
     * @param clone the instance to copy
     */
    private MavenModelResolver(final MavenModelResolver clone) {
        this.system = clone.system;
        this.session = clone.session;
        this.repositories = new ArrayList<RemoteRepository>(clone.repositories);
        this.repositoryIds = new HashSet<String>(clone.repositoryIds);
    }

    @Override
    public void addRepository(final Repository repository,
            final boolean replace)
            throws InvalidRepositoryException {

        if (!replace && repositoryIds.contains(repository.getId())) {
            return;
        }
        if (!repositoryIds.add(repository.getId())) {
            return;
        }

        List<RemoteRepository> newRepositories =
                Collections.singletonList(
                ArtifactDescriptorUtils.toRemoteRepository(repository));

        repositoryIds.add(repository.getId());
        repositories.addAll(newRepositories);
    }

    @Override
    public void addRepository(final Repository repository)
            throws InvalidRepositoryException {

        addRepository(repository, /* replace */ false);
    }

    @Override
    public ModelResolver newCopy() {
        return new MavenModelResolver(this);
    }

    @Override
    public ModelSource resolveModel(final String groupId,
                  final String artifactId,
            final String version)
            throws UnresolvableModelException {

        Artifact artifact = new DefaultArtifact(groupId, artifactId, "pom",
                version);
        try {
            ArtifactRequest request = new ArtifactRequest(artifact,
                    repositories, /* context */ null);
            artifact = system.resolveArtifact(session, request).getArtifact();
        } catch (ArtifactResolutionException e) {
            throw new UnresolvableModelException(
                    String.format(
                            "Failed to resolve POM for %s:%s:%s due to %s",
                            groupId, artifactId, version, e.getMessage()),
                    groupId, artifactId, version, e);
        }
        return new FileModelSource(artifact.getFile());
    }

    @Override
    public ModelSource resolveModel(final Parent parent)
            throws UnresolvableModelException {

        return resolveModel(parent.getGroupId(), parent.getArtifactId(),
                parent.getVersion());
    }
}
