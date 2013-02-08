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
package com.google.dart.engine.internal.resolver;

import com.google.dart.engine.AnalysisEngine;
import com.google.dart.engine.ast.ASTNode;
import com.google.dart.engine.ast.AssignmentExpression;
import com.google.dart.engine.ast.BinaryExpression;
import com.google.dart.engine.ast.BreakStatement;
import com.google.dart.engine.ast.ContinueStatement;
import com.google.dart.engine.ast.ExportDirective;
import com.google.dart.engine.ast.Expression;
import com.google.dart.engine.ast.FunctionExpressionInvocation;
import com.google.dart.engine.ast.Identifier;
import com.google.dart.engine.ast.ImportDirective;
import com.google.dart.engine.ast.IndexExpression;
import com.google.dart.engine.ast.LibraryDirective;
import com.google.dart.engine.ast.LibraryIdentifier;
import com.google.dart.engine.ast.MethodInvocation;
import com.google.dart.engine.ast.PartDirective;
import com.google.dart.engine.ast.PartOfDirective;
import com.google.dart.engine.ast.PostfixExpression;
import com.google.dart.engine.ast.PrefixExpression;
import com.google.dart.engine.ast.PrefixedIdentifier;
import com.google.dart.engine.ast.PropertyAccess;
import com.google.dart.engine.ast.RedirectingConstructorInvocation;
import com.google.dart.engine.ast.SimpleIdentifier;
import com.google.dart.engine.ast.SuperConstructorInvocation;
import com.google.dart.engine.ast.visitor.SimpleASTVisitor;
import com.google.dart.engine.element.ClassElement;
import com.google.dart.engine.element.CompilationUnitElement;
import com.google.dart.engine.element.ConstructorElement;
import com.google.dart.engine.element.Element;
import com.google.dart.engine.element.ExecutableElement;
import com.google.dart.engine.element.FieldElement;
import com.google.dart.engine.element.FunctionElement;
import com.google.dart.engine.element.ImportElement;
import com.google.dart.engine.element.LabelElement;
import com.google.dart.engine.element.LibraryElement;
import com.google.dart.engine.element.MethodElement;
import com.google.dart.engine.element.ParameterElement;
import com.google.dart.engine.element.PrefixElement;
import com.google.dart.engine.element.PropertyAccessorElement;
import com.google.dart.engine.element.TypeVariableElement;
import com.google.dart.engine.element.VariableElement;
import com.google.dart.engine.internal.element.LabelElementImpl;
import com.google.dart.engine.internal.scope.LabelScope;
import com.google.dart.engine.resolver.ResolverErrorCode;
import com.google.dart.engine.scanner.Token;
import com.google.dart.engine.scanner.TokenType;
import com.google.dart.engine.type.InterfaceType;
import com.google.dart.engine.type.Type;

import java.util.HashSet;

