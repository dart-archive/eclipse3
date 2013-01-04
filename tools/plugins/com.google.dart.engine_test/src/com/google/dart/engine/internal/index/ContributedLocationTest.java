/*
 * Copyright (c) 2012, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.engine.internal.index;

import com.google.common.collect.Lists;
import com.google.dart.engine.EngineTestCase;
import com.google.dart.engine.index.Location;
import com.google.dart.engine.source.Source;

import static org.mockito.Mockito.mock;

import java.util.List;

public class ContributedLocationTest extends EngineTestCase {
  public void test_new() throws Exception {
    List<ContributedLocation> owner = Lists.newArrayList();
    Source contributor = mock(Source.class);
    Location location = mock(Location.class);
    ContributedLocation contributedLocation = new ContributedLocation(owner, contributor, location);
    assertSame(owner, contributedLocation.getOwner());
    assertSame(contributor, contributedLocation.getContributor());
    assertSame(location, contributedLocation.getLocation());
  }
}
