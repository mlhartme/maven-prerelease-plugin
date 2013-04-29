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

import net.oneandone.maven.plugins.prerelease.Plugin;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class ScheduleTest {
    @Test
    public void empty() {
        Properties properties;
        Schedule orig;

        properties = new Properties();
        orig = new Schedule();
        orig.save(properties, "");
        assertEquals(orig, Schedule.load(properties, ""));
    }

    @Test
    public void normal() {
        Properties properties;
        Schedule orig;

        properties = new Properties();
        orig = new Schedule();
        orig.add(new Plugin("group", "artifact", "goal", Plugin.Phase.BEFORE_PROMOTE, "skip"));
        orig.add(new Plugin("group2", "artifact2", "goal2", Plugin.Phase.AFTER_PROMOTE, "skip2"));
        orig.save(properties, "");
        assertEquals(orig, Schedule.load(properties, ""));
    }
}