/**
 * Instances of the class {@code ElementResolver} are used by instances of {@link ResolverVisitor}
 * to resolve references within the AST structure to the elements being referenced. The requirements
 * for the element resolver are:
 * <ol>
 * <li>Every {@link SimpleIdentifier} should be resolved to the element to which it refers.
 * Specifically:
 * <ul>
 * <li>An identifier within the declaration of that name should resolve to the element being
 * declared.</li>
 * <li>An identifier denoting a prefix should resolve to the element representing the import that
 * defines the prefix (an {@link ImportElement}).</li>
 * <li>An identifier denoting a variable should resolve to the element representing the variable (a
 * {@link VariableElement}).</li>
 * <li>An identifier denoting a parameter should resolve to the element representing the parameter
 * (a {@link ParameterElement}).</li>
 * <li>An identifier denoting a field should resolve to the element representing the getter or
 * setter being invoked (a {@link PropertyAccessorElement}).</li>
 * <li>An identifier denoting the name of a method or function being invoked should resolve to the
 * element representing the method or function (a {@link ExecutableElement}).</li>
 * <li>An identifier denoting a label should resolve to the element representing the label (a
 * {@link LabelElement}).</li>
 * </ul>
 * The identifiers within directives are exceptions to this rule and are covered below.</li>
 * <li>Every node containing a token representing an operator that can be overridden (
 * {@link BinaryExpression}, {@link PrefixExpression}, {@link PostfixExpression}) should resolve to
 * the element representing the method invoked by that operator (a {@link MethodElement}).</li>
 * <li>Every {@link FunctionExpressionInvocation} should resolve to the element representing the
 * function being invoked (a {@link FunctionElement}). This will be the same element as that to
 * which the name is resolved if the function has a name, but is provided for those cases where an
 * unnamed function is being invoked.</li>
 * <li>Every {@link LibraryDirective} and {@link PartOfDirective} should resolve to the element
 * representing the library being specified by the directive (a {@link LibraryElement}) unless, in
 * the case of a part-of directive, the specified library does not exist.</li>
 * <li>Every {@link ImportDirective} and {@link ExportDirective} should resolve to the element
 * representing the library being specified by the directive unless the specified library does not
 * exist (a {@link LibraryElement}).</li>
 * <li>The identifier representing the prefix in an {@link ImportDirective} should resolve to the
 * element representing the prefix (a {@link PrefixElement}).</li>
 * <li>The identifiers in the hide and show combinators in {@link ImportDirective}s and
 * {@link ExportDirective}s should resolve to the elements that are being hidden or shown,
 * respectively, unless those names are not defined in the specified library (or the specified
 * library does not exist).</li>
 * <li>Every {@link PartDirective} should resolve to the element representing the compilation unit
 * being specified by the string unless the specified compilation unit does not exist (a
 * {@link CompilationUnitElement}).</li>
 * </ol>
 * Note that AST nodes that would represent elements that are not defined are not resolved to
 * anything. This includes such things as references to undeclared variables (which is an error) and
 * names in hide and show combinators that are not defined in the imported library (which is not an
 * error).
 */
public class ElementResolver extends SimpleASTVisitor<Void> {
  /**
   * The resolver driving this participant.
   */
  private ResolverVisitor resolver;

  /**
   * Initialize a newly created visitor to resolve the nodes in a compilation unit.
   * 
   * @param resolver the resolver driving this participant
   */
  public ElementResolver(ResolverVisitor resolver) {
    this.resolver = resolver;
  }

  @Override
  public Void visitAssignmentExpression(AssignmentExpression node) {
    TokenType operator = node.getOperator().getType();
    if (operator != TokenType.EQ) {
      operator = operatorFromCompoundAssignment(operator);
      Expression leftNode = node.getLeftHandSide();
      if (leftNode != null) {
        Type leftType = leftNode.getStaticType();
        if (leftType != null) {
          Element leftElement = leftType.getElement();
          if (leftElement != null) {
            MethodElement method = lookUpMethod(leftElement, operator.getLexeme());
            if (method != null) {
              node.setElement(method);
            } else {
              // TODO(brianwilkerson) Report this error.
            }
          }
        }
      }
    }
    return null;
  }

  @Override
  public Void visitBinaryExpression(BinaryExpression node) {
    Token operator = node.getOperator();
    if (operator.isUserDefinableOperator()) {
      Type leftType = getType(node.getLeftOperand());
      if (leftType == null) {
        return null;
      }
      Element leftTypeElement = leftType.getElement();
      String methodName = operator.getLexeme();
      MethodElement member = lookUpMethod(leftTypeElement, methodName);
      if (member == null) {
        resolver.reportError(ResolverErrorCode.CANNOT_BE_RESOLVED, operator, methodName);
      } else {
        node.setElement(member);
      }
    }
    return null;
  }

  @Override
  public Void visitBreakStatement(BreakStatement node) {
    SimpleIdentifier labelNode = node.getLabel();
    LabelElementImpl labelElement = lookupLabel(node, labelNode);
    if (labelElement != null && labelElement.isOnSwitchMember()) {
      resolver.reportError(ResolverErrorCode.BREAK_LABEL_ON_SWITCH_MEMBER, labelNode);
    }
    return null;
  }

  @Override
  public Void visitContinueStatement(ContinueStatement node) {
    SimpleIdentifier labelNode = node.getLabel();
    LabelElementImpl labelElement = lookupLabel(node, labelNode);
    if (labelElement != null && labelElement.isOnSwitchStatement()) {
      resolver.reportError(ResolverErrorCode.CONTINUE_LABEL_ON_SWITCH, labelNode);
    }
    return null;
  }

