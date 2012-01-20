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
package com.google.dart.tools.debug.ui.internal.browser;

import com.google.dart.tools.debug.core.DartLaunchConfigWrapper;
import com.google.dart.tools.debug.ui.internal.DartDebugUIPlugin;
import com.google.dart.tools.debug.ui.internal.DartUtil;

import org.eclipse.core.resources.IResource;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * Main Dart Launch/Debug configuration tab
 */
public class BrowserMainTab extends AbstractLaunchConfigurationTab {

  private BrowserLaunchConfigurationPanel panel;
  private int initialLaunchType;
  private String initialResPath;
  private boolean initialExternal;

  @Override
  public void createControl(Composite parent) {
    panel = new BrowserLaunchConfigurationPanel(parent, SWT.NONE, this);
    setControl(panel);

    // TODO (danrubel) implement help
    // if (helpContextId != null) {
    // PlatformUI.getWorkbench().getHelpSystem().setHelp(group, helpContextId);
    // }

    // Use asyncExec(...) rather then scheduleUpdateJob()
    // so that this code will execute in Eclipse 3.5
    parent.getDisplay().asyncExec(new Runnable() {
      @Override
      public void run() {
        updateLaunchConfigurationDialog();
      }
    });
  }

  @Override
  public void dispose() {
    Control control = getControl();
    if (control != null) {
      control.dispose();
      setControl(null);
    }
  }

  /**
   * Answer the image to show in the configuration tab or <code>null</code> if none
   */
  @Override
  public Image getImage() {
    return DartDebugUIPlugin.getImage("dart_app.png");
  }

  /**
   * Answer the name to show in the configuration tab
   */
  @Override
  public String getName() {
    return "Main";
  }

  /**
   * Initialize the UI from the specified configuration
   */
  @Override
  public void initializeFrom(ILaunchConfiguration config) {
    DartLaunchConfigWrapper launchWrapper = new DartLaunchConfigWrapper(config);

    initialLaunchType = DartUtil.getLaunchType(config);
    initialResPath = launchWrapper.getApplicationName();
    initialExternal = DartUtil.isExternalBrowser(config);

    panel.setLaunchType(initialLaunchType);
    panel.setResourcePath(initialResPath);
    panel.setExternalBrowser(initialExternal);
  }

  /**
   * Determine if the configuration can be launched
   */
  @Override
  public boolean isValid(ILaunchConfiguration config) {
    DartLaunchConfigWrapper launchWrapper = new DartLaunchConfigWrapper(config);

    IResource resource = launchWrapper.getApplicationResource();

    if (resource == null) {
      return false;
    }
    if (!resource.exists()) {
      return false;
    }

    return DartUtil.isWebPage(resource) || DartUtil.isDartApp(resource);
  }

  /**
   * Store the value specified in the UI into the launch configuration
   */
  @Override
  public void performApply(ILaunchConfigurationWorkingCopy config) {
    initialLaunchType = panel.getLaunchType();
    initialResPath = panel.getResourcePath();
    initialExternal = panel.isExternalBrowser();

    DartLaunchConfigWrapper launchWrapper = new DartLaunchConfigWrapper(config);

    launchWrapper.setApplicationName(initialResPath);

    config.setAttribute(ILaunchConstants.ATTR_LAUNCH_TYPE, initialLaunchType);
    //config.setAttribute(ILaunchConstants.ATTR_RESOURCE_PATH, initialResPath);
    config.setAttribute(ILaunchConstants.ATTR_EXTERNAL_BROWSER, initialExternal);
  }

  @Override
  public void setDefaults(ILaunchConfigurationWorkingCopy config) {
    DartUtil.notYetImplemented(config);
  }

  /**
   * Called by the {@link BrowserLaunchConfigurationPanel} when the user has changed the panel
   * content.
   */
  void panelChanged() {
    setDirty(true);
    updateLaunchConfigurationDialog();
  }
}
