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
package com.google.dart.tools.wst.ui.hyperlink;

import com.google.dart.tools.core.DartCore;
import com.google.dart.tools.core.DartCoreDebug;
import com.google.dart.tools.deploy.Activator;
import com.google.dart.tools.ui.internal.text.editor.DartNavigationRegionHyperlink_NEW;

import org.dartlang.analysis.server.protocol.NavigationRegion;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import java.io.File;

public class DartHyperlinkDetector extends AbstractHyperlinkDetector {
  @Override
  public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
      boolean canShowMultipleHyperlinks) {
    if (DartCoreDebug.ENABLE_ANALYSIS_SERVER) {
      ITextFileBufferManager fileManager = FileBuffers.getTextFileBufferManager();
      IDocument document = textViewer.getDocument();
      ITextFileBuffer fileBuffer = fileManager.getTextFileBuffer(document);
      try {
        File file = fileBuffer.getFileStore().toLocalFile(0, null);
        if (file == null) {
          return null;
        }
        String fileName = file.getAbsolutePath();
        int offset = region.getOffset();
        NavigationRegion[] navigationRegions = DartCore.getAnalysisServerData().getNavigation(
            fileName);
        for (NavigationRegion navigationRegion : navigationRegions) {
          if (navigationRegion.containsInclusive(offset)) {
            return new IHyperlink[] {new DartNavigationRegionHyperlink_NEW(navigationRegion)};
          }
        }
      } catch (Throwable e) {
        Activator.logError(e);
        return null;
      }
    }
    return null;
  }
}