  @Override
  public Void visitFunctionExpressionInvocation(FunctionExpressionInvocation node) {
    // TODO(brianwilkerson) Resolve the function being invoked?
    return null;
  }

  @Override
  public Void visitImportDirective(ImportDirective node) {
    // TODO(brianwilkerson) Determine whether this still needs to be done.
    // TODO(brianwilkerson) Resolve the names in combinators
    SimpleIdentifier prefixNode = node.getPrefix();
    if (prefixNode != null) {
      String prefixName = prefixNode.getName();
      for (PrefixElement prefixElement : resolver.getDefiningLibrary().getPrefixes()) {
        if (prefixElement.getName().equals(prefixName)) {
          recordResolution(prefixNode, prefixElement);
        }
        return null;
      }
    }
    return null;
  }

  @Override
  public Void visitIndexExpression(IndexExpression node) {
    Type arrayType = getType(node.getArray());
    if (arrayType == null) {
      return null;
    }
    Element arrayTypeElement = arrayType.getElement();
    String operator;
    if (node.inSetterContext()) {
      operator = TokenType.INDEX_EQ.getLexeme();
    } else {
      operator = TokenType.INDEX.getLexeme();
    }
    MethodElement member = lookUpMethod(arrayTypeElement, operator);
    if (member == null) {
      resolver.reportError(ResolverErrorCode.CANNOT_BE_RESOLVED, node, operator);
    } else {
      node.setElement(member);
    }
    return null;
  }

  @Override
  public Void visitLibraryIdentifier(LibraryIdentifier node) {
    // We don't resolve the individual components of the library identifier because they have no
    // semantic meaning.
    return null;
  }

  @Override
  public Void visitMethodInvocation(MethodInvocation node) {
    SimpleIdentifier methodName = node.getMethodName();
    Expression target = node.getTarget();
    Element element;
    if (target == null) {
      element = resolver.getNameScope().lookup(methodName, resolver.getDefiningLibrary());
    } else {
      Type targetType = getType(target);
      if (targetType instanceof InterfaceType) {
        element = lookUpMethod(targetType.getElement(), methodName.getName());
      } else {
        //TODO(brianwilkerson) Report this error.
        return null;
      }
    }
    ExecutableElement invokedMethod = null;
    if (element instanceof ExecutableElement) {
      invokedMethod = (ExecutableElement) element;
    } else if (element instanceof FieldElement) {
      // TODO(brianwilkerson) Decide whether to resolve to the getter or the setter (or what to do
      // when both are appropriate).
    } else {
      //TODO(brianwilkerson) Report this error (for example, found an "invocation" of a class).
      return null;
    }
    if (invokedMethod == null) {
      // TODO(brianwilkerson) Report this error.
      return null;
    }
    recordResolution(methodName, invokedMethod);
    //TODO(brianwilkerson) Validate the method invocation (number of arguments, etc.).
    return null;
  }

  @Override
  public Void visitPostfixExpression(PostfixExpression node) {
    Token operator = node.getOperator();
    if (operator.isUserDefinableOperator()) {
      Type operandType = getType(node.getOperand());
      if (operandType == null) {
        return null;
      }
      Element operandTypeElement = operandType.getElement();
      String methodName;
      if (operator.getType() == TokenType.PLUS_PLUS) {
        methodName = TokenType.PLUS.getLexeme();
      } else {
        methodName = TokenType.MINUS.getLexeme();
      }
      MethodElement member = lookUpMethod(operandTypeElement, methodName);
      if (member == null) {
        resolver.reportError(ResolverErrorCode.CANNOT_BE_RESOLVED, operator, methodName);
      } else {
        node.setElement(member);
      }
    }
    return null;
  }

