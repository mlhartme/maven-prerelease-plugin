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

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.HashMap;
import java.util.Map;

/**
 * Perform update-promote without a working copy. Svn url and revision are passed as arguments, not determined from a working copy.
 */
@Mojo(name = "bare-update-promote", requiresProject = false)
public class BareUpdatePromote extends BareBase {
    /**
     * Passed to the promote goal when specified.
     */
    @Parameter
    protected String createTagMessage;

    /**
     * Passed to the promote goal when specified.
     */
    @Parameter
    protected String revertTagMessage;

    /**
     * Passed to the promote goal when specified.
     */
    @Parameter
    protected String nextIterationMessage;

    public BareUpdatePromote() {
        super("update-promote");
    }

    @Override
    public Map<String, String> userProperties() {
        Map<String, String> result;

        result = new HashMap<>();
        if (createTagMessage != null) {
            result.put("prerelease.createTagMessage", createTagMessage);
        }
        if (revertTagMessage != null) {
            result.put("prerelease.revertTagMessage", revertTagMessage);
        }
        if (nextIterationMessage != null) {
            result.put("prerelease.nextIterationMessage", nextIterationMessage);
        }
        return result;
    }
}
