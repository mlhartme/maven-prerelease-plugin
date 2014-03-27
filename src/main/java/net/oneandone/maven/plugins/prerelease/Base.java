/**
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.prerelease;

import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Separator;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Base extends AbstractMojo {
    /**
     * Where to store prereleases.
     */
    @Parameter(property = "prerelease.storages", defaultValue = "${settings.localRepository}/../prereleases", required = true)
    private List<String> storages;

    /**
     * Timeout in seconds for locking a prerelease archive.
     */
    @Parameter(property = "prerelease.lockTimeout", defaultValue = "3600", required = true)
    protected int lockTimeout;

    /**
     * Number of prereleases to keep in archive. 0 to keep all, which should only be used together with swap and keep.
     */
    @Parameter(property = "prerelease.keep", defaultValue = "1", required = true)
    protected int keep;

    /**
     * Extra arguments to pass to the sub-maven build. A space-separated list with entries of the form -Dkey=value.
     * Similar to "arguments" parameter of the Maven Release Plugin, but restricted to -D properties.
     */
    @Parameter(property = "prerelease.args", defaultValue = "")
    private String propertyArgs;

    @Component
    private ProjectBuilder projectBuilder;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(property = "project.remoteArtifactRepositories", readonly = true)
    private List<ArtifactRepository> remoteRepositories;

    @Parameter( defaultValue = "${localRepository}", readonly = true, required = true )
    private ArtifactRepository localRepository;

    @Component
    protected MavenSession session;

    protected final World world;

    public Base() {
        this.world = new World();
    }

    @Override
    public void execute() throws MojoExecutionException {
        getLog().debug("user-properties: " + session.getUserProperties());
        try {
            doExecute();
        } catch (RuntimeException | MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("plugin failed: " + e.getMessage(), e);
        }
    }

    public abstract void doExecute() throws Exception;

    protected Map<String, String> propertyArgs() throws MojoExecutionException {
        int idx;
        Map<String, String> result;
        String key;
        String value;

        result = new HashMap<>();
        if (propertyArgs != null) {
            for (String entry : Separator.SPACE.split(propertyArgs)) {
                if (!entry.startsWith("-D")) {
                    throw new MojoExecutionException("-Dkey=value expected, got " + entry);
                }
                entry = entry.substring(2);
                idx = entry.indexOf('=');
                if (idx == -1) {
                    key = entry;
                    value = "true";
                } else {
                    key = entry.substring(0, idx).trim();
                    value = entry.substring(idx + 1).trim();
                }
                result.put(key, value);
            }
        }
        return result;
    }

    protected Maven maven() {
        return new Maven(world, getLog(), session, localRepository,
                session.getRequest().getExecutionListener(), projectHelper, projectBuilder, remoteRepositories);
    }

    protected List<FileNode> storages() throws IOException {
        List<FileNode> result;

        if (storages.size() == 0) {
            throw new IOException("expected at least 1 storage");
        }
        result = new ArrayList<>(storages.size());
        for (String s : storages) {
            result.add(world.file(new File(s).getCanonicalFile()));
        }
        return result;
    }

    protected String version() {
        String result;

        result = getClass().getPackage().getSpecificationVersion();
        return result == null ? "unknown" : result;
    }
}