  @Override
  public Void visitPrefixedIdentifier(PrefixedIdentifier node) {
    SimpleIdentifier prefix = node.getPrefix();
    SimpleIdentifier identifier = node.getIdentifier();

    Element prefixElement = resolver.getNameScope().lookup(prefix, resolver.getDefiningLibrary());
    recordResolution(prefix, prefixElement);
    // TODO(brianwilkerson) This needs to be an ImportElement
    if (prefixElement instanceof PrefixElement) {
      Element element = resolver.getNameScope().lookup(node, resolver.getDefiningLibrary());
      recordResolution(node, element);
    } else if (prefixElement instanceof ClassElement) {
      // TODO(brianwilkerson) Should we replace this node with a PropertyAccess node?
      Element memberElement;
      if (node.getIdentifier().inSetterContext()) {
        memberElement = lookUpSetterInType((ClassElement) prefixElement, identifier.getName());
      } else {
        memberElement = lookUpGetterInType((ClassElement) prefixElement, identifier.getName());
      }
      if (memberElement == null) {
        resolver.reportError(ResolverErrorCode.CANNOT_BE_RESOLVED, identifier, identifier.getName());
      } else {
//      if (!element.isStatic()) {
//        reportError(ResolverErrorCode.STATIC_ACCESS_TO_INSTANCE_MEMBER, identifier, identifier.getName());
//      }
        recordResolution(identifier, memberElement);
      }
    } else if (prefixElement instanceof VariableElement) {
      // TODO(brianwilkerson) Should we replace this node with a PropertyAccess node?
      Element variableType = ((VariableElement) prefixElement).getType().getElement();
      PropertyAccessorElement memberElement;
      if (node.getIdentifier().inGetterContext()) {
        memberElement = lookUpGetter(variableType, identifier.getName());
      } else {
        memberElement = lookUpSetter(variableType, identifier.getName());
      }
      if (memberElement == null) {
        resolver.reportError(ResolverErrorCode.CANNOT_BE_RESOLVED, identifier, identifier.getName());
      } else {
//      if (!element.isStatic()) {
//        reportError(ResolverErrorCode.STATIC_ACCESS_TO_INSTANCE_MEMBER, identifier, identifier.getName());
//      }
        recordResolution(identifier, memberElement);
      }
    } else {
      // reportError(ResolverErrorCode.UNDEFINED_PREFIX);
    }
    return null;
  }

  @Override
  public Void visitPrefixExpression(PrefixExpression node) {
    Token operator = node.getOperator();
    if (operator.isUserDefinableOperator()) {
      Type operandType = getType(node.getOperand());
      if (operandType == null) {
        return null;
      }
      Element operandTypeElement = operandType.getElement();
      String methodName;
      if (operator.getType() == TokenType.PLUS_PLUS) {
        methodName = TokenType.PLUS.getLexeme();
      } else if (operator.getType() == TokenType.MINUS_MINUS) {
        methodName = TokenType.MINUS.getLexeme();
      } else if (operator.getType() == TokenType.MINUS) {
        methodName = "unary-";
      } else {
        methodName = operator.getLexeme();
      }
      MethodElement member = lookUpMethod(operandTypeElement, methodName);
      if (member == null) {
        resolver.reportError(ResolverErrorCode.CANNOT_BE_RESOLVED, operator, methodName);
      } else {
        node.setElement(member);
      }
    }
    return null;
  }

  @Override
  public Void visitPropertyAccess(PropertyAccess node) {
    Type targetType = getType(node.getRealTarget());
    if (!(targetType instanceof InterfaceType)) {
      // TODO(brianwilkerson) Report this error
      return null;
    }
    ClassElement targetElement = ((InterfaceType) targetType).getElement();
    SimpleIdentifier identifier = node.getPropertyName();
    PropertyAccessorElement memberElement;
    if (identifier.inGetterContext()) {
      memberElement = lookUpGetter(targetElement, identifier.getName());
    } else {
      memberElement = lookUpSetter(targetElement, identifier.getName());
    }
    if (memberElement == null) {
      resolver.reportError(ResolverErrorCode.CANNOT_BE_RESOLVED, identifier, identifier.getName());
//    } else if (!element.isStatic()) {
//      reportError(ResolverErrorCode.STATIC_ACCESS_TO_INSTANCE_MEMBER, identifier, identifier.getName());
    } else {
      recordResolution(identifier, memberElement);
    }
    return null;
  }

