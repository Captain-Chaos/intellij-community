// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.configuration.deployment;

import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.RemoteServersManager;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class DeployToServerConfigurationType extends ConfigurationTypeBase {
  private final ServerType<?> myServerType;
  private final MultiSourcesConfigurationFactory myMultiSourcesFactory;
  private final Map<SingletonDeploymentSourceType, SingletonTypeConfigurationFactory> myPerTypeFactories = new HashMap<>();

  public DeployToServerConfigurationType(@NotNull ServerType<?> serverType) {
    super(serverType.getId() + "-deploy", serverType.getDeploymentConfigurationTypePresentableName(),
          "Deploy to " + serverType.getPresentableName() + " run configuration", serverType.getIcon());

    myServerType = serverType;
    if (myServerType.mayHaveProjectSpecificDeploymentSources()) {
      myMultiSourcesFactory = new MultiSourcesConfigurationFactory();
      addFactory(myMultiSourcesFactory);
    }
    else {
      myMultiSourcesFactory = null;
    }

    for (SingletonDeploymentSourceType next : serverType.getSingletonDeploymentSourceTypes()) {
      SingletonTypeConfigurationFactory nextFactory = new SingletonTypeConfigurationFactory(next);
      addFactory(nextFactory);
      myPerTypeFactories.put(next, nextFactory);
    }
  }

  /**
   * @param sourceType hint for a type of deployment source or null if unknown
   */
  @NotNull
  public ConfigurationFactoryEx getFactoryForType(@Nullable DeploymentSourceType<?> sourceType) {
    ConfigurationFactoryEx result = null;
    if (sourceType instanceof SingletonDeploymentSourceType && myServerType.getSingletonDeploymentSourceTypes().contains(sourceType)) {
      result = myPerTypeFactories.get(sourceType);
    }
    if (result == null) {
      result = myMultiSourcesFactory;
    }
    assert result != null : "server type: " + myServerType + ", requested source type: " + sourceType;
    return result;
  }

  /**
   * Will be removed after 2017.3
   *
   * @deprecated use {@link #getFactoryForType(DeploymentSourceType)}
   */
  @Deprecated
  public ConfigurationFactoryEx getFactory() {
    return getFactoryForType(null);
  }

  @NotNull
  public ServerType<?> getServerType() {
    return myServerType;
  }

  public class DeployToServerConfigurationFactory extends ConfigurationFactoryEx {
    public DeployToServerConfigurationFactory() {
      super(DeployToServerConfigurationType.this);
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return myServerType.canAutoDetectConfiguration() || !RemoteServersManager.getInstance().getServers(myServerType).isEmpty();
    }

    @Override
    public void onNewConfigurationCreated(@NotNull RunConfiguration configuration) {
      DeployToServerRunConfiguration<?, ?> deployConfiguration = (DeployToServerRunConfiguration<?, ?>)configuration;
      if (deployConfiguration.getServerName() == null) {
        RemoteServer<?> server = ContainerUtil.getFirstItem(RemoteServersManager.getInstance().getServers(myServerType));
        if (server != null) {
          deployConfiguration.setServerName(server.getName());
        }
      }

      if (deployConfiguration.getDeploymentSource() == null) {
        setupDeploymentSource(configuration, deployConfiguration);
      }
    }

    private <S extends ServerConfiguration, D extends DeploymentConfiguration> void setupDeploymentSource(
      @NotNull RunConfiguration configuration, @NotNull DeployToServerRunConfiguration<S, D> deployConfiguration) {

      DeploymentConfigurator<D, S> deploymentConfigurator = deployConfiguration.getDeploymentConfigurator();
      List<DeploymentSource> sources = deploymentConfigurator.getAvailableDeploymentSources();
      DeploymentSource source = ContainerUtil.getFirstItem(sources);
      if (source != null) {
        deployConfiguration.setDeploymentSource(source);
        deployConfiguration.setDeploymentConfiguration(deploymentConfigurator.createDefaultConfiguration(source));
        DeploymentSourceType type = source.getType();
        //noinspection unchecked
        type.setBuildBeforeRunTask(configuration, source);
      }
    }

    @Override
    @NotNull
    public DeployToServerRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      DeploymentConfigurator<?, ?> deploymentConfigurator = myServerType.createDeploymentConfigurator(project);
      //noinspection unchecked
      return new DeployToServerRunConfiguration(project, this, "", myServerType, deploymentConfigurator);
    }
  }

  public class MultiSourcesConfigurationFactory extends DeployToServerConfigurationFactory {
    @NotNull
    @Override
    public String getId() {
      //compatibility reasons, before 173 it was the only configuration factory stored with this ID
      return DeployToServerConfigurationType.this.getDisplayName();
    }
  }

  public class SingletonTypeConfigurationFactory extends DeployToServerConfigurationFactory {
    private final SingletonDeploymentSourceType mySourceType;

    public SingletonTypeConfigurationFactory(@NotNull SingletonDeploymentSourceType sourceType) {
      mySourceType = sourceType;
    }

    @NotNull
    @Override
    public String getId() {
      return mySourceType.getId();
    }

    @NotNull
    @Nls
    @Override
    public String getName() {
      return mySourceType.getPresentableName();
    }

    @NotNull
    @Override
    public DeployToServerRunConfiguration createTemplateConfiguration(@NotNull Project project) {
      DeployToServerRunConfiguration result = super.createTemplateConfiguration(project);
      result.lockDeploymentSource(mySourceType);
      return result;
    }
  }
}

