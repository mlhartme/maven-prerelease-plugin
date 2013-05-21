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
package net.oneandone.maven.plugins.prerelease.core;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;


public class BaseExecutionListener implements ExecutionListener {
    private final ExecutionListener base;

    public BaseExecutionListener(ExecutionListener base) {
        this.base = base;
    }

    @Override
    public void projectDiscoveryStarted(ExecutionEvent event) {
        base.projectDiscoveryStarted(event);
    }

    @Override
    public void sessionStarted(ExecutionEvent event) {
        base.sessionStarted(event);
    }

    @Override
    public void sessionEnded(ExecutionEvent event) {
        base.sessionEnded(event);
    }

    @Override
    public void projectSkipped(ExecutionEvent event) {
        base.projectSkipped(event);
    }

    @Override
    public void projectStarted(ExecutionEvent event) {
        base.projectStarted(event);
    }

    @Override
    public void projectSucceeded(ExecutionEvent event) {
        base.projectSucceeded(event);
    }

    @Override
    public void projectFailed(ExecutionEvent event) {
        base.projectFailed(event);
    }

    @Override
    public void mojoSkipped(ExecutionEvent event) {
        base.mojoSkipped(event);
    }

    @Override
    public void mojoStarted(ExecutionEvent event) {
        base.mojoStarted(event);
    }

    @Override
    public void mojoSucceeded(ExecutionEvent event) {
        base.mojoSucceeded(event);
    }

    @Override
    public void mojoFailed(ExecutionEvent event) {
        base.mojoFailed(event);
    }

    @Override
    public void forkStarted(ExecutionEvent event) {
        base.forkStarted(event);
    }

    @Override
    public void forkSucceeded(ExecutionEvent event) {
        base.forkSucceeded(event);
    }

    @Override
    public void forkFailed(ExecutionEvent event) {
        base.forkFailed(event);
    }

    @Override
    public void forkedProjectStarted(ExecutionEvent event) {
        base.forkedProjectStarted(event);
    }

    @Override
    public void forkedProjectSucceeded(ExecutionEvent event) {
        base.forkedProjectSucceeded(event);
    }

    @Override
    public void forkedProjectFailed(ExecutionEvent event) {
        base.forkedProjectFailed(event);
    }
}