  @Override
  public Void visitRedirectingConstructorInvocation(RedirectingConstructorInvocation node) {
    ClassElement enclosingClass = resolver.getEnclosingClass();
    if (enclosingClass == null) {
      // TODO(brianwilkerson) Report this error.
      return null;
    }
    SimpleIdentifier name = node.getConstructorName();
    ConstructorElement element;
    if (name == null) {
      element = enclosingClass.getUnnamedConstructor();
    } else {
      element = enclosingClass.getNamedConstructor(name.getName());
    }
    if (element == null) {
      // TODO(brianwilkerson) Report this error and decide what element to associate with the node.
    }
    recordResolution(name, element);
    return null;
  }

  @Override
  public Void visitSimpleIdentifier(SimpleIdentifier node) {
    //
    // There are four cases in which we defer the resolution of a simple identifier to the method
    // in which we are resolving it's parent. We do this to prevent creating false positives.
    //
    ASTNode parent = node.getParent();
    if (parent instanceof PrefixedIdentifier
        && ((PrefixedIdentifier) parent).getIdentifier() == node) {
      return null;
    } else if (parent instanceof PropertyAccess
        && ((PropertyAccess) parent).getPropertyName() == node) {
      return null;
    } else if (parent instanceof RedirectingConstructorInvocation
        || parent instanceof SuperConstructorInvocation) {
      return null;
    }
    //
    // We also ignore identifiers that have already been resolved.
    //
    if (node.getElement() != null) {
      return null;
    }
    //
    // If it's not one of those special cases, then the node should be resolved.
    //
    Element element = resolver.getNameScope().lookup(node, resolver.getDefiningLibrary());
    if (element == null) {
      if (node.inGetterContext()) {
        element = lookUpGetter(resolver.getEnclosingClass(), node.getName());
      } else {
        element = lookUpSetter(resolver.getEnclosingClass(), node.getName());
      }
      if (element == null) {
        element = lookUpMethod(resolver.getEnclosingClass(), node.getName());
      }
    }
    if (element == null) {
      // TODO(brianwilkerson) Report and recover from this error.
    }
    recordResolution(node, element);
    return null;
  }

  @Override
  public Void visitSuperConstructorInvocation(SuperConstructorInvocation node) {
    ClassElement enclosingClass = resolver.getEnclosingClass();
    if (enclosingClass == null) {
      // TODO(brianwilkerson) Report this error.
      return null;
    }
    ClassElement superclass = getSuperclass(enclosingClass);
    if (superclass == null) {
      // TODO(brianwilkerson) Report this error.
      return null;
    }
    SimpleIdentifier name = node.getConstructorName();
    ConstructorElement element;
    if (name == null) {
      element = superclass.getUnnamedConstructor();
    } else {
      element = superclass.getNamedConstructor(name.getName());
    }
    if (element == null) {
      // TODO(brianwilkerson) Report this error and decide what element to associate with the node.
    }
    recordResolution(name, element);
    return null;
  }

  /**
   * Return the element representing the superclass of the given class.
   * 
   * @param targetClass the class whose superclass is to be returned
   * @return the element representing the superclass of the given class
   */
  private ClassElement getSuperclass(ClassElement targetClass) {
    InterfaceType superType = targetClass.getSupertype();
    if (superType == null) {
      return null;
    }
    return superType.getElement();
  }

  /**
   * Return the type of the given expression that is to be used for type analysis.
   * 
   * @param expression the expression whose type is to be returned
   * @return the type of the given expression
   */
  private Type getType(Expression expression) {
    return expression.getStaticType();
  }

  /**
   * Look up the getter with the given name in the given type. Return the element representing the
   * getter that was found, or {@code null} if there is no getter with the given name.
   * 
   * @param element the element representing the type in which the getter is defined
   * @param getterName the name of the getter being looked up
   * @return the element representing the getter that was found
   */
  private PropertyAccessorElement lookUpGetter(Element element, String getterName) {
    // TODO(brianwilkerson) Decide how to represent members defined in 'dynamic'.
//    if (element == DynamicTypeImpl.getInstance()) {
//      return ?;
//    } else
    if (element instanceof ClassElement) {
      ClassElement classElement = (ClassElement) element;
      PropertyAccessorElement member = classElement.lookUpGetter(
          getterName,
          resolver.getDefiningLibrary());
      if (member != null) {
        return member;
      }
      // return classElement.getType().lookUpGetter(methodName, resolver.getDefiningLibrary());
      return lookUpGetterInInterfaces(
          (ClassElement) element,
          getterName,
          new HashSet<ClassElement>());
    }
    return null;
  }

