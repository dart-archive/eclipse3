/*
 * Copyright 2012 Dart project authors.
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
package com.google.dart.tools.core.analysis;

class EverythingChangedTask extends Task {

  private final AnalysisServer server;
  private final Context context;

  EverythingChangedTask(AnalysisServer server, Context context) {
    this.server = server;
    this.context = context;
  }

  @Override
  public boolean isBackgroundAnalysis() {
    return false;
  }

  @Override
  public boolean isPriority() {
    return true;
  }

  @Override
  public void perform() {
    context.discardLibraries();
    server.queueAnalyzeContext();
  }
}
