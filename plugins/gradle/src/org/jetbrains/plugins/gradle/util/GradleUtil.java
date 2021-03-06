package org.jetbrains.plugins.gradle.util;

import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.project.id.GradleSyntheticId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureTreeModel;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNodeDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileTypeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.ui.MatrixControlBuilder;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.*;
import java.util.Arrays;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds miscellaneous utility methods.
 *
 * @author Denis Zhdanov
 * @since 8/25/11 1:19 PM
 */
public class GradleUtil {

  public static final  String  SYSTEM_DIRECTORY_PATH_KEY    = "GRADLE_USER_HOME";
  private static final String  WRAPPER_VERSION_PROPERTY_KEY = "distributionUrl";
  private static final Pattern WRAPPER_VERSION_PATTERN      = Pattern.compile(".*gradle-(.+)-bin.zip");

  private static final NotNullLazyValue<GradleInstallationManager> INSTALLATION_MANAGER =
    new NotNullLazyValue<GradleInstallationManager>() {
      @NotNull
      @Override
      protected GradleInstallationManager compute() {
        return ServiceManager.getService(GradleInstallationManager.class);
      }
    };

  private GradleUtil() {
  }

  /**
   * Allows to retrieve file chooser descriptor that filters gradle scripts.
   * <p/>
   * <b>Note:</b> we want to fall back to the standard {@link FileTypeDescriptor} when dedicated gradle file type
   * is introduced (it's processed as groovy file at the moment). We use open project descriptor here in order to show
   * custom gradle icon at the file chooser ({@link icons.GradleIcons#Gradle}, is used at the file chooser dialog via
   * the dedicated gradle project open processor).
   */
  @NotNull
  public static FileChooserDescriptor getGradleProjectFileChooserDescriptor() {
    return DescriptorHolder.GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR;
  }

  @NotNull
  public static FileChooserDescriptor getGradleHomeFileChooserDescriptor() {
    return DescriptorHolder.GRADLE_HOME_FILE_CHOOSER_DESCRIPTOR;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  public static boolean isGradleWrapperDefined(@Nullable String gradleProjectPath) {
    return !StringUtil.isEmpty(getWrapperVersion(gradleProjectPath));
  }

  /**
   * Tries to parse what gradle version should be used with gradle wrapper for the gradle project located at the given path. 
   *
   * @param gradleProjectPath  target gradle project path
   * @return gradle version should be used with gradle wrapper for the gradle project located at the given path
   *                           if any; <code>null</code> otherwise
   */
  @Nullable
  public static String getWrapperVersion(@Nullable String gradleProjectPath) {
    if (gradleProjectPath == null) {
      return null;
    }
    File file = new File(gradleProjectPath);
    if (!file.isFile()) {
      return null;
    }

    File gradleDir = new File(file.getParentFile(), "gradle");
    if (!gradleDir.isDirectory()) {
      return null;
    }

    File wrapperDir = new File(gradleDir, "wrapper");
    if (!wrapperDir.isDirectory()) {
      return null;
    }

    File[] candidates = wrapperDir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File candidate) {
        return candidate.isFile() && candidate.getName().endsWith(".properties");
      }
    });
    if (candidates == null) {
      GradleLog.LOG.warn("No *.properties file is found at the gradle wrapper directory " + wrapperDir.getAbsolutePath());
      return null;
    }
    else if (candidates.length != 1) {
      GradleLog.LOG.warn(String.format(
        "%d *.properties files instead of one have been found at the wrapper directory (%s): %s",
        candidates.length, wrapperDir.getAbsolutePath(), Arrays.toString(candidates)
      ));
      return null;
    }

    Properties props = new Properties();
    BufferedReader reader = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      reader = new BufferedReader(new FileReader(candidates[0]));
      props.load(reader);
      String value = props.getProperty(WRAPPER_VERSION_PROPERTY_KEY);
      if (StringUtil.isEmpty(value)) {
        return null;
      }
      Matcher matcher = WRAPPER_VERSION_PATTERN.matcher(value);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    catch (IOException e) {
      GradleLog.LOG.warn(
        String.format("I/O exception on reading gradle wrapper properties file at '%s'", candidates[0].getAbsolutePath()),
        e
      );
    }
    finally {
      if (reader != null) {
        try {
          reader.close();
        }
        catch (IOException e) {
          // Ignore
        }
      }
    }
    return null;
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
   * Tries to calculate the position to use for showing hint for the given node of the given tree.
   *
   * @param node  target node for which a hint should be shown
   * @param tree  target tree that contains given node
   * @return      preferred hint position (in coordinates relative to the given tree) if it's possible to calculate the one;
   *              <code>null</code> otherwise
   */
  @Nullable
  public static Point getHintPosition(@NotNull ProjectStructureNode<?> node, @NotNull Tree tree) {
    final Rectangle bounds = tree.getPathBounds(new TreePath(node.getPath()));
    if (bounds == null) {
      return null;
    }
    final Icon icon = ((ProjectStructureNode)node).getDescriptor().getIcon();
    int xAdjustment = 0;
    if (icon != null) {
      xAdjustment = icon.getIconWidth();
    }
    return new Point(bounds.x + xAdjustment, bounds.y + bounds.height);
  }

  /**
   * Tries to find the current {@link ExternalProjectStructureTreeModel} instance.
   *
   * @param context  target context (if defined)
   * @return         current {@link ExternalProjectStructureTreeModel} instance (if any has been found); <code>null</code> otherwise
   */
  @Nullable
  public static ExternalProjectStructureTreeModel getProjectStructureTreeModel(@Nullable DataContext context) {
    // TODO den implement
    return null;
//    return getToolWindowElement(ExternalProjectStructureTreeModel.class, context, ExternalSystemDataKeys.PROJECT_TREE_MODEL);
  }

  /**
   * @return    {@link MatrixControlBuilder} with predefined set of columns ('gradle' and 'intellij')
   */
  @NotNull
  public static MatrixControlBuilder getConflictChangeBuilder() {
    // TODO den implement
    final String gradle = "";
    final String intellij = "";
//    final String gradle = ExternalSystemBundle.message("gradle.name");
//    final String intellij = ExternalSystemBundle.message("gradle.ide");
    return new MatrixControlBuilder(gradle, intellij);
  }

  public static boolean isGradleAvailable(@Nullable Project project) {
    if (project != null) {
      GradleSettings settings = GradleSettings.getInstance(project);
      if (!settings.isPreferLocalInstallationToWrapper() && isGradleWrapperDefined(settings.getLinkedExternalProjectPath())) {
        return true;
      }
    }
    return INSTALLATION_MANAGER.getValue().getGradleHome(project) != null;
  }

  /**
   * We use this class in order to avoid static initialisation of the wrapped object - it loads number of pico container-based
   * dependencies that are unavailable to the slave gradle project, so, we don't want to get unexpected NPE there.
   */
  private static class DescriptorHolder {
    public static final FileChooserDescriptor GRADLE_BUILD_FILE_CHOOSER_DESCRIPTOR = new OpenProjectFileChooserDescriptor(true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName());
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!super.isFileVisible(file, showHiddenFiles)) {
          return false;
        }
        return file.isDirectory() || GradleConstants.DEFAULT_SCRIPT_NAME.equals(file.getName());
      }
    };

    public static final FileChooserDescriptor GRADLE_HOME_FILE_CHOOSER_DESCRIPTOR
      = new FileChooserDescriptor(false, true, false, false, false, false);
  }
}
