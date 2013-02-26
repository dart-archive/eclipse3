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

package com.google.dart.engine.services.internal.refactoring;

import com.google.dart.engine.element.ClassElement;
import com.google.dart.engine.element.ConstructorElement;
import com.google.dart.engine.element.Element;
import com.google.dart.engine.element.FieldElement;
import com.google.dart.engine.element.MethodElement;
import com.google.dart.engine.formatter.edit.Edit;
import com.google.dart.engine.search.MatchKind;
import com.google.dart.engine.search.SearchEngine;
import com.google.dart.engine.search.SearchMatch;
import com.google.dart.engine.services.change.Change;
import com.google.dart.engine.services.change.SourceChange;
import com.google.dart.engine.services.refactoring.NamingConventions;
import com.google.dart.engine.services.refactoring.ProgressMonitor;
import com.google.dart.engine.services.refactoring.Refactoring;
import com.google.dart.engine.services.refactoring.SubProgressMonitor;
import com.google.dart.engine.services.status.RefactoringStatus;
import com.google.dart.engine.services.status.RefactoringStatusContext;

import static com.google.dart.engine.services.internal.correction.CorrectionUtils.getChildren;
import static com.google.dart.engine.services.internal.correction.CorrectionUtils.getElementKindName;

import java.text.MessageFormat;
import java.util.List;

/**
 * {@link Refactoring} for renaming {@link FieldElement} and {@link MethodElement}.
 */
public class RenameConstructorRefactoringImpl extends RenameRefactoringImpl {
  private final ConstructorElement element;

  public RenameConstructorRefactoringImpl(SearchEngine searchEngine, ConstructorElement element) {
    super(searchEngine, element);
    this.element = element;
  }

  @Override
  public RefactoringStatus checkFinalConditions(ProgressMonitor pm) throws Exception {
    pm.beginTask("Checking final conditions", 1);
    try {
      RefactoringStatus result = new RefactoringStatus();
      result.merge(analyzePossibleConflicts(new SubProgressMonitor(pm, 1)));
      return result;
    } finally {
      pm.done();
    }
  }

  @Override
  public RefactoringStatus checkNewName(String newName) {
    RefactoringStatus result = new RefactoringStatus();
    result.merge(super.checkNewName(newName));
    result.merge(NamingConventions.validateConstructorName(newName));
    return result;
  }

  @Override
  public Change createChange(ProgressMonitor pm) throws Exception {
    SourceChange change = new SourceChange(getRefactoringName(), elementSource);
    String replacement = newName.isEmpty() ? "" : "." + newName;
    List<SearchMatch> references = searchEngine.searchReferences(element, null, null);
    // update declaration
    if (!element.isSynthetic()) {
      for (SearchMatch reference : references) {
        if (reference.getKind() == MatchKind.CONSTRUCTOR_DECLARATION) {
          Edit edit = new Edit(reference.getSourceRange(), replacement);
          change.addEdit("Update declaration", edit);
        }
      }
    }
    // update references
    for (SearchMatch reference : references) {
      if (reference.getKind() == MatchKind.CONSTRUCTOR_REFERENCE) {
        Edit edit = new Edit(reference.getSourceRange(), replacement);
        change.addEdit("Update reference", edit);
      }
    }
    return change;
  }

  @Override
  public String getRefactoringName() {
    return "Rename Constructor";
  }

  private RefactoringStatus analyzePossibleConflicts(ProgressMonitor pm) {
    pm.beginTask("Analyze possible conflicts", 1);
    try {
      final RefactoringStatus result = new RefactoringStatus();
      // check if there are members with "newName" in the same ClassElement
      {
        ClassElement parentClass = element.getEnclosingElement();
        for (Element newNameMember : getChildren(parentClass, newName)) {
          String message = MessageFormat.format(
              "Class ''{0}'' already declares {1} with name ''{2}''.",
              parentClass.getName(),
              getElementKindName(newNameMember),
              newName);
          result.addError(message, RefactoringStatusContext.create(newNameMember));
        }
      }
      pm.worked(1);
      // done
      return result;
    } finally {
      pm.done();
    }
  }
}
