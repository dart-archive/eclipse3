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
package com.google.dart.tools.internal.corext.refactoring.code;

import com.google.common.collect.Lists;
import com.google.dart.compiler.ast.ASTVisitor;
import com.google.dart.compiler.ast.DartBinaryExpression;
import com.google.dart.compiler.ast.DartExpression;
import com.google.dart.compiler.ast.DartNode;
import com.google.dart.compiler.ast.DartUnit;
import com.google.dart.compiler.common.HasSourceInfo;
import com.google.dart.compiler.common.SourceInfo;
import com.google.dart.compiler.parser.DartScanner;
import com.google.dart.compiler.parser.Token;
import com.google.dart.compiler.type.Type;
import com.google.dart.compiler.util.apache.StringUtils;
import com.google.dart.engine.scanner.StringScanner;
import com.google.dart.engine.scanner.TokenType;
import com.google.dart.tools.core.buffer.Buffer;
import com.google.dart.tools.core.internal.model.SourceRangeImpl;
import com.google.dart.tools.core.internal.util.SourceRangeUtils;
import com.google.dart.tools.core.model.CompilationUnit;
import com.google.dart.tools.core.model.DartModelException;
import com.google.dart.tools.core.model.SourceRange;
import com.google.dart.tools.core.utilities.compiler.DartCompilerUtilities;
import com.google.dart.tools.internal.corext.SourceRangeFactory;
import com.google.dart.tools.internal.corext.refactoring.util.ExecutionUtils;
import com.google.dart.tools.internal.corext.refactoring.util.RunnableEx;
import com.google.dart.tools.internal.corext.refactoring.util.RunnableObjectEx;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.ReplaceEdit;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Extract Local Variable (from selected expression inside method or initializer).
 * 
 * @coverage dart.editor.ui.refactoring.core
 */
public class ExtractUtils {

  /**
   * The default end-of-line marker for the current platform. This value should (almost) never be
   * used directly. The end-of-line marker should always be queried from {@link Buffer} because it
   * can differ from the platform default in some situations.
   */
  public static final String DEFAULT_END_OF_LINE = System.getProperty("line.separator", "\n");

  /**
   * Sorts given {@link ReplaceEdit}s and returns updated {@link String} with applied replaces.
   */
  public static String applyReplaceEdits(String s, List<ReplaceEdit> edits) {
    Collections.sort(edits, new Comparator<ReplaceEdit>() {
      @Override
      public int compare(ReplaceEdit o1, ReplaceEdit o2) {
        return o2.getOffset() - o1.getOffset();
      }
    });
    for (ReplaceEdit replaceEdit : edits) {
      String beforeEdit = s.substring(0, replaceEdit.getOffset());
      String afterEdit = s.substring(replaceEdit.getExclusiveEnd());
      s = beforeEdit + replaceEdit.getText() + afterEdit;
    }
    return s;
  }

  /**
   * @return <code>true</code> if given {@link SourceRange} covers given {@link DartNode}.
   */
  public static boolean covers(SourceRange r, DartNode node) {
    return SourceRangeUtils.covers(r, new SourceRangeImpl(node));
  }

  /**
   * @return the actual type source of the given {@link DartExpression}, may be <code>null</code> if
   *         can not be resolved, should be treated as <code>Dynamic</code>.
   */
  public static String getTypeSource(DartExpression expression) {
    if (expression == null) {
      return null;
    }
    Type type = expression.getType();
    String typeSource = getTypeSource(type);
    if ("Dynamic".equals(typeSource)) {
      return null;
    }
    return typeSource;
  }

  /**
   * @return the source of the given {@link Type}.
   */
  public static String getTypeSource(Type type) {
    String typeSource = type.toString();
    typeSource = StringUtils.replace(typeSource, "<dynamic>", "Dynamic");
    typeSource = StringUtils.replace(typeSource, "<Dynamic>", "");
    typeSource = StringUtils.replace(typeSource, "<Dynamic, Dynamic>", "");
    return typeSource;
  }

