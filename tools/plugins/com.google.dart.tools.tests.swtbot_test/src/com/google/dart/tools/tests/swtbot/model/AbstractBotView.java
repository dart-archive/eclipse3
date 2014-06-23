/*
 * Copyright 2014 Dart project authors.
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
package com.google.dart.tools.tests.swtbot.model;

import com.google.dart.engine.index.Index;
import com.google.dart.engine.internal.index.IndexImpl;
import com.google.dart.engine.internal.index.operation.OperationQueue;
import com.google.dart.tools.core.DartCore;
import com.google.dart.tools.core.internal.builder.AnalysisManager;
import com.google.dart.tools.core.pub.PubBuildParticipant;
import com.google.dart.tools.internal.corext.refactoring.util.ReflectionUtils;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.VoidResult;

import java.util.HashSet;

@SuppressWarnings("restriction")
abstract public class AbstractBotView {

  private static OperationQueue OpQueue;
  private static HashSet<IContainer> PubContainers;

  static {
    Index index = DartCore.getProjectManager().getIndex();
    IndexImpl impl = (IndexImpl) index;
    OpQueue = ReflectionUtils.getFieldObject(impl, "queue");
    PubContainers = ReflectionUtils.getFieldObject(PubBuildParticipant.class, "currentContainers");
  }

  protected final SWTWorkbenchBot bot;

  public AbstractBotView(SWTWorkbenchBot bot) {
    this.bot = bot;
  }

  /**
   * Heuristic to determine when analysis is finished: indexer queue is empty, no background
   * analysis is in progress, pub containers is empty, and eclipse build queue is empty. Even with
   * all that, it sometimes fails.
   * 
   * @see waitForAsyncDrain(), waitForToolsOutput()
   */
  public void waitForAnalysis() {
    AnalysisManager am = AnalysisManager.getInstance();
    loop : while (true) {
      if (OpQueue.size() > 0 || !am.waitForBackgroundAnalysis(10)) {
        waitForEmptyQueue();
        continue loop;
      }
      if (!PubContainers.isEmpty()) {
        waitForEmptyQueue();
        continue loop;
      }
      waitForEmptyQueue();
      break;
    }
  }

  /**
   * Wait until all async events have been processed. This is sometimes necessary to allow widgets
   * to finish updating.
   */
  public void waitForAsyncDrain() {
    final boolean[] done = new boolean[1];
    done[0] = false;
    UIThreadRunnable.asyncExec(new VoidResult() {
      @Override
      public void run() {
        done[0] = true;
      }
    });
    while (true) {
      waitMillis(5);
      if (done[0]) {
        return;
      }
    }
  }

  /**
   * Hackish way to allow Problems to get updated when a new project is loaded.
   */
  public void waitForToolsOutput() {
    if (bot.activeView().getViewReference().getPartName().equals("Files")) {
      waitMillis(500); // allow some time for the console to be activated
    }
    if (bot.activeView().getViewReference().getPartName().equals("Tools Output")) {
      waitMillis(500); // allow some time to append text
    }
  }

  /**
   * Wait for the given number of milliseconds.
   * 
   * @param millis the number of milliseconds to wait
   */
  public void waitMillis(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // ignore it
    }
  }

  abstract protected String viewName();

  private void waitForEmptyQueue() {
    try {
      ResourcesPlugin.getWorkspace().run(new IWorkspaceRunnable() {
        @Override
        public void run(IProgressMonitor monitor) throws CoreException {
          // nothing to do!
        }
      }, new NullProgressMonitor());
    } catch (CoreException e) {
    }
  }
}