  /**
   * Look up the name of a getter in the interfaces implemented by the given type, either directly
   * or indirectly. Return the element representing the getter that was found, or {@code null} if
   * there is no getter with the given name.
   * 
   * @param element the element representing the type in which the getter is defined
   * @param memberName the name of the getter being looked up
   * @param visitedInterfaces a set containing all of the interfaces that have been examined, used
   *          to prevent infinite recursion and to optimize the search
   * @return the element representing the getter that was found
   */
  private PropertyAccessorElement lookUpGetterInInterfaces(ClassElement targetClass,
      String memberName, HashSet<ClassElement> visitedInterfaces) {
    // TODO(brianwilkerson) This isn't correct. Section 8.1.1 of the specification (titled
    // "Inheritance and Overriding" under "Interfaces") describes a much more complex scheme for
    // finding the inherited member. We need to follow that scheme. The code below should cover the
    // 80% case.
    if (visitedInterfaces.contains(targetClass)) {
      return null;
    }
    visitedInterfaces.add(targetClass);
    PropertyAccessorElement member = lookUpGetterInType(targetClass, memberName);
    if (member != null) {
      return member;
    }
    for (InterfaceType interfaceType : targetClass.getInterfaces()) {
      member = lookUpGetterInInterfaces(interfaceType.getElement(), memberName, visitedInterfaces);
      if (member != null) {
        return member;
      }
    }
    ClassElement superclass = getSuperclass(targetClass);
    if (superclass == null) {
      return null;
    }
    return lookUpGetterInInterfaces(superclass, memberName, visitedInterfaces);
  }

  /**
   * Look up the name of a getter in the given type. Return the element representing the getter that
   * was found, or {@code null} if there is no getter with the given name.
   * 
   * @param element the element representing the type in which the getter is defined
   * @param memberName the name of the getter being looked up
   * @return the element representing the getter that was found
   */
  private PropertyAccessorElement lookUpGetterInType(ClassElement element, String memberName) {
    for (PropertyAccessorElement accessor : element.getAccessors()) {
      if (accessor.isGetter() && accessor.getName().equals(memberName)) {
        return accessor;
      }
    }
    return null;
  }

  /**
   * Find the element corresponding to the given label node in the current label scope.
   * 
   * @param parentNode the node containing the given label
   * @param labelNode the node representing the label being looked up
   * @return the element corresponding to the given label node in the current scope
   */
  private LabelElementImpl lookupLabel(ASTNode parentNode, SimpleIdentifier labelNode) {
    LabelScope labelScope = resolver.getLabelScope();
    LabelElementImpl labelElement = null;
    if (labelNode == null) {
      if (labelScope == null) {
        // TODO(brianwilkerson) Do we need to report this error, or is this condition always caught in the parser?
        // reportError(ResolverErrorCode.BREAK_OUTSIDE_LOOP);
      } else {
        labelElement = (LabelElementImpl) labelScope.lookup(LabelScope.EMPTY_LABEL);
        if (labelElement == null) {
          // TODO(brianwilkerson) Do we need to report this error, or is this condition always caught in the parser?
          // reportError(ResolverErrorCode.BREAK_OUTSIDE_LOOP);
        }
      }
    } else {
      if (labelScope == null) {
        resolver.reportError(ResolverErrorCode.UNDEFINED_LABEL, labelNode, labelNode.getName());
      } else {
        labelElement = (LabelElementImpl) labelScope.lookup(labelNode);
        if (labelElement == null) {
          resolver.reportError(ResolverErrorCode.UNDEFINED_LABEL, labelNode, labelNode.getName());
        } else {
          recordResolution(labelNode, labelElement);
        }
      }
    }
    if (labelElement != null) {
      ExecutableElement labelContainer = labelElement.getAncestor(ExecutableElement.class);
      if (labelContainer != resolver.getEnclosingFunction()) {
        if (labelNode == null) {
          // TODO(brianwilkerson) Create a new error for cases where there is no label.
          resolver.reportError(ResolverErrorCode.LABEL_IN_OUTER_SCOPE, parentNode, "");
        } else {
          resolver.reportError(
              ResolverErrorCode.LABEL_IN_OUTER_SCOPE,
              labelNode,
              labelNode.getName());
        }
        labelElement = null;
      }
    }
    return labelElement;
  }

