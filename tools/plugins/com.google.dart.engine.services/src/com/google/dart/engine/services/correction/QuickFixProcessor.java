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

package com.google.dart.engine.services.correction;

import com.google.dart.engine.error.AnalysisError;
import com.google.dart.engine.error.ErrorCode;
import com.google.dart.engine.services.assist.AssistContext;

/**
 * Provides {@link CorrectionProposal}s to fix given {@link AnalysisError}s.
 */
public interface QuickFixProcessor {
  /**
   * @return the {@link CorrectionProposal}s which may fix given {@link AnalysisError}.
   */
  CorrectionProposal[] computeProposals(AssistContext context, AnalysisError problem)
      throws Exception;

  /**
   * Returns {@link ErrorCode}s for which this processor may compute fixes.
   */
  ErrorCode[] getFixableErrorCodes();

  /**
   * @return the {@code true} if {@link QuickFixProcessor} can produce {@link CorrectionProposal}(s)
   *         which may fix given {@link AnalysisError}.
   */
  boolean hasFix(AnalysisError problem);
}
