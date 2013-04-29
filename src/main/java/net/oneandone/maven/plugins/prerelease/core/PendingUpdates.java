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

import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.util.Strings;

import java.util.List;

public class PendingUpdates extends Exception {
    public final long revision;
    public final List<String> updates;

    public PendingUpdates(long revision, List<String> updates) {
        super("pending updates for revision " + revision + ":\n"
                + Strings.indent(Separator.RAW_LINE.join(updates), "  ") + "\nRun svn update.");
        this.revision = revision;
        this.updates = updates;
    }
}
