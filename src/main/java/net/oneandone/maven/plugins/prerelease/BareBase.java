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

import net.oneandone.maven.plugins.prerelease.core.Archive;
import net.oneandone.maven.plugins.prerelease.core.Descriptor;
import net.oneandone.maven.plugins.prerelease.core.Target;
import net.oneandone.maven.plugins.prerelease.util.Maven;
import net.oneandone.maven.plugins.prerelease.util.Subversion;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.svn.SvnNode;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.lifecycle.internal.MojoDescriptorCreator;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MavenPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Perform update-promote without a working copy. Svn url and revision are passed as arguments, not determined from a working copy.
 */
public abstract class BareBase extends Base {
    public static final String LASTEST_PRERELEASE = "LATEST_PRERELEASE";

    public static final String HEAD = "HEAD";

    private final String goal;

    protected BareBase(String goal) {
        this.goal = goal;
    }

    /**
     * Svn URL to be update-promoted.
     */
    @Parameter(property = "prerelease.svnurl", required = true)
    private String svnurl;

    /**
     * Revision to be processed. A revision number, or HEAD, or LATEST_PRERELEASE to get the last good prerelease.
     */
    @Parameter(property = "prerelease.revision", defaultValue = HEAD, required = true)
    protected String revision;

    /**
     * Specifies where to create a symlink to the prerelease checkout. No symlink is created if the prerelease has no checkout (and thus is
     * broken). No symlink is created if not specified.
     */
    @Parameter(property = "prerelease.checkoutLink")
    private String checkoutLink;

    @Override
    public void doExecute() throws Exception {
        Maven maven;
        FileNode tempCheckout;

        maven = maven();
        tempCheckout = tempCheckout();
        try {
            maven.build(tempCheckout, userProperties(), "net.oneandone.maven.plugins:prerelease:" + goal);
        } finally {
            tempCheckout.deleteTree();
        }
    }

    public Map<String, String> userProperties() {
        return new HashMap<>();
    }

    private FileNode tempCheckout() throws Exception {
        FileNode result;

        result = ((FileNode) world.getWorking()).createTempDirectory();
        Subversion.sparseCheckout(getLog(), result, svnurl, revisionForPomLoading(), false);
        return result;
    }

    private String revisionForPomLoading() {
        if (LASTEST_PRERELEASE.equals(revision)) {
            // Load latest. Fails if groupId/artifactId has changed since last good prerelease ...
            return HEAD;
        } else if (HEAD.equals(revision)) {
            // Load latest
            return HEAD;
        } else {
            return revision;
        }
    }
}