  /**
   * @return <code>true</code> if given {@link DartBinaryExpression} uses associative operator and
   *         its arguments are not {@link DartBinaryExpression} or are also associative.
   */
  public static boolean isAssociative(DartBinaryExpression expression) {
    if (isAssociativeOperator(expression.getOperator())) {
      DartExpression arg1 = expression.getArg1();
      DartExpression arg2 = expression.getArg2();
      if (arg1 instanceof DartBinaryExpression && !isAssociative((DartBinaryExpression) arg1)) {
        return false;
      }
      if (arg2 instanceof DartBinaryExpression && !isAssociative((DartBinaryExpression) arg2)) {
        return false;
      }
      return true;
    }
    return false;
  }

  public static boolean rangeEndsBetween(SourceRange range, DartNode first, DartNode next) {
    int pos = SourceRangeUtils.getEnd(range);
    return first.getSourceInfo().getEnd() <= pos && pos <= next.getSourceInfo().getOffset();
  }

  public static boolean rangeStartsBetween(SourceRange range, DartNode first, DartNode next) {
    int pos = range.getOffset();
    return first.getSourceInfo().getEnd() <= pos && pos <= next.getSourceInfo().getOffset();
  }

  /**
   * @return {@link com.google.dart.engine.scanner.Token}s of the given Dart source.
   */
  public static List<com.google.dart.engine.scanner.Token> tokenizeSource(final String s) {
    final List<com.google.dart.engine.scanner.Token> tokens = Lists.newArrayList();
    ExecutionUtils.runIgnore(new RunnableEx() {
      @Override
      public void run() throws Exception {
        StringScanner scanner = new StringScanner(null, s, null);
        com.google.dart.engine.scanner.Token token = scanner.tokenize();
        while (token.getType() != TokenType.EOF) {
          tokens.add(token);
          token = token.getNext();
        }
      }
    });
    return tokens;
  }

  /**
   * @return {@link DartExpression}s from <code>operands</code> which are completely covered by
   *         given {@link SourceRange}. Range should start and end between given
   *         {@link DartExpression}s.
   */
  private static List<DartExpression> getOperandsForSourceRange(List<DartExpression> operands,
      SourceRange range) {
    Assert.isTrue(!operands.isEmpty());
    List<DartExpression> subOperands = Lists.newArrayList();
    // track range enter/exit
    boolean entered = false;
    boolean exited = false;
    // may be range starts exactly on first operand
    if (range.getOffset() == operands.get(0).getSourceInfo().getOffset()) {
      entered = true;
    }
    // iterate over gaps between operands
    for (int i = 0; i < operands.size() - 1; i++) {
      DartExpression operand = operands.get(i);
      DartExpression nextOperand = operands.get(i + 1);
      // add operand, if already entered range
      if (entered) {
        subOperands.add(operand);
        // may be last operand in range
        if (ExtractUtils.rangeEndsBetween(range, operand, nextOperand)) {
          exited = true;
        }
      } else {
        // may be first operand in range
        if (ExtractUtils.rangeStartsBetween(range, operand, nextOperand)) {
          entered = true;
        }
      }
    }
    // check if last operand is in range
    DartExpression lastGroupMember = operands.get(operands.size() - 1);
    if (SourceRangeUtils.getEnd(range) == lastGroupMember.getSourceInfo().getEnd()) {
      subOperands.add(lastGroupMember);
      exited = true;
    }
    // we expect that range covers only given operands
    if (!exited) {
      return Lists.newArrayList();
    }
    // done
    return subOperands;
  }

