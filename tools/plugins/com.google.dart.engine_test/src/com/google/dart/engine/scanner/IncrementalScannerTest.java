/*
 * Copyright (c) 2013, the Dart project authors.
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
package com.google.dart.engine.scanner;

import com.google.dart.engine.EngineTestCase;
import com.google.dart.engine.error.GatheringErrorListener;
import com.google.dart.engine.source.Source;
import com.google.dart.engine.source.TestSource;

public class IncrementalScannerTest extends EngineTestCase {

  public void test_rescan_addedBeforeIdentifier1() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "xa + b;");
    assertEquals("xa", scanner.getFirstToken().getLexeme());
    assertEquals("xa", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_addedBeforeIdentifier2() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "a + xb;");
    assertEquals("xb", scanner.getFirstToken().getLexeme());
    assertEquals("xb", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_addedNewIdentifier1() {
    IncrementalScanner scanner = assertTokens(//
        "a;  c;",
        "a; b c;");
    assertEquals("b", scanner.getFirstToken().getLexeme());
    assertEquals("b", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_addedNewIdentifier2() {
    IncrementalScanner scanner = assertTokens(//
        "a;  c;",
        "a;b  c;");
    assertEquals("b", scanner.getFirstToken().getLexeme());
    assertEquals("b", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_addedToIdentifier1() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "abs + b;");
    assertEquals("abs", scanner.getFirstToken().getLexeme());
    assertEquals("abs", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_addedToIdentifier2() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "a + by;");
    assertEquals("by", scanner.getFirstToken().getLexeme());
    assertEquals("by", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_appendWhitespace1() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "a + b; ");
    assertNull(scanner.getFirstToken());
    assertNull(scanner.getLastToken());
  }

  public void test_rescan_appendWhitespace2() {
    IncrementalScanner scanner = assertTokens(//
        "a + b; ",
        "a + b;  ");
    assertNull(scanner.getFirstToken());
    assertNull(scanner.getLastToken());
  }

  public void test_rescan_insertedPeriod() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "a + b.;");
    assertEquals(".", scanner.getFirstToken().getLexeme());
    assertEquals(".", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_insertedPeriodBetweenIdentifiers1() {
    IncrementalScanner scanner = assertTokens(//
        "a b;",
        "a. b;");
    assertEquals(".", scanner.getFirstToken().getLexeme());
    assertEquals(".", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_insertedPeriodBetweenIdentifiers2() {
    IncrementalScanner scanner = assertTokens(//
        "a b;",
        "a .b;");
    assertEquals(".", scanner.getFirstToken().getLexeme());
    assertEquals(".", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_insertedPeriodBetweenIdentifiers3() {
    IncrementalScanner scanner = assertTokens(//
        "a  b;",
        "a . b;");
    assertEquals(".", scanner.getFirstToken().getLexeme());
    assertEquals(".", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_insertedPeriodIdentifier() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "a + b.x;");
    assertEquals(".", scanner.getFirstToken().getLexeme());
    assertEquals("x", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_insertedPeriodInsideExistingIdentifier() {
    IncrementalScanner scanner = assertTokens(//
        "ab;",
        "a.b;");
    assertEquals("a", scanner.getFirstToken().getLexeme());
    assertEquals("b", scanner.getLastToken().getLexeme());
  }

  public void test_rescan_insertLeadingWhitespace() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        " a + b;");
    assertNull(scanner.getFirstToken());
    assertNull(scanner.getLastToken());
  }

  public void test_rescan_insertWhitespace() {
    IncrementalScanner scanner = assertTokens(//
        "a + b;",
        "a  + b;");
    assertNull(scanner.getFirstToken());
    assertNull(scanner.getLastToken());
  }

  public void test_rescan_insertWhitespaceWithMultipleComments() {
    IncrementalScanner scanner = assertTokens(//
        createSource(//
            "//comment",
            "//comment2",
            "a + b;"),
        createSource(//
            "//comment",
            "//comment2",
            "a  + b;"));
    assertNull(scanner.getFirstToken());
    assertNull(scanner.getLastToken());
  }

  public void test_rescan_oneFunctionToTwo() {
    IncrementalScanner scanner = assertTokens(//
        "f() {}",
        "f() => 0; g() {}");
    assertEquals("=>", scanner.getFirstToken().getLexeme());
    assertEquals(")", scanner.getLastToken().getLexeme());
  }

  private IncrementalScanner assertTokens(String originalContents, String modifiedContents) {
    //
    // Compute the location of the deleted and inserted text.
    //
    int originalLength = originalContents.length();
    int modifiedLength = modifiedContents.length();
    int replaceStart = 0;
    while (replaceStart < originalLength && replaceStart < modifiedLength
        && originalContents.charAt(replaceStart) == modifiedContents.charAt(replaceStart)) {
      replaceStart++;
    }
    int originalEnd = originalLength - 1;
    int modifiedEnd = modifiedLength - 1;
    while (originalEnd >= replaceStart && modifiedEnd >= replaceStart
        && originalContents.charAt(originalEnd) == modifiedContents.charAt(modifiedEnd)) {
      originalEnd--;
      modifiedEnd--;
    }
    Source source = new TestSource();
    //
    // Scan the original contents.
    //
    GatheringErrorListener originalListener = new GatheringErrorListener();
    Scanner originalScanner = new Scanner(
        source,
        new CharSequenceReader(originalContents),
        originalListener);
    Token originalToken = originalScanner.tokenize();
    assertNotNull(originalToken);
    //
    // Scan the modified contents.
    //
    GatheringErrorListener modifiedListener = new GatheringErrorListener();
    Scanner modifiedScanner = new Scanner(
        source,
        new CharSequenceReader(modifiedContents),
        modifiedListener);
    Token modifiedToken = modifiedScanner.tokenize();
    assertNotNull(modifiedToken);
    //
    // Incrementally scan the modified contents.
    //
    GatheringErrorListener incrementalListener = new GatheringErrorListener();
    IncrementalScanner incrementalScanner = new IncrementalScanner(source, new CharSequenceReader(
        modifiedContents), incrementalListener);
    Token incrementalToken = incrementalScanner.rescan(originalToken, replaceStart, originalEnd
        - replaceStart + 1, modifiedEnd - replaceStart + 1);
    //
    // Validate that the results of the incremental scan are the same as the full scan of the
    // modified source.
    //
    assertNotNull(incrementalToken);
    while (incrementalToken.getType() != TokenType.EOF && modifiedToken.getType() != TokenType.EOF) {
      assertSame("Wrong type for token", modifiedToken.getType(), incrementalToken.getType());
      assertEquals(
          "Wrong offset for token",
          modifiedToken.getOffset(),
          incrementalToken.getOffset());
      assertEquals(
          "Wrong length for token",
          modifiedToken.getLength(),
          incrementalToken.getLength());
      assertEquals(
          "Wrong lexeme for token",
          modifiedToken.getLexeme(),
          incrementalToken.getLexeme());
      incrementalToken = incrementalToken.getNext();
      modifiedToken = modifiedToken.getNext();
    }
    assertSame("Too many tokens", TokenType.EOF, incrementalToken.getType());
    assertSame("Not enough tokens", TokenType.EOF, modifiedToken.getType());
    // TODO(brianwilkerson) Verify that the errors are correct?

    return incrementalScanner;
  }
}
