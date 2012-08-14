package com.google.dart.tools.core.analysis;

import com.google.dart.compiler.SystemLibraryManager;
import com.google.dart.tools.core.internal.model.SystemLibraryManagerProvider;

/**
 * {@link AnalysisServer} subclass that intercepts requests to analyze context
 */
class AnalysisServerAdapter extends AnalysisServer {
  private boolean analyzeContext = false;

  public AnalysisServerAdapter() {
    this(SystemLibraryManagerProvider.getAnyLibraryManager());
  }

  public AnalysisServerAdapter(SystemLibraryManager libraryManager) {
    super(libraryManager);
  }

  public void assertAnalyzeContext(boolean expectedState) {
    if (analyzeContext != expectedState) {
      AnalyzeLibraryTaskTest.fail("Expected background analysis " + expectedState + " but found "
          + analyzeContext);
    }
  }

  public void resetAnalyzeContext() {
    analyzeContext = false;
  }

  @Override
  protected void queueAnalyzeContext() {
    analyzeContext = true;
  }
}