  /**
   * @return all operands of the given {@link DartBinaryExpression} and its children with the same
   *         operator.
   */
  private static List<DartExpression> getOperandsInOrderFor(final DartBinaryExpression groupRoot) {
    final List<DartExpression> operands = Lists.newArrayList();
    groupRoot.accept(new ASTVisitor<Void>() {
      @Override
      public Void visitExpression(DartExpression node) {
        if (node instanceof DartBinaryExpression
            && ((DartBinaryExpression) node).getOperator() == groupRoot.getOperator()) {
          return super.visitNode(node);
        }
        operands.add(node);
        return null;
      }
    });
    return operands;
  }

  /**
   * @return the {@link SourceRange} which covers given ordered list of operands.
   */
  private static SourceRange getRangeOfOperands(List<DartExpression> operands) {
    DartExpression first = operands.get(0);
    DartExpression last = operands.get(operands.size() - 1);
    int offset = first.getSourceInfo().getOffset();
    int length = last.getSourceInfo().getEnd() - offset;
    return new SourceRangeImpl(offset, length);
  }

  /**
   * @return <code>true</code> if given operator {@link Token} is associative.
   */
  private static boolean isAssociativeOperator(Token operator) {
    return operator == Token.ADD || operator == Token.MUL || operator == Token.BIT_XOR
        || operator == Token.BIT_OR || operator == Token.BIT_AND || operator == Token.OR
        || operator == Token.AND;
  }

  @SuppressWarnings("unused")
  private final CompilationUnit unit;

  private final Buffer buffer;

  private final DartUnit unitNode;

  private String endOfLine;

  public ExtractUtils(CompilationUnit unit) throws DartModelException {
    this.unit = unit;
    this.buffer = unit.getBuffer();
    this.unitNode = DartCompilerUtilities.resolveUnit(unit);
  }

  public ExtractUtils(CompilationUnit unit, DartUnit unitNode) throws DartModelException {
    this.unit = unit;
    this.buffer = unit.getBuffer();
    this.unitNode = unitNode;
  }

  /**
   * @return the EOL to use for this {@link CompilationUnit}.
   */
  public String getEndOfLine() {
    if (endOfLine == null) {
      endOfLine = ExecutionUtils.runObjectIgnore(new RunnableObjectEx<String>() {
        @Override
        public String runObject() throws Exception {
          // find first EOL
          IDocument document = new Document(buffer.getContents());
          int numberOfLines = document.getNumberOfLines();
          for (int i = 0; i < numberOfLines; i++) {
            String delimiter = document.getLineDelimiter(i);
            if (delimiter != null) {
              return delimiter;
            }
          }
          // no EOL, use default
          return DEFAULT_END_OF_LINE;
        }
      }, DEFAULT_END_OF_LINE);
    }
    return endOfLine;
  }

  /**
   * @return the default indentation with given level.
   */
  public String getIndent(int level) {
    return StringUtils.repeat("  ", level);
  }

  /**
   * @return the index of the first not space or tab on the right from the given one, if form
   *         statement or method end, then this is in most cases start of the next line.
   */
  public int getLineNextIndex(int index) {
    int length = buffer.getLength();
    // skip whitespace characters
    while (index < length) {
      char c = buffer.getChar(index);
      if (!Character.isWhitespace(c) || c == '\r' || c == '\n') {
        break;
      }
      index++;
    }
    // skip single \r
    if (index < length && buffer.getChar(index) == '\r') {
      index++;
    }
    // skip single \n
    if (index < length && buffer.getChar(index) == '\n') {
      index++;
    }
    // done
    return index;
  }

  /**
   * @return the index of the last space or tab on the left from the given one, if form statement or
   *         method start, then this is in most cases start of the line.
   */
  public int getLineThisIndex(int index) {
    while (index > 0) {
      char c = buffer.getChar(index - 1);
      if (c != ' ' && c != '\t') {
        break;
      }
      index--;
    }
    return index;
  }

  /**
   * @return the line prefix consisting of spaces and tabs on the left from the given
   *         {@link DartNode}.
   */
  public String getNodePrefix(DartNode node) {
    int endIndex = node.getSourceInfo().getOffset();
    return getPrefix(endIndex);
  }

