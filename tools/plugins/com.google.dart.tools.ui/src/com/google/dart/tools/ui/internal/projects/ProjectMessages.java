/*
 * Copyright 2012 Google Inc.
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

package com.google.dart.tools.ui.internal.projects;

import org.eclipse.osgi.util.NLS;

/**
 * 
 */
public class ProjectMessages extends NLS {
  private static final String BUNDLE_NAME = "com.google.dart.tools.ui.internal.projects.ProjectMessages"; //$NON-NLS-1$
  public static String HideProjectAction_operation_msg;
  public static String HideProjectAction_problems_msg;
  public static String HideProjectAction_problems_title;
  public static String HideProjectAction_text;
  public static String HideProjectAction_tooltip;
  public static String NewApplicationWizardPage_browse_label;
  public static String NewApplicationWizardPage_directory_label;
  public static String NewApplicationWizardPage_project_name_label;
  public static String NewProjectCreationPage_create_msg;
  public static String NewProjectCreationPage_description;
  public static String NewProjectCreationPage_invalid_loc;
  public static String NewProjectCreationPage_NewProjectCreationPage_title;
  public static String NewApplicationWizardPage_open_existing;
  public static String NewApplicationWizardPage_create_new;
  public static String NewApplicationWizardPage_create_metadata;
  public static String OpenNewProjectWizardAction_desc;
  public static String OpenNewProjectWizardAction_text;
  public static String OpenNewProjectWizardAction_tooltip;
  static {
    // initialize resource bundle
    NLS.initializeMessages(BUNDLE_NAME, ProjectMessages.class);
  }

  private ProjectMessages() {
  }
}