  /**
   * Look up the method with the given name in the given type. Return the element representing the
   * method that was found, or {@code null} if there is no method with the given name.
   * 
   * @param element the element representing the type in which the method is defined
   * @param methodName the name of the method being looked up
   * @return the element representing the method that was found
   */
  private MethodElement lookUpMethod(Element element, String methodName) {
    // TODO(brianwilkerson) Decide how to represent members defined in 'dynamic'.
//    if (element == DynamicTypeImpl.getInstance()) {
//      return ?;
//    }
    if (element instanceof TypeVariableElement) {
      Type bound = ((TypeVariableElement) element).getBound();
      if (bound == null) {
        element = resolver.getTypeProvider().getObjectType().getElement();
      } else {
        element = bound.getElement();
      }
    }
    if (element instanceof ClassElement) {
      ClassElement classElement = (ClassElement) element;
      MethodElement member = classElement.lookUpMethod(methodName, resolver.getDefiningLibrary());
      if (member != null) {
        return member;
      }
      // return classElement.getType().lookUpMethod(methodName, resolver.getDefiningLibrary());
      return lookUpMethodInInterfaces(
          (ClassElement) element,
          methodName,
          new HashSet<ClassElement>());
    }
    return null;
  }

  /**
   * Look up the name of a member in the interfaces implemented by the given type, either directly
   * or indirectly. Return the element representing the member that was found, or {@code null} if
   * there is no member with the given name.
   * 
   * @param element the element representing the type in which the member is defined
   * @param memberName the name of the member being looked up
   * @param visitedInterfaces a set containing all of the interfaces that have been examined, used
   *          to prevent infinite recursion and to optimize the search
   * @return the element representing the member that was found
   */
  private MethodElement lookUpMethodInInterfaces(ClassElement targetClass, String memberName,
      HashSet<ClassElement> visitedInterfaces) {
    // TODO(brianwilkerson) This isn't correct. Section 8.1.1 of the specification (titled
    // "Inheritance and Overriding" under "Interfaces") describes a much more complex scheme for
    // finding the inherited member. We need to follow that scheme. The code below should cover the
    // 80% case.
    if (visitedInterfaces.contains(targetClass)) {
      return null;
    }
    visitedInterfaces.add(targetClass);
    MethodElement member = lookUpMethodInType(targetClass, memberName);
    if (member != null) {
      return member;
    }
    for (InterfaceType interfaceType : targetClass.getInterfaces()) {
      member = lookUpMethodInInterfaces(interfaceType.getElement(), memberName, visitedInterfaces);
      if (member != null) {
        return member;
      }
    }
    ClassElement superclass = getSuperclass(targetClass);
    if (superclass == null) {
      return null;
    }
    return lookUpMethodInInterfaces(superclass, memberName, visitedInterfaces);
  }

  /**
   * Look up the name of a method in the given type. Return the element representing the method that
   * was found, or {@code null} if there is no method with the given name.
   * 
   * @param element the element representing the type in which the method is defined
   * @param memberName the name of the method being looked up
   * @return the element representing the method that was found
   */
  private MethodElement lookUpMethodInType(ClassElement element, String memberName) {
    for (MethodElement method : element.getMethods()) {
      if (method.getName().equals(memberName)) {
        return method;
      }
    }
    return null;
  }

  /**
   * Look up the setter with the given name in the given type. Return the element representing the
   * setter that was found, or {@code null} if there is no setter with the given name.
   * 
   * @param element the element representing the type in which the setter is defined
   * @param setterName the name of the setter being looked up
   * @return the element representing the setter that was found
   */
  private PropertyAccessorElement lookUpSetter(Element element, String setterName) {
    // TODO(brianwilkerson) Decide how to represent members defined in 'dynamic'.
//    if (element == DynamicTypeImpl.getInstance()) {
//      return ?;
//    } else
    if (element instanceof ClassElement) {
      ClassElement classElement = (ClassElement) element;
      PropertyAccessorElement member = classElement.lookUpSetter(
          setterName,
          resolver.getDefiningLibrary());
      if (member != null) {
        return member;
      }
      // return classElement.getType().lookUpSetter(methodName, resolver.getDefiningLibrary());
      return lookUpSetterInInterfaces(
          (ClassElement) element,
          setterName,
          new HashSet<ClassElement>());
    }
    return null;
  }

