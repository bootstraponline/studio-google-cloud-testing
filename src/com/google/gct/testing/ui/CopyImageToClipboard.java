/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.testing.ui;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class CopyImageToClipboard implements ClipboardOwner {

  public static void copy(BufferedImage image) {
    CopyImageToClipboard copier = new CopyImageToClipboard();
    copier.copyImageToClipboard(image);
  }

  private void copyImageToClipboard(BufferedImage image) {
    TransferableImage trans = new TransferableImage(image);
    Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
    c.setContents(trans, this);
  }

  @Override
  public void lostOwnership(Clipboard clip, Transferable trans) {
    // TODO: Probably we should do something here.  But what?
    System.out.println("Lost Clipboard Ownership");
  }

  private class TransferableImage implements Transferable {
    Image image;

    public TransferableImage(Image image) {
      this.image = image;
    }

    @Override
    public Object getTransferData(DataFlavor flavor)
      throws UnsupportedFlavorException, IOException {
      if (flavor.equals(DataFlavor.imageFlavor) && image != null) {
        return image;
      }
      else {
        throw new UnsupportedFlavorException(flavor);
      }
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
      DataFlavor[] flavors = new DataFlavor[1];
      flavors[0] = DataFlavor.imageFlavor;
      return flavors;
    }

    @Override
    public boolean isDataFlavorSupported( DataFlavor flavor ) {
      DataFlavor[] flavors = getTransferDataFlavors();
      for (int i = 0; i < flavors.length; i++) {
        if (flavor.equals(flavors[i])) {
          return true;
        }
      }

      return false;
    }
  }
}
