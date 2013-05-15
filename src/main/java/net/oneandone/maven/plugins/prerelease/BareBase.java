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
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.svn.SvnNode;
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

/**
 * Perform update-promote without a working copy. Svn url and revision are passed as arguments, not determined from a working copy.
 */
public abstract class BareBase extends Base {
    private static final String LASTEST_PRERELEASE = "LATEST_PRERELEASE";

    private static final String LAST_CHANGED = "LAST_CHANGED";

    /**
     * Svn URL to be update-promoted.
     */
    @Parameter(property = "prerelease.svnurl", required = true)
    private String svnurl;

    /**
     * Revision to be processed. A revision number, or LATEST_PRERELEASE to get the last good prerelease,
     * or LAST_CHANGED.
     */
    @Parameter(property = "prerelease.revision", defaultValue = LAST_CHANGED, required = true)
    private String revision;

    /**
     * Specifies where to create a symlink to the prerelease checkout. No symlink is created if the prerelease has no checkout (and thus is
     * broken). No symlink is created if not specified.
     */
    @Parameter(property = "prerelease.checkoutLink")
    private String checkoutLink;

    @Component
    private MavenPluginManager pluginManager;

    @Component
    private MojoDescriptorCreator mojoDescriptorCreator;

    @Override
    public void doExecute() throws Exception {
        Maven maven;
        MavenProject project;
        FileNode archiveDirectory;
        Descriptor descriptor;
        Target target;

        maven = maven();
        project = load(maven);
        getLog().info("project " + project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion());
        archiveDirectory = Archive.directory(world.file(archiveRoot), project);
        descriptor = Descriptor.create(project, revisionForDescriptor(archiveDirectory)).check(world, project);
        getLog().info("revision: " + descriptor.revision);
        if (!descriptor.svnOrig.equals(svnurl)) {
            throw new MojoExecutionException("svn mismatch: " + svnurl + " vs " + descriptor.svnOrig);
        }
        try (Archive archive = Archive.open(archiveDirectory, lockTimeout, getLog())) {
            target = archive.target(descriptor.revision);
            try {
                doExecute(maven, project, target, descriptor);
            } finally {
                target.checkoutLinkOpt(checkoutLink);
            }
        }
    }

    public abstract void doExecute(Maven maven, MavenProject project, Target target, Descriptor descriptor) throws Exception;

    protected org.apache.maven.plugin.Mojo mojo(MavenProject project, String key) throws Exception {
        MavenProject oldProject;
        MojoDescriptor descriptor;
        MojoExecution execution;
        ClassRealm pluginRealm;
        ClassLoader oldClassLoader;

        oldProject = session.getCurrentProject();
        project.setPluginArtifactRepositories(project.getRemoteArtifactRepositories()); // TODO ...
        session.setCurrentProject(project);
        try {
            descriptor = mojoDescriptorCreator.getMojoDescriptor(key, session, project);
            execution = new MojoExecution(descriptor, "default-cli", MojoExecution.Source.CLI);
            configure(project, execution, true);
            finalizeMojoConfiguration(execution);
            pluginRealm = getPluginRealm(descriptor.getPluginDescriptor());
            oldClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(pluginRealm);
            try {
                return pluginManager.getConfiguredMojo(org.apache.maven.plugin.Mojo.class, session, execution);
            } finally {
                Thread.currentThread().setContextClassLoader(oldClassLoader);
            }
        } finally {
            session.setCurrentProject(oldProject);
        }
    }

    public ClassRealm getPluginRealm(PluginDescriptor pluginDescriptor) throws PluginResolutionException, PluginManagerException {
        ClassRealm pluginRealm;

        pluginRealm = pluginDescriptor.getClassRealm();
        if (pluginRealm != null) {
            return pluginRealm;
        }
        pluginManager.setupPluginRealm(pluginDescriptor, session, null, null, null);
        return pluginDescriptor.getClassRealm();
    }


    private MavenProject load(Maven maven) throws Exception {
        SvnNode pom;
        FileNode tmp;
        OutputStream tmpStream;

        pom = (SvnNode) world.node("svn:" + svnurl).join("pom.xml");
        tmp = world.getTemp().createTempFile();
        tmpStream = tmp.createOutputStream();
        pom.doWriteTo(revisionForPomLoading(), tmpStream);
        tmpStream.close();
        try {
            return maven.loadPom(tmp);
        } finally {
            tmp.deleteFile();
        }
    }


    private long revisionForPomLoading() {
        if (LASTEST_PRERELEASE.equals(revision)) {
            // Load latest. Fails if groupId/artifactId has changed since last good prerelease ...
            return -1;
        } else if (LAST_CHANGED.equals(revision)) {
            // Load latest
            return -1;
        } else {
            return Long.parseLong(revision);
        }
    }

