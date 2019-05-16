// From the CIDRE project, an illumination correction method for optical
// microscopy (https://github.com/smithk/cidre).
// Copyright © 2015 Kevin Smith and Peter Horvath, adapted in Java by 
// Csaba Balazs. Scientific Center for Optical and Electron Microscopy 
// (SCOPEM), Swiss Federal Institute of Technology Zurich (ETH Zurich), 
// Switzerland. All rights reserved.
//
// CIDRE is free software; you can redistribute it and/or modify it 
// under the terms of the GNU General Public License version 2 (or higher) 
// as published by the Free Software Foundation. See the license file in
// the root folder. This program is distributed WITHOUT ANY WARRANTY; 
// without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
// PARTICULAR PURPOSE.  See the GNU General Public License for more details.

package com.cidre.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;

public class ImageCorrection {

    private static final Logger log =
        LoggerFactory.getLogger(ImageCorrection.class);

    public static float[][] correctPlane(
        double[][] pixelData, ModelDescriptor descriptor,
        Options.CorrectionMode correctionMode, boolean useMinImage)
    {
        double minImageMean = CidreMath.mean(descriptor.minImage);
        double enumerator, denominator;
        int width = descriptor.imageSize.width,
            height = descriptor.imageSize.height;
        double mean_v = CidreMath.mean(descriptor.v);
        double mean_z = CidreMath.mean(descriptor.z);
        log.debug("{}, {}", width, height);
        log.debug("{}, {} ,{}", mean_v, mean_z, minImageMean);
        float[][] floatArray = new float[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (useMinImage) {
                    enumerator = pixelData[x][y] - (
                        descriptor.minImage[x * height + y]
                        - minImageMean + mean_z);
                } else {
                    enumerator = pixelData[x][y]
                               - descriptor.z[x * height + y];
                }
                denominator = descriptor.v[x * height + y];
                switch(correctionMode)
                {
                    case ZERO_LIGHT_PRESERVED:
                        floatArray[x][y] = (float) (
                            ((enumerator / denominator) * mean_v) + mean_z);
                        break;
                    case DYNAMIC_RANGE_CORRECTED:
                        floatArray[x][y] = (float) (
                             (enumerator / denominator) * mean_v);
                        break;
                    case DIRECT:
                        floatArray[x][y] = (float) (
                            enumerator / denominator);
                        break;
                    default:
                        log.error("Unrecognized correction mode.");
                        break;
                }
            }
        }
        log.debug("Image size [{}, {}], mean: {}",
                 floatArray.length, floatArray[0].length,
                 CidreMath.mean(floatArray));
        return floatArray;
    }

}
