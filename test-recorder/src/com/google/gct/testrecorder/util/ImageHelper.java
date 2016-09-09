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

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;

public class ImageHelper {

  public static BufferedImage rotateImage(BufferedImage image, int rotation) {
    if (rotation == 0) {
      return image; // No need to rotate for the standard portrait mode.
    }

    AffineTransform transform = new AffineTransform();
    transform.translate(image.getHeight() / 2, image.getWidth() / 2);
    transform.rotate(getTheta(rotation));
    transform.translate(-image.getWidth() / 2, -image.getHeight() / 2);
    AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
    return op.filter(image, null);
  }

  private static double getTheta(int rotation) {
    switch (rotation) {
      case 1:
        return -Math.PI / 2;
      case 2:
        return Math.PI;
      case 3:
        return Math.PI / 2;
    }

    return 0.0; // Do not rotate if rotation is not recognized.
  }
}