    private long revisionForDescriptor(FileNode archiveDirectory) throws MojoExecutionException, IOException, SVNException {
        long result;

        if (LASTEST_PRERELEASE.equals(revision)) {
            result = Archive.latest(archiveDirectory);
            if (result == -1) {
                throw new MojoExecutionException("no existing prerelease");
            }
            return result;
        } else if (LAST_CHANGED.equals(revision)) {
            return ((SvnNode) world.validNode("svn:" + svnurl)).getLatestRevision();
        } else {
            return Long.parseLong(revision);
        }
    }

    //--

    // from DefaultLifecycleExecutionPlanCalculator
    private void configure(MavenProject project, MojoExecution mojoExecution, boolean allowPluginLevelConfig) {
        String g = mojoExecution.getGroupId();

        String a = mojoExecution.getArtifactId();

        Plugin plugin = findPlugin(g, a, project.getBuildPlugins());

        if (plugin == null && project.getPluginManagement() != null) {
            plugin = findPlugin(g, a, project.getPluginManagement().getPlugins());
        }

        if (plugin != null) {
            PluginExecution pluginExecution = findPluginExecution(mojoExecution.getExecutionId(), plugin.getExecutions());

            Xpp3Dom pomConfiguration = null;

            if (pluginExecution != null) {
                pomConfiguration = (Xpp3Dom) pluginExecution.getConfiguration();
            } else if (allowPluginLevelConfig) {
                pomConfiguration = (Xpp3Dom) plugin.getConfiguration();
            }

            Xpp3Dom mojoConfiguration = (pomConfiguration != null) ? new Xpp3Dom(pomConfiguration) : null;

            mojoConfiguration = Xpp3Dom.mergeXpp3Dom(mojoExecution.getConfiguration(), mojoConfiguration);

            mojoExecution.setConfiguration(mojoConfiguration);
        }
    }

    private Plugin findPlugin(String groupId, String artifactId, Collection<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            if (artifactId.equals(plugin.getArtifactId()) && groupId.equals(plugin.getGroupId())) {
                return plugin;
            }
        }

        return null;
    }

    private PluginExecution findPluginExecution(String executionId, Collection<PluginExecution> executions) {
        if (StringUtils.isNotEmpty(executionId)) {
            for (PluginExecution execution : executions) {
                if (executionId.equals(execution.getId())) {
                    return execution;
                }
            }
        }
        return null;
    }

    //--

    /**
     * Post-processes the effective configuration for the specified mojo execution. This step discards all parameters
     * from the configuration that are not applicable to the mojo and injects the default values for any missing
     * parameters.
     *
     * @param mojoExecution The mojo execution whose configuration should be finalized, must not be {@code null}.
     */
    private void finalizeMojoConfiguration(MojoExecution mojoExecution) {
        MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();

        Xpp3Dom executionConfiguration = mojoExecution.getConfiguration();
        if (executionConfiguration == null) {
            executionConfiguration = new Xpp3Dom("configuration");
        }

        Xpp3Dom defaultConfiguration = getMojoConfiguration(mojoDescriptor);

        Xpp3Dom finalConfiguration = new Xpp3Dom("configuration");

        if (mojoDescriptor.getParameters() != null) {
            for (org.apache.maven.plugin.descriptor.Parameter parameter : mojoDescriptor.getParameters()) {
                Xpp3Dom parameterConfiguration = executionConfiguration.getChild(parameter.getName());

                if (parameterConfiguration == null) {
                    parameterConfiguration = executionConfiguration.getChild(parameter.getAlias());
                }

                Xpp3Dom parameterDefaults = defaultConfiguration.getChild(parameter.getName());

                parameterConfiguration = Xpp3Dom.mergeXpp3Dom(parameterConfiguration, parameterDefaults, Boolean.TRUE);

                if (parameterConfiguration != null) {
                    parameterConfiguration = new Xpp3Dom(parameterConfiguration, parameter.getName());

                    if (StringUtils.isEmpty(parameterConfiguration.getAttribute("implementation"))
                            && StringUtils.isNotEmpty(parameter.getImplementation())) {
                        parameterConfiguration.setAttribute("implementation", parameter.getImplementation());
                    }

                    finalConfiguration.addChild(parameterConfiguration);
                }
            }
        }

        mojoExecution.setConfiguration(finalConfiguration);
    }

    private Xpp3Dom getMojoConfiguration(MojoDescriptor mojoDescriptor) {
        return MojoDescriptorCreator.convert(mojoDescriptor);
    }

}
