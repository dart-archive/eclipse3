package com.google.dart.tools.wst.ui;

import com.google.dart.engine.context.AnalysisContext;
import com.google.dart.engine.source.Source;
import com.google.dart.tools.core.analysis.model.Project;
import com.google.dart.tools.ui.internal.text.dart.DartPriorityFileEditor;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.core.runtime.IPath;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.wst.sse.ui.StructuredTextEditor;
import org.eclipse.wst.sse.ui.internal.StructuredTextViewer;

/**
 * This {@link IAdapterFactory} adapts {@link StructuredTextEditor} to
 * {@link DartPriorityFileEditor}.
 */
public class StructuredTextEditorToPriorityFileAdapterFactory implements IAdapterFactory {

  @Override
  @SuppressWarnings("rawtypes")
  public Object getAdapter(Object adaptableObject, Class adapterType) {
    if (adaptableObject instanceof StructuredTextEditor
        && DartPriorityFileEditor.class.equals(adapterType)) {
      final StructuredTextEditor textEditor = (StructuredTextEditor) adaptableObject;
      final StructuredTextViewer textViewer = textEditor.getTextViewer();
      final StyledText textWidget = textViewer.getTextWidget();
      final IEditorInput editorInput = textEditor.getEditorInput();
      if (editorInput instanceof IFileEditorInput) {
        final IFileEditorInput fileEditorInput = (IFileEditorInput) editorInput;
        final IPath location = fileEditorInput.getFile().getLocation();
        if (location != null) {
          final String filePath = location.toOSString();
          return new DartPriorityFileEditor() {
            @Override
            public AnalysisContext getInputAnalysisContext() {
              return null;
            }

            @Override
            public String getInputFilePath() {
              return filePath;
            }

            @Override
            public Project getInputProject() {
              return null;
            }

            @Override
            public Source getInputSource() {
              return null;
            }

            @Override
            public boolean isVisible() {
              return textWidget.isVisible();
            }
          };
        }
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Class[] getAdapterList() {
    return new Class[] {DartPriorityFileEditor.class};
  }
}