  /**
   * Look up the name of a setter in the interfaces implemented by the given type, either directly
   * or indirectly. Return the element representing the setter that was found, or {@code null} if
   * there is no setter with the given name.
   * 
   * @param element the element representing the type in which the setter is defined
   * @param memberName the name of the setter being looked up
   * @param visitedInterfaces a set containing all of the interfaces that have been examined, used
   *          to prevent infinite recursion and to optimize the search
   * @return the element representing the setter that was found
   */
  private PropertyAccessorElement lookUpSetterInInterfaces(ClassElement targetClass,
      String memberName, HashSet<ClassElement> visitedInterfaces) {
    // TODO(brianwilkerson) This isn't correct. Section 8.1.1 of the specification (titled
    // "Inheritance and Overriding" under "Interfaces") describes a much more complex scheme for
    // finding the inherited member. We need to follow that scheme. The code below should cover the
    // 80% case.
    if (visitedInterfaces.contains(targetClass)) {
      return null;
    }
    visitedInterfaces.add(targetClass);
    PropertyAccessorElement member = lookUpSetterInType(targetClass, memberName);
    if (member != null) {
      return member;
    }
    for (InterfaceType interfaceType : targetClass.getInterfaces()) {
      member = lookUpSetterInInterfaces(interfaceType.getElement(), memberName, visitedInterfaces);
      if (member != null) {
        return member;
      }
    }
    ClassElement superclass = getSuperclass(targetClass);
    if (superclass == null) {
      return null;
    }
    return lookUpSetterInInterfaces(superclass, memberName, visitedInterfaces);
  }

  /**
   * Look up the name of a setter in the given type. Return the element representing the setter that
   * was found, or {@code null} if there is no setter with the given name.
   * 
   * @param element the element representing the type in which the setter is defined
   * @param memberName the name of the setter being looked up
   * @return the element representing the setter that was found
   */
  private PropertyAccessorElement lookUpSetterInType(ClassElement element, String memberName) {
    for (PropertyAccessorElement accessor : element.getAccessors()) {
      if (accessor.isSetter() && accessor.getName().equals(memberName)) {
        return accessor;
      }
    }
    return null;
  }

  /**
   * Return the binary operator that is invoked by the given compound assignment operator.
   * 
   * @param operator the assignment operator being mapped
   * @return the binary operator that invoked by the given assignment operator
   */
  private TokenType operatorFromCompoundAssignment(TokenType operator) {
    switch (operator) {
      case AMPERSAND_EQ:
        return TokenType.AMPERSAND;
      case BAR_EQ:
        return TokenType.BAR;
      case CARET_EQ:
        return TokenType.CARET;
      case GT_GT_EQ:
        return TokenType.GT_GT;
      case LT_LT_EQ:
        return TokenType.LT_LT;
      case MINUS_EQ:
        return TokenType.MINUS;
      case PERCENT_EQ:
        return TokenType.PERCENT;
      case PLUS_EQ:
        return TokenType.PLUS;
      case SLASH_EQ:
        return TokenType.SLASH;
      case STAR_EQ:
        return TokenType.STAR;
      case TILDE_SLASH_EQ:
        return TokenType.TILDE_SLASH;
    }
    // Internal error: Unmapped assignment operator.
    AnalysisEngine.getInstance().getLogger().logError(
        "Failed to map " + operator.getLexeme() + " to it's corresponding operator");
    return operator;
  }

  /**
   * Record the fact that the given AST node was resolved to the given element.
   * 
   * @param node the AST node that was resolved
   * @param element the element to which the AST node was resolved
   */
  private void recordResolution(Identifier node, Element element) {
    if (element != null) {
      node.setElement(element);
    }
  }
}
