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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.internal.DependencyContext;
import org.apache.maven.lifecycle.internal.LifecycleModuleBuilder;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.lifecycle.internal.PhaseRecorder;
import org.apache.maven.lifecycle.internal.ProjectIndex;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class FilteringMojoExecutor extends MojoExecutor {
    public static FilteringMojoExecutor install(PlexusContainer container, Filter filter) {
        LifecycleModuleBuilder builder;
        Field field;
        FilteringMojoExecutor result;

        try {
            builder = container.lookup(LifecycleModuleBuilder.class);
            field = builder.getClass().getDeclaredField("mojoExecutor");
            field.setAccessible(true);
            result = new FilteringMojoExecutor(builder, field, (MojoExecutor) field.get(builder), filter, container.lookup(DefaultLifecycles.class));
            field.set(builder, result);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    private final LifecycleModuleBuilder builder;
    private final Field field;
    private final MojoExecutor baseExecutor;

    /** what to filter for; null: nothing */
    private final Filter filter;
    private final DefaultLifecycles defaultLifecycles;

    public FilteringMojoExecutor(LifecycleModuleBuilder builder, Field field, MojoExecutor baseExecutor, Filter filter, DefaultLifecycles defaultLifecyles) {
        this.builder = builder;
        this.field = field;
        this.baseExecutor = baseExecutor;
        this.filter = filter;
        this.defaultLifecycles = defaultLifecyles;
    }

    public void uninstall() {
        try {
            field.set(builder, baseExecutor);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    //-- public MojoExecutor methods; they all just delegate - except execute for mojoExecutions, which adds filtering

    @Override
    public DependencyContext newDependencyContext(MavenSession session, List<MojoExecution> mojoExecutions) {
        return baseExecutor.newDependencyContext(session, mojoExecutions);
    }

    @Override
    public void execute(MavenSession session, List<MojoExecution> mojoExecutions, ProjectIndex projectIndex )
            throws LifecycleExecutionException {
        baseExecutor.execute(session, filter(mojoExecutions), projectIndex);
    }

    @Override
    public void execute(MavenSession session, MojoExecution mojoExecution, ProjectIndex projectIndex,
                        DependencyContext dependencyContext, PhaseRecorder phaseRecorder)
            throws LifecycleExecutionException {
        baseExecutor.execute(session, mojoExecution, projectIndex, dependencyContext, phaseRecorder);
    }


    @Override
    public void ensureDependenciesAreResolved(MojoDescriptor mojoDescriptor, MavenSession session, DependencyContext dependencyContext)
            throws LifecycleExecutionException {
        baseExecutor.ensureDependenciesAreResolved(mojoDescriptor, session, dependencyContext);
    }

    @Override
    public List<MavenProject> executeForkedExecutions(MojoExecution mojoExecution, MavenSession session, ProjectIndex projectIndex)
            throws LifecycleExecutionException
    {
        return baseExecutor.executeForkedExecutions(mojoExecution, session, projectIndex);
    }

    //--

    private List<MojoExecution> filter(List<MojoExecution> orig) {
        List<MojoExecution> lst;

        lst = new ArrayList<>();
        for (MojoExecution execution : orig) {
            if (filter.include(execution)) {
                lst.add(execution);
            }
        }
        return lst;
    }

    public static abstract class Filter {
        public abstract boolean include(MojoExecution execution);
    }
}
