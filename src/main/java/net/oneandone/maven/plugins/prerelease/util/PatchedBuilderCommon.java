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
package net.oneandone.maven.plugins.prerelease.util;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.LifecycleNotFoundException;
import org.apache.maven.lifecycle.LifecyclePhaseNotFoundException;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.lifecycle.internal.BuilderCommon;
import org.apache.maven.lifecycle.internal.ExecutionPlanItem;
import org.apache.maven.lifecycle.internal.LifecycleModuleBuilder;
import org.apache.maven.lifecycle.internal.ReactorContext;
import org.apache.maven.lifecycle.internal.TaskSegment;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PatchedBuilderCommon extends BuilderCommon {
    public static PatchedBuilderCommon install(PlexusContainer container, boolean filter) {
        LifecycleModuleBuilder builder;
        Field field;
        PatchedBuilderCommon result;

        try {
            builder = container.lookup(LifecycleModuleBuilder.class);
            field = builder.getClass().getDeclaredField("builderCommon");
            field.setAccessible(true);
            result = new PatchedBuilderCommon(builder, field, (BuilderCommon) field.get(builder), filter, container.lookup(DefaultLifecycles.class));
            field.set(builder, result);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    private final LifecycleModuleBuilder builder;
    private final Field field;
    private final BuilderCommon bc;
    private final boolean filter;
    private final DefaultLifecycles defaultLifecycles;

    public PatchedBuilderCommon(LifecycleModuleBuilder builder, Field field, BuilderCommon bc, boolean filter, DefaultLifecycles defaultLifecyles) {
        this.builder = builder;
        this.field = field;
        this.bc = bc;
        this.filter = filter;
        this.defaultLifecycles = defaultLifecyles;
    }

    public void uninstall() {
        try {
            field.set(builder, bc);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public MavenExecutionPlan resolveBuildPlan(MavenSession session, MavenProject project, TaskSegment taskSegment,
            Set<Artifact> projectArtifacts)
    throws PluginNotFoundException, PluginResolutionException, LifecyclePhaseNotFoundException,
            PluginDescriptorParsingException, MojoNotFoundException, InvalidPluginDescriptorException,
            NoPluginFoundForPrefixException, LifecycleNotFoundException, PluginVersionResolutionException,
            LifecycleExecutionException {
        MavenExecutionPlan result;
        result = bc.resolveBuildPlan(session, project, taskSegment, projectArtifacts);
        return filter? filtered(result) : result;
    }

    public void handleBuildError(ReactorContext buildContext, MavenSession rootSession,
                                 MavenProject mavenProject, Exception e, long buildStartTime) {
        bc.handleBuildError(buildContext, rootSession, mavenProject, e, buildStartTime);
    }

    private MavenExecutionPlan filtered(MavenExecutionPlan base) {
        List<ExecutionPlanItem> lst;

        lst = new ArrayList<>();
        for (ExecutionPlanItem item : base) {
            if ("deploy".equals(item.getLifecyclePhase())) {
                lst.add(item);
            }
        }
        return new MavenExecutionPlan(lst, defaultLifecycles);
    }
}
