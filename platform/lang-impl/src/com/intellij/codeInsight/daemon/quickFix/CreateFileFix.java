/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author peter
*/
public class CreateFileFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private final boolean myIsDirectory;
  private final String myNewFileName;
  private final String myText;
  @NotNull private String myKey;
  private boolean myIsAvailable;
  private long myIsAvailableTimeStamp;
  private static final int REFRESH_INTERVAL = 1000;

  public CreateFileFix(boolean isDirectory,
                       @NotNull String newFileName,
                       @NotNull PsiDirectory directory,
                       @Nullable String text,
                       @NotNull String key) {
    super(directory);

    myIsDirectory = isDirectory;
    myNewFileName = newFileName;
    myText = text;
    myKey = key;
    myIsAvailable = isDirectory || !FileTypeManager.getInstance().getFileTypeByFileName(newFileName).isBinary();
    myIsAvailableTimeStamp = System.currentTimeMillis();
  }

  public CreateFileFix(final String newFileName,
                                   @NotNull PsiDirectory directory, String text) {
    this(false,newFileName,directory, text, "create.file.text" );
  }

  public CreateFileFix(final boolean isDirectory,
                                   final String newFileName,
                                   @NotNull PsiDirectory directory) {
    this(isDirectory,newFileName,directory,null, isDirectory ? "create.directory.text":"create.file.text" );
  }

  @Nullable
  protected String getFileText() {
    return myText;
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message(myKey, myNewFileName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("create.file.family");
  }

  @Override
  public void invoke(@NotNull final Project project,
                     @NotNull PsiFile file,
                     Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiDirectory myDirectory = (PsiDirectory)startElement;
    if (isAvailable(project, null, file)) {
      new WriteCommandAction(project) {
        @Override
        protected void run(Result result) throws Throwable {
          invoke(project, myDirectory);
        }
      }.execute();
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    final PsiDirectory myDirectory = (PsiDirectory)startElement;
    long current = System.currentTimeMillis();

    if (ApplicationManager.getApplication().isUnitTestMode() || current - myIsAvailableTimeStamp > REFRESH_INTERVAL) {
      myIsAvailable &= myDirectory.getVirtualFile().findChild(myNewFileName) == null;
      myIsAvailableTimeStamp = current;
    }

    return myIsAvailable;
  }

  private void invoke(@NotNull Project project, PsiDirectory myDirectory) throws IncorrectOperationException {
    myIsAvailableTimeStamp = 0; // to revalidate applicability

    try {
      if (myIsDirectory) {
        myDirectory.createSubdirectory(myNewFileName);
      }
      else {
        String newFileName = myNewFileName;
        String newDirectories = null;
        if (myNewFileName.contains("/")) {
          int pos = myNewFileName.lastIndexOf("/");
          newFileName = myNewFileName.substring(pos + 1);
          newDirectories = myNewFileName.substring(0, pos);
        }
        PsiDirectory directory = myDirectory;
        if (newDirectories != null) {
          try {
            VfsUtil.createDirectoryIfMissing(myDirectory.getVirtualFile(), newDirectories);
            VirtualFile vfsDir = VfsUtil.findRelativeFile(myDirectory.getVirtualFile(), ArrayUtil.toStringArray(StringUtil.split(newDirectories, "/")));
            directory = new PsiDirectoryImpl((PsiManagerImpl)myDirectory.getManager(), vfsDir);
          }
          catch (IOException e) {
            throw new IncorrectOperationException(e.getMessage());
          }
        }
        final PsiFile newFile = directory.createFile(newFileName);
        String text = getFileText();

        if (text != null) {
          final FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(newFileName);
          final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("_" + newFileName, type, text);
          final PsiElement psiElement = CodeStyleManager.getInstance(project).reformat(psiFile);
          text = psiElement.getText();
        }

        final FileEditorManager editorManager = FileEditorManager.getInstance(directory.getProject());
        final FileEditor[] fileEditors = editorManager.openFile(newFile.getVirtualFile(), true);

        if (text != null) {
          for(FileEditor fileEditor: fileEditors) {
            if (fileEditor instanceof TextEditor) { // JSP is not safe to edit via Psi
              final Document document = ((TextEditor)fileEditor).getEditor().getDocument();
              document.setText(text);

              if (ApplicationManager.getApplication().isUnitTestMode()) {
                FileDocumentManager.getInstance().saveDocument(document);
              }
              PsiDocumentManager.getInstance(project).commitDocument(document);
              break;
            }
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      myIsAvailable = false;
    }
  }
}
