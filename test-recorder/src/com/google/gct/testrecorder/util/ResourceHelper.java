/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gct.testrecorder.util;

import com.android.annotations.Nullable;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class ResourceHelper {

  public static File getFileForResource(@NotNull Object resourceRequester, @NotNull String resourcePath, @NotNull String prefix,
                                        @Nullable String suffix) {
    InputStream in = resourceRequester.getClass().getClassLoader().getResourceAsStream(resourcePath);
    FileOutputStream out = null;
    try {
      // TODO: Re-use unpacked resource across Test Recorder runs (for performance).
      File tempFile = File.createTempFile(prefix, suffix);
      tempFile.deleteOnExit();
      out = new FileOutputStream(tempFile);
      IOUtils.copy(in, out);
      return tempFile;
    } catch(Exception e) {
      throw new RuntimeException("Could not create file for resource " + resourcePath, e);
    } finally {
      try {
        in.close();
        if (out != null) {
          out.close();
        }
      } catch (Exception e) {
        // ignore
      }
    }
  }

  public static byte[] readResourceFully(@NotNull Object resourceRequester, @NotNull String resourcePath) {
    ByteArrayOutputStream readBytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    InputStream in = resourceRequester.getClass().getClassLoader().getResourceAsStream(resourcePath);
    try {
      while ((count = in.read(buffer)) != -1) {
        readBytes.write(buffer, 0, count);
      }
    } catch (Exception e) {
      throw new RuntimeException("Could not read resource " + resourcePath, e);
    } finally {
      try {
        in.close();
      } catch (Exception e) {
        // ignore
      }
    }
    return readBytes.toByteArray();
  }

}
