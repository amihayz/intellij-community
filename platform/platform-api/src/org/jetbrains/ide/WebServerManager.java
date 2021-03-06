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
package org.jetbrains.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

public abstract class WebServerManager {
  // Your handler will be instantiated on first user request
  public static final ExtensionPointName<HttpRequestHandler> EP_NAME = ExtensionPointName.create("com.intellij.httpRequestHandler");

  public static WebServerManager getInstance() {
    return ServiceManager.getService(WebServerManager.class);
  }

  public abstract int getPort();

  public abstract WebServerManager waitForStart();

  @Nullable
  public abstract Disposable getServerDisposable();
}