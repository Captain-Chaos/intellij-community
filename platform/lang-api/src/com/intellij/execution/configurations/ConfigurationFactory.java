// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Factory for run configuration instances.
 * @see com.intellij.execution.configurations.ConfigurationType#getConfigurationFactories()
 */
public abstract class ConfigurationFactory {
  private final ConfigurationType myType;

  protected ConfigurationFactory(@NotNull final ConfigurationType type) {
    myType = type;
  }

  /**
   * Creates a new run configuration with the specified name by cloning the specified template.
   *
   * @param name the name for the new run configuration.
   * @param template the template from which the run configuration is copied
   * @return the new run configuration.
   */
  public RunConfiguration createConfiguration(String name, RunConfiguration template) {
    RunConfiguration newConfiguration = template.clone();
    newConfiguration.setName(name);
    return newConfiguration;
  }

  /**
   * Override this method and return {@code false} to hide the configuration from 'New' popup in 'Edit Configurations' dialog. It will be
   * still possible to create this configuration by clicking on '42 more items' in the 'New' popup.
   *
   * @return {@code true} if it makes sense to create configurations of this type in {@code project}
   */
  public boolean isApplicable(@NotNull Project project) {
    return true;
  }

  /**
   * Creates a new template run configuration within the context of the specified project.
   *
   * @param project the project in which the run configuration will be used
   * @return the run configuration instance.
   */
  @NotNull
  public abstract RunConfiguration createTemplateConfiguration(@NotNull Project project);

  @NotNull
  public RunConfiguration createTemplateConfiguration(@NotNull Project project, @NotNull RunManager runManager) {
    return createTemplateConfiguration(project);
  }

  /**
   * Returns the id of the run configuration that is used for serialization.
   * For compatibility reason the default implementation calls
   * the method <code>getName</code> instead of <code>myType.getId()</code>.
   * New implementations need to call <code>myType.getId()</code> by default.
   */
  @NotNull
  public String getId() {
    return getName();
  }

  /**
   * The name of the run configuration variant created by this factory.
   */
  @NotNull
  public String getName() {
    return myType.getDisplayName();
  }

  /**
   * @deprecated Use {@link com.intellij.icons.AllIcons.General.Add}
   */
  @Deprecated
  public Icon getAddIcon() {
    return IconUtil.getAddIcon();
  }

  public Icon getIcon(@NotNull final RunConfiguration configuration) {
    return getIcon();
  }

  public Icon getIcon() {
    return myType.getIcon();
  }

  @NotNull
  public ConfigurationType getType() {
    return myType;
  }

  /**
   * In this method you can configure defaults for the task, which are preferable to be used for your particular configuration type
   */
  public void configureBeforeRunTaskDefaults(Key<? extends BeforeRunTask> providerID, BeforeRunTask task) {
  }

  /**
   * @deprecated Use {@link RunConfigurationSingletonPolicy}
   */
  @Deprecated
  public boolean isConfigurationSingletonByDefault() {
    return getSingletonPolicy() != RunConfigurationSingletonPolicy.MULTIPLE_INSTANCE;
  }

  /**
   * @deprecated Use {@link RunConfigurationSingletonPolicy}
   */
  @Deprecated
  public boolean canConfigurationBeSingleton() {
    return getSingletonPolicy() != RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY;
  }

  @NotNull
  public RunConfigurationSingletonPolicy getSingletonPolicy() {
    return RunConfigurationSingletonPolicy.SINGLE_INSTANCE;
  }
}
