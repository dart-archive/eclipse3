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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.dart.engine.EngineTestCase;
import com.google.dart.engine.context.AnalysisContext;
import com.google.dart.engine.element.CompilationUnitElement;
import com.google.dart.engine.element.Element;
import com.google.dart.engine.element.ElementLocation;
import com.google.dart.engine.index.Location;
import com.google.dart.engine.index.Relationship;
import com.google.dart.engine.internal.context.InstrumentedAnalysisContextImpl;
import com.google.dart.engine.internal.element.ElementLocationImpl;
import com.google.dart.engine.source.Source;
import com.google.dart.engine.source.SourceContainer;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;

public class MemoryIndexStoreImplTest extends EngineTestCase {
  /**
   * {@link Location} has no "equals" and "hasCode", so to compare locations by value we need to
   * wrap them into such object.
   */
  private static class LocationEqualsWrapper {
    private final Location location;

    LocationEqualsWrapper(Location location) {
      this.location = location;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof LocationEqualsWrapper)) {
        return false;
      }
      LocationEqualsWrapper other = (LocationEqualsWrapper) obj;
      return other.location.getOffset() == other.location.getOffset()
          && other.location.getLength() == other.location.getLength()
          && Objects.equal(other.location.getElement(), location.getElement())
          && Objects.equal(other.location.getImportPrefix(), location.getImportPrefix());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(location.getElement(), location.getOffset(), location.getLength());
    }
  }

  /**
   * Asserts that the "actual" locations have all the "expected" locations and only them.
   */
  private static void assertLocations(Location[] actual, Location... expected) {
    List<LocationEqualsWrapper> actualWrappers = wrapLocations(actual);
    List<LocationEqualsWrapper> expectedWrappers = wrapLocations(expected);
    assertThat(actualWrappers).isEqualTo(expectedWrappers);
  }

  /**
   * @return the {@link SourceContainer} mock with contains given {@link Source}s.
   */
  private static SourceContainer mockSourceContainer(Source... sources) {
    final Set<Source> sourceSet = ImmutableSet.<Source> builder().add(sources).build();
    SourceContainer container = mock(SourceContainer.class);
    when(container.contains(any(Source.class))).then(new Answer<Boolean>() {
      @Override
      public Boolean answer(InvocationOnMock invocation) throws Throwable {
        return sourceSet.contains(invocation.getArguments()[0]);
      }
    });
    return container;
  }

  /**
   * Wraps the given locations into {@link LocationEqualsWrapper}.
   */
  private static List<LocationEqualsWrapper> wrapLocations(Location[] locations) {
    List<LocationEqualsWrapper> wrappers = Lists.newArrayList();
    for (Location location : locations) {
      wrappers.add(new LocationEqualsWrapper(location));
    }
    return wrappers;
  }

  private MemoryIndexStoreImpl store = new MemoryIndexStoreImpl();
  private AnalysisContext contextA = mock(AnalysisContext.class);
  private AnalysisContext contextB = mock(AnalysisContext.class);
  private AnalysisContext contextC = mock(AnalysisContext.class);
  private ElementLocation elementLocationA = new ElementLocationImpl("elementLocationA");
  private ElementLocation elementLocationB = new ElementLocationImpl("elementLocationB");
  private ElementLocation elementLocationC = new ElementLocationImpl("elementLocationC");
  private ElementLocation elementLocationD = new ElementLocationImpl("elementLocationD");
  private Element elementA = mock(Element.class);
  private Element elementB = mock(Element.class);
  private Element elementC = mock(Element.class);
  private Element elementD = mock(Element.class);
  private Source sourceA = mock(Source.class);
  private Source sourceB = mock(Source.class);
  private Source sourceC = mock(Source.class);
  private Source sourceD = mock(Source.class);
  private CompilationUnitElement unitElementA = mock(CompilationUnitElement.class);
  private CompilationUnitElement unitElementB = mock(CompilationUnitElement.class);

  private CompilationUnitElement unitElementC = mock(CompilationUnitElement.class);

  private CompilationUnitElement unitElementD = mock(CompilationUnitElement.class);

  private Relationship relationship = Relationship.getRelationship("test-relationship");

  private Location location = mock(Location.class);

  public void test_getRelationships_hasOne() throws Exception {
    store.recordRelationship(elementA, relationship, location);
    Location[] locations = store.getRelationships(elementA, relationship);
    assertLocations(locations, location);
  }

  public void test_getRelationships_hasTwo() throws Exception {
    Location locationA = mock(Location.class);
    Location locationB = mock(Location.class);
    when(locationA.getElement()).thenReturn(elementA);
    when(locationB.getElement()).thenReturn(elementB);
    store.recordRelationship(elementA, relationship, locationA);
    store.recordRelationship(elementA, relationship, locationB);
    Location[] locations = store.getRelationships(elementA, relationship);
    assertLocations(locations, locationA, locationB);
  }

  public void test_getRelationships_noRelations() throws Exception {
    store.recordRelationship(elementA, relationship, location);
    Location[] locations = store.getRelationships(
        elementA,
        Relationship.getRelationship("no-such-relationship"));
    assertThat(locations).isEmpty();
  }

  public void test_getRelationships_twoContexts_oneSource() throws Exception {
    when(unitElementB.getSource()).thenReturn(sourceB);
    when(unitElementC.getSource()).thenReturn(sourceB);
    when(elementA.getContext()).thenReturn(contextA);
    when(elementB.getContext()).thenReturn(contextB);
    Location locationA = mock(Location.class);
    Location locationB = mock(Location.class);
    when(locationA.getElement()).thenReturn(elementA);
    when(locationB.getElement()).thenReturn(elementB);
    store.recordRelationship(elementA, relationship, locationA);
    store.recordRelationship(elementB, relationship, locationB);
    // "elementA"
    {
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationA);
    }
    // "elementB"
    {
      Location[] locations = store.getRelationships(elementB, relationship);
      assertLocations(locations, locationB);
    }
  }

  public void test_recordRelationship() throws Exception {
    // no relationships initially
    assertEquals(0, store.internalGetLocationCount());
    // record relationship
    store.recordRelationship(elementA, relationship, location);
    assertEquals(1, store.internalGetLocationCount());
  }

  public void test_recordRelationship_noElement() throws Exception {
    store.recordRelationship(null, relationship, location);
    assertEquals(0, store.internalGetLocationCount());
  }

  public void test_recordRelationship_noLocation() throws Exception {
    store.recordRelationship(elementA, relationship, null);
    assertEquals(0, store.internalGetLocationCount());
  }

  public void test_recordRelationship_noLocationElement() throws Exception {
    Element elementWithoutEnclosing = mock(Element.class);
    Location location = new Location(elementWithoutEnclosing, 0, 0, null);
    store.recordRelationship(elementA, relationship, location);
    assertEquals(0, store.internalGetLocationCount());
  }

  public void test_removeContext_instrumented() throws Exception {
    InstrumentedAnalysisContextImpl instrumentedContext = mock(InstrumentedAnalysisContextImpl.class);
    when(instrumentedContext.getBasis()).thenReturn(contextA);
    // configure B
    when(elementB.getContext()).thenReturn(contextA);
    Location locationB = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    // record: [B -> A]
    {
      store.recordRelationship(elementA, relationship, locationB);
      assertEquals(1, store.internalGetLocationCount());
      assertEquals(1, store.internalGetKeyCount());
    }
    // remove _wrapper_ of context A
    InstrumentedAnalysisContextImpl iContextA = mock(InstrumentedAnalysisContextImpl.class);
    when(iContextA.getBasis()).thenReturn(contextA);
    store.removeContext(iContextA);
    assertEquals(0, store.internalGetLocationCount());
    assertEquals(0, store.internalGetKeyCount());
  }

  public void test_removeContext_withDeclaration() throws Exception {
    when(elementB.getContext()).thenReturn(contextB);
    when(elementC.getContext()).thenReturn(contextC);
    // configure B and C
    Location locationB = mock(Location.class);
    Location locationC = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(locationC.getElement()).thenReturn(elementC);
    // record: [B -> A] and [C -> A]
    {
      store.recordRelationship(elementA, relationship, locationB);
      store.recordRelationship(elementA, relationship, locationC);
      assertEquals(2, store.internalGetLocationCount());
      assertEquals(1, store.internalGetKeyCount());
      assertEquals(0, store.internalGetLocationCount(contextA));
      assertEquals(1, store.internalGetLocationCount(contextB));
      assertEquals(1, store.internalGetLocationCount(contextC));
      // we get locations from all contexts
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationB, locationC);
    }
    // remove A, so no relations anymore
    // remove B, 1 relation and 1 location left
    store.removeContext(contextA);
    assertEquals(0, store.internalGetLocationCount());
    assertEquals(0, store.internalGetLocationCount(contextA));
    assertEquals(0, store.internalGetLocationCount(contextB));
    assertEquals(0, store.internalGetLocationCount(contextC));
    {
      Location[] locations = store.getRelationships(elementA, relationship);
      assertThat(locations).isEmpty();
    }
  }

  public void test_removeContext_withRelationship() throws Exception {
    when(elementB.getContext()).thenReturn(contextB);
    when(elementC.getContext()).thenReturn(contextC);
    // configure B and C
    Location locationB = mock(Location.class);
    Location locationC = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(locationC.getElement()).thenReturn(elementC);
    // record: [B -> A] and [C -> A]
    {
      store.recordRelationship(elementA, relationship, locationB);
      store.recordRelationship(elementA, relationship, locationC);
      assertEquals(2, store.internalGetLocationCount());
      assertEquals(0, store.internalGetLocationCount(contextA));
      assertEquals(1, store.internalGetLocationCount(contextB));
      assertEquals(1, store.internalGetLocationCount(contextC));
      // we get locations from all contexts
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationB, locationC);
    }
    // remove B, 1 relation and 1 location left
    store.removeContext(contextB);
    assertEquals(1, store.internalGetLocationCount());
    assertEquals(0, store.internalGetLocationCount(contextA));
    assertEquals(0, store.internalGetLocationCount(contextB));
    assertEquals(1, store.internalGetLocationCount(contextC));
    {
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationC);
    }
    // now remove C, empty
    store.removeContext(contextC);
    assertEquals(0, store.internalGetLocationCount(contextA));
    assertEquals(0, store.internalGetLocationCount(contextB));
    assertEquals(0, store.internalGetLocationCount(contextC));
    {
      Location[] locations = store.getRelationships(elementA, relationship);
      assertThat(locations).isEmpty();
    }
  }

  public void test_removeSource_withDeclaration() throws Exception {
    Location locationB = mock(Location.class);
    Location locationC = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(locationC.getElement()).thenReturn(elementC);
    // record: [B -> A] and [C -> A]
    {
      store.recordRelationship(elementA, relationship, locationB);
      store.recordRelationship(elementA, relationship, locationC);
      assertEquals(2, store.internalGetLocationCount());
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationB, locationC);
    }
    // remove A, no relations
    store.removeSource(contextA, sourceA);
    assertEquals(0, store.internalGetLocationCount());
  }

  public void test_removeSource_withRelationship() throws Exception {
    Location locationB = mock(Location.class);
    Location locationC = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(locationC.getElement()).thenReturn(elementC);
    // record: [B -> A] and [C -> A]
    {
      store.recordRelationship(elementA, relationship, locationB);
      store.recordRelationship(elementA, relationship, locationC);
      assertEquals(2, store.internalGetLocationCount());
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationB, locationC);
    }
    // remove B, 1 relation and 1 location left
    store.removeSource(contextA, sourceB);
    assertEquals(1, store.internalGetLocationCount());
    assertEquals(1, store.internalGetLocationCount(contextA));
    Location[] locations = store.getRelationships(elementA, relationship);
    assertLocations(locations, locationC);
  }

  public void test_removeSource_withRelationship_twoContexts_oneSource() throws Exception {
    when(elementC.getSource()).thenReturn(sourceB);
    when(elementB.getContext()).thenReturn(contextB);
    when(elementC.getContext()).thenReturn(contextC);
    // configure B and C
    Location locationB = mock(Location.class);
    Location locationC = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(locationC.getElement()).thenReturn(elementC);
    // record: [B -> A] and [C -> A]
    {
      store.recordRelationship(elementA, relationship, locationB);
      store.recordRelationship(elementA, relationship, locationC);
      assertEquals(2, store.internalGetLocationCount());
      assertEquals(1, store.internalGetLocationCount(contextB));
      assertEquals(1, store.internalGetLocationCount(contextC));
      // we get locations from all contexts
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationB, locationC);
    }
    // remove "B" in B, 1 relation and 1 location left
    store.removeSource(contextB, sourceB);
    assertEquals(1, store.internalGetLocationCount());
    assertEquals(0, store.internalGetLocationCount(contextB));
    assertEquals(1, store.internalGetLocationCount(contextC));
    {
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationC);
    }
    // now remove "B" in C, empty
    store.removeSource(contextC, sourceB);
    assertEquals(0, store.internalGetLocationCount());
    assertEquals(0, store.internalGetLocationCount(contextB));
    assertEquals(0, store.internalGetLocationCount(contextC));
    {
      Location[] locations = store.getRelationships(elementA, relationship);
      assertThat(locations).isEmpty();
    }
  }

  public void test_removeSources_withDeclaration() throws Exception {
    Location locationB = mock(Location.class);
    Location locationC = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(locationC.getElement()).thenReturn(elementC);
    // record: A, [B -> A],  [C -> A] and [B -> C]
    {
      store.recordRelationship(elementA, relationship, locationB);
      store.recordRelationship(elementA, relationship, locationC);
      store.recordRelationship(elementC, relationship, locationB);
      assertEquals(3, store.internalGetLocationCount());
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationB, locationC);
    }
    // remove container with [A], only [B -> C] left
    SourceContainer containerA = mockSourceContainer(sourceA);
    store.removeSources(contextA, containerA);
    assertEquals(1, store.internalGetLocationCount());
    assertEquals(1, store.internalGetLocationCount(contextA));
    {
      Location[] locations = store.getRelationships(elementC, relationship);
      assertLocations(locations, locationB);
    }
  }

  public void test_removeSources_withRelationship() throws Exception {
    Location locationB = mock(Location.class);
    Location locationC = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(locationC.getElement()).thenReturn(elementC);
    // record: [B -> A] and [C -> A]
    {
      store.recordRelationship(elementA, relationship, locationB);
      store.recordRelationship(elementA, relationship, locationC);
      assertEquals(2, store.internalGetLocationCount());
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationB, locationC);
    }
    // remove container with [B], 1 relation and 1 location left
    SourceContainer containerB = mockSourceContainer(sourceB);
    store.removeSources(contextA, containerB);
    assertEquals(1, store.internalGetLocationCount());
    assertEquals(1, store.internalGetLocationCount(contextA));
    Location[] locations = store.getRelationships(elementA, relationship);
    assertLocations(locations, locationC);
  }

  public void test_tryToRecord_afterContextRemove_element() throws Exception {
    Location locationB = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    // remove "A" - context of "elementA"
    store.removeContext(contextA);
    // so, this record request is ignored
    store.recordRelationship(elementA, relationship, locationB);
    assertEquals(0, store.internalGetLocationCount());
  }

  public void test_tryToRecord_afterContextRemove_location() throws Exception {
    Location locationB = mock(Location.class);
    when(locationB.getElement()).thenReturn(elementB);
    when(elementB.getContext()).thenReturn(contextB);
    // remove "B" - context of location
    store.removeContext(contextB);
    // so, this record request is ignored
    store.recordRelationship(elementA, relationship, locationB);
    assertEquals(0, store.internalGetLocationCount());
  }

  public void test_writeRead() throws Exception {
    when(contextA.getElement(eq(elementLocationA))).thenReturn(elementA);
    when(contextB.getElement(eq(elementLocationB))).thenReturn(elementB);
    when(elementA.getContext()).thenReturn(contextA);
    when(elementB.getContext()).thenReturn(contextB);
    // fill store
    Location locationA = new Location(elementA, 0, 0, null);
    Location locationB = new Location(elementB, 0, 0, null);
    store.recordRelationship(elementA, relationship, locationA);
    store.recordRelationship(elementB, relationship, locationB);
    assertEquals(2, store.internalGetKeyCount());
    assertEquals(2, store.internalGetLocationCount());
    assertLocations(store.getRelationships(elementA, relationship), locationA);
    assertLocations(store.getRelationships(elementB, relationship), locationB);
    // write
    byte[] content;
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      store.writeIndex(contextA, baos);
      content = baos.toByteArray();
    }
    // clear
    store.removeContext(contextA);
    store.removeContext(contextB);
    assertEquals(0, store.internalGetKeyCount());
    assertEquals(0, store.internalGetLocationCount());
    // we need to re-create AnalysisContext, current instance was marked as removed
    {
      contextA = mock(AnalysisContext.class);
      when(contextA.getElement(eq(elementLocationA))).thenReturn(elementA);
      when(elementA.getContext()).thenReturn(contextA);
    }
    // read
    {
      ByteArrayInputStream bais = new ByteArrayInputStream(content);
      store.readIndex(contextA, bais);
    }
    // validate after read
    assertEquals(1, store.internalGetKeyCount());
    assertEquals(1, store.internalGetLocationCount());
    {
      Location[] locations = store.getRelationships(elementA, relationship);
      assertLocations(locations, locationA);
    }
  }

  public void test_writeRead_invalidVersion() throws Exception {
    // write fake content with invalid version
    byte[] content;
    {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      dos.writeInt(-1);
      content = baos.toByteArray();
    }
    // read
    try {
      ByteArrayInputStream bais = new ByteArrayInputStream(content);
      store.readIndex(contextA, bais);
      fail();
    } catch (IOException e) {
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    when(sourceA.toString()).thenReturn("sourceA");
    when(sourceB.toString()).thenReturn("sourceB");
    when(sourceC.toString()).thenReturn("sourceC");
    when(sourceD.toString()).thenReturn("sourceD");
    when(location.getElement()).thenReturn(elementC);
    when(elementA.toString()).thenReturn("elementA");
    when(elementB.toString()).thenReturn("elementB");
    when(elementC.toString()).thenReturn("elementC");
    when(elementD.toString()).thenReturn("elementD");
    when(elementA.getContext()).thenReturn(contextA);
    when(elementB.getContext()).thenReturn(contextA);
    when(elementC.getContext()).thenReturn(contextA);
    when(elementD.getContext()).thenReturn(contextA);
    when(elementA.getLocation()).thenReturn(elementLocationA);
    when(elementB.getLocation()).thenReturn(elementLocationB);
    when(elementC.getLocation()).thenReturn(elementLocationC);
    when(elementD.getLocation()).thenReturn(elementLocationD);
    when(elementA.getEnclosingElement()).thenReturn(unitElementA);
    when(elementB.getEnclosingElement()).thenReturn(unitElementB);
    when(elementC.getEnclosingElement()).thenReturn(unitElementC);
    when(elementD.getEnclosingElement()).thenReturn(unitElementD);
    when(elementA.getSource()).thenReturn(sourceA);
    when(elementB.getSource()).thenReturn(sourceB);
    when(elementC.getSource()).thenReturn(sourceC);
    when(elementD.getSource()).thenReturn(sourceD);
    when(unitElementA.getSource()).thenReturn(sourceA);
    when(unitElementB.getSource()).thenReturn(sourceB);
    when(unitElementC.getSource()).thenReturn(sourceC);
    when(unitElementD.getSource()).thenReturn(sourceD);
  }
}
