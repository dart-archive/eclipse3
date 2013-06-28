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
package com.google.dart.engine.error;

/**
 * The enumeration {@code AuditCode} defines the audits and coding recommendations for best
 * practices which are not mentioned in the Dart Language Specification.
 */
public enum AuditCode implements ErrorCode {
  /**
   * Dead code is code that is never reached, this can happen for instance if a statement follows a
   * return statement.
   */
  DEAD_CODE("Dead code");

  /**
   * The message template used to create the message to be displayed for this error.
   */
  private final String message;

  /**
   * Initialize a newly created error code to have the given message.
   * 
   * @param message the message template used to create the message to be displayed for the error
   */
  private AuditCode(String message) {
    this.message = message;
  }

  @Override
  public ErrorSeverity getErrorSeverity() {
    return ErrorType.AUDIT.getSeverity();
  }

  @Override
  public String getMessage() {
    return message;
  }

  @Override
  public ErrorType getType() {
    return ErrorType.AUDIT;
  }
}