  /**
   * @return the line prefix consisting of spaces and tabs on the left from the given offset.
   */
  public String getPrefix(int endIndex) {
    int startIndex = getLineThisIndex(endIndex);
    return buffer.getText(startIndex, endIndex - startIndex);
  }

  /**
   * @return the given range of text from {@link Buffer}.
   */
  public String getText(HasSourceInfo hasSourceInfo) {
    SourceInfo sourceInfo = hasSourceInfo.getSourceInfo();
    return getText(sourceInfo.getOffset(), sourceInfo.getLength());
  }

  /**
   * @return the given range of text from {@link Buffer}.
   */
  public String getText(int offset, int length) {
    return buffer.getText(offset, length);
  }

  /**
   * @return the resolved {@link DartUnit}.
   */
  public DartUnit getUnitNode() {
    return unitNode;
  }

  public boolean rangeIncludesNonWhitespaceOutsideNode(SourceRange range, DartNode node)
      throws DartModelException {
    return rangeIncludesNonWhitespaceOutsideRange(range, SourceRangeFactory.create(node));
  }

  public boolean rangeIncludesNonWhitespaceOutsideRange(SourceRange selection, SourceRange node) {
    // selection should cover node
    if (!SourceRangeUtils.covers(selection, node)) {
      return false;
    }
    // non-whitespace between selection start and node start
    if (!isJustWhitespace(selection.getOffset(), node.getOffset())) {
      return true;
    }
    // non-whitespace after node
    if (!isJustWhitespaceOrComment(node.getOffset() + node.getLength(), selection.getOffset()
        + selection.getLength())) {
      return true;
    }
    // only whitespace in selection around node
    return false;
  }

  /**
   * @return <code>true</code> if given range of {@link DartBinaryExpression} can be extracted.
   */
  public boolean validateBinaryExpressionRange(DartBinaryExpression binaryExpression,
      SourceRange range) {
    // only parts of associative expression are safe to extract
    if (!ExtractUtils.isAssociative(binaryExpression)) {
      return false;
    }
    // prepare selected operands
    List<DartExpression> operands = getOperandsInOrderFor(binaryExpression);
    List<DartExpression> subOperands = getOperandsForSourceRange(operands, range);
    // if empty, then something wrong with selection
    if (subOperands.isEmpty()) {
      return false;
    }
    // may be some punctuation included into selection - operators, braces, etc
    if (selectionIncludesNonWhitespaceOutsideOperands(subOperands, range)) {
      return false;
    }
    // OK
    return true;
  }

  /**
   * @return <code>true</code> if selection range contains only whitespace.
   */
  private boolean isJustWhitespace(int start, int end) {
    if (start == end) {
      return true;
    }
    Assert.isTrue(start <= end);
    return getText(start, end - start).trim().length() == 0;
  }

  /**
   * @return <code>true</code> if selection range contains only whitespace or comments
   */
  private boolean isJustWhitespaceOrComment(int start, int end) {
    if (start == end) {
      return true;
    }
    // prepare text
    Assert.isTrue(start <= end);
    final String trimmedText = buffer.getText(start, end - start).trim();
    // may be whitespace
    if (trimmedText.isEmpty()) {
      return true;
    }
    // may be comment
    return ExecutionUtils.runObjectIgnore(new RunnableObjectEx<Boolean>() {
      @Override
      public Boolean runObject() throws Exception {
        DartScanner scanner = new DartScanner(trimmedText);
        return scanner.next() == Token.EOS;
      }
    }, false).booleanValue();
  }

  private boolean selectionIncludesNonWhitespaceOutsideOperands(List<DartExpression> operands,
      SourceRange selectionRange) {
    return rangeIncludesNonWhitespaceOutsideRange(selectionRange, getRangeOfOperands(operands));
  }

}
