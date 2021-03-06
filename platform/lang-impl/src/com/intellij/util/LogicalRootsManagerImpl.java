// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.ProjectTopics;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author spleaner
 */
public class LogicalRootsManagerImpl extends LogicalRootsManager {
  private Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> myRoots = null;
  private final MultiValuesMap<LogicalRootType, NotNullFunction> myProviders = new MultiValuesMap<>();
  private final MultiValuesMap<FileType, LogicalRootType> myFileTypes2RootTypes = new MultiValuesMap<>();
  private final ModuleManager myModuleManager;
  private final Project myProject;

  public LogicalRootsManagerImpl(final MessageBus bus, final ModuleManager moduleManager, final Project project) {
    myModuleManager = moduleManager;
    myProject = project;

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(LOGICAL_ROOTS, new LogicalRootListener() {
      @Override
      public void logicalRootsChanged() {
        clear();
        //updateCache(moduleManager);
      }
    });
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        bus.syncPublisher(LOGICAL_ROOTS).logicalRootsChanged();
      }
    });
    registerLogicalRootProvider(LogicalRootType.SOURCE_ROOT,
                                module -> ContainerUtil.map2List(ModuleRootManager.getInstance(module).getSourceRoots(), s -> new VirtualFileLogicalRoot(s)));
  }

  private synchronized void clear() {
    myRoots = null;
  }

  private synchronized  Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> getRoots(final ModuleManager moduleManager) {
    if (myRoots == null) {
      myRoots = new THashMap<>();

      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        final MultiValuesMap<LogicalRootType, LogicalRoot> map = new MultiValuesMap<>();
        for (Map.Entry<LogicalRootType, Collection<NotNullFunction>> entry : myProviders.entrySet()) {
          final Collection<NotNullFunction> functions = entry.getValue();
          for (NotNullFunction function : functions) {
            map.putAll(entry.getKey(), (List<LogicalRoot>) function.fun(module));
          }
        }
        myRoots.put(module, map);
      }
    }

    return myRoots;
  }

  @Override
  @Nullable
  public LogicalRoot findLogicalRoot(@NotNull final VirtualFile file) {
    final Module module = ModuleUtil.findModuleForFile(file, myProject);
    if (module == null) return null;

    LogicalRoot result = null;
    final List<LogicalRoot> list = getLogicalRoots(module);
    for (final LogicalRoot root : list) {
      final VirtualFile rootFile = root.getVirtualFile();
      if (rootFile != null && VfsUtil.isAncestor(rootFile, file, false)) {
        result = root;
        break;
      }
    }

    return result;
  }

  @Override
  public List<LogicalRoot> getLogicalRoots() {
    return ContainerUtil.concat(myModuleManager.getModules(), module -> getLogicalRoots(module));
  }

  @Override
  public List<LogicalRoot> getLogicalRoots(@NotNull final Module module) {
    final Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> roots = getRoots(myModuleManager);
    final MultiValuesMap<LogicalRootType, LogicalRoot> valuesMap = roots.get(module);
    if (valuesMap == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(valuesMap.values());
  }

  @Override
  public <T extends LogicalRoot> List<T> getLogicalRootsOfType(@NotNull final Module module, @NotNull final LogicalRootType<T> type) {
    final Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> roots = getRoots(myModuleManager);
    final MultiValuesMap<LogicalRootType, LogicalRoot> map = roots.get(module);
    if (map == null) {
      return Collections.emptyList();
    }

    Collection<LogicalRoot> collection = map.get(type);
    if (collection == null) return Collections.emptyList();
    return new ArrayList<>((Collection<T>)collection);
  }

  @Override
  @NotNull
  public LogicalRootType[] getRootTypes(@NotNull final FileType type) {
    final Collection<LogicalRootType> rootTypes = myFileTypes2RootTypes.get(type);
    if (rootTypes == null) {
      return new LogicalRootType[0];
    }

    return rootTypes.toArray(new LogicalRootType[0]);
  }

  @Override
  public void registerRootType(@NotNull final FileType fileType, @NotNull final LogicalRootType... rootTypes) {
    myFileTypes2RootTypes.putAll(fileType, rootTypes);
  }

  @Override
  public <T extends LogicalRoot> void registerLogicalRootProvider(@NotNull final LogicalRootType<T> rootType, @NotNull NotNullFunction<Module, List<T>> provider) {
    myProviders.put(rootType, provider);
    clear();
  }
}
