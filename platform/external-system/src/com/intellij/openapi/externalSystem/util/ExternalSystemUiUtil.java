/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.project.id.GradleSyntheticId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNodeDescriptor;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Denis Zhdanov
 * @since 4/8/13 7:29 PM
 */
public class ExternalSystemUiUtil {

  public static final ProjectStructureNodeDescriptor<GradleSyntheticId> DEPENDENCIES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(ExternalSystemBundle.message("entity.type.dependencies"));

  public static final ProjectStructureNodeDescriptor<GradleSyntheticId> MODULES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(ExternalSystemBundle.message("entity.type.modules"));

  public static final ProjectStructureNodeDescriptor<GradleSyntheticId> LIBRARIES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(ExternalSystemBundle.message("entity.type.libraries"));
  
  private ExternalSystemUiUtil() {
  }

  @NotNull
  public static <T extends ProjectEntityId> ProjectStructureNodeDescriptor<T> buildDescriptor(@NotNull T id, @NotNull String name) {
    return new ProjectStructureNodeDescriptor<T>(id, name, id.getType().getIcon());
  }

  @NotNull
  public static ProjectStructureNodeDescriptor<GradleSyntheticId> buildSyntheticDescriptor(@NotNull String text) {
    return buildSyntheticDescriptor(text, null);
  }

  public static ProjectStructureNodeDescriptor<GradleSyntheticId> buildSyntheticDescriptor(@NotNull String text, @Nullable Icon icon) {
    return new ProjectStructureNodeDescriptor<GradleSyntheticId>(new GradleSyntheticId(text), text, icon);
  }

  /**
   * Asks to show balloon that contains information related to the given component.
   *
   * @param component    component for which we want to show information
   * @param messageType  balloon message type
   * @param message      message to show
   */
  public static void showBalloon(@NotNull JComponent component, @NotNull MessageType messageType, @NotNull String message) {
    final BalloonBuilder builder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(message, messageType, null)
                                                 .setDisposable(ApplicationManager.getApplication())
                                                 .setFadeoutTime(TimeUnit.SECONDS.toMillis(1));
    Balloon balloon = builder.createBalloon();
    Dimension size = component.getSize();
    Balloon.Position position;
    int x;
    int y;
    if (size == null) {
      x = y = 0;
      position = Balloon.Position.above;
    }
    else {
      x = Math.min(10, size.width / 2);
      y = size.height;
      position = Balloon.Position.below;
    }
    balloon.show(new RelativePoint(component, new Point(x, y)), position);
  }
}
