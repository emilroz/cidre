/*
 * Copyright (C) 2019 Glencoe Software, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.cidre.io;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.algorithms.CidreMath;
import com.cidre.core.Options;
import com.cidre.preprocessing.CidrePreprocess;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.common.DataTools;
import loci.formats.FormatTools;
import loci.formats.ImageReader;
import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.services.OMEXMLService;
import ome.xml.model.enums.PixelType;

public class BfImageLoader extends ImageLoader {

    private static final Logger log =
            LoggerFactory.getLogger(BfImageLoader.class);

    // passed in
    private List<Integer> series = new ArrayList<Integer>();
    private List<Integer> zSections = new ArrayList<Integer>();
    private List<Integer> timepoints = new ArrayList<Integer>();

    // derived from passed in
    private int maxS;
    private int maxT;
    private int maxZ;

    // derived from reader
    private int sizeX;
    private int sizeY;
    private int sizeS;
    private int sizeC;
    private int sizeT;
    private int sizeZ;
    private int pixelType;
    private int bitsPerPixel;

    private List<ImageReader> readers;

    public BfImageLoader(
            Options options, String source,
            List<Integer> series, List<Integer> zSections,
            List<Integer> timepoints)
    {
        super(options, source);
        this.series = new ArrayList<Integer>(series);
        this.timepoints = new ArrayList<Integer>(timepoints);
        this.zSections = new ArrayList<Integer>(zSections);
        this.readers = new ArrayList<ImageReader>();
    }

    public BfImageLoader(
            Options options, String source,
            List<Integer> series, Integer zSection,
            Integer timepoint)
    {
        super(options, source);
        this.series = new ArrayList<Integer>(series);
        this.timepoints = Arrays.asList(timepoint);
        this.zSections = Arrays.asList(zSection);
        this.readers = new ArrayList<ImageReader>();
    }

    public BfImageLoader(Options options, String source) {
        super(options, source);
        this.readers = new ArrayList<ImageReader>();
    }

    @Override
    public boolean initialise() throws Exception {
        this.getFileList(this.source);
        this.populateDimensions(); //  Call it first!!!!!!
        this.readers.clear();
        for (String fileName : this.options.fileNames) {
            ImageReader reader = new ImageReader();
            BfImageLoader.initializeReader(reader);
            reader.setId(fileName);
            if (!this.checkReaderDimensions(reader)) {
                return false;
            }
            this.readers.add(reader);
        }
        // store the number of source images into the options structure
        int numberOfS =
            this.series.isEmpty() ? this.sizeS : this.series.size();
        int numberOfT =
            this.timepoints.isEmpty() ? this.sizeT : this.timepoints.size();
        int numberOfZ =
            this.zSections.isEmpty() ? this.sizeZ : this.zSections.size();
        this.options.numImagesProvided = numberOfS * numberOfT * numberOfZ;
        log.info("Using {} images to build the model",
                 this.options.numImagesProvided);
        if (this.options.numImagesProvided <= 0) {
            log.error("Empty dimension found. Nothing to read.");
            return false;
        }
        this.options.imageSize = new Dimension(this.sizeX, this.sizeY);
        this.options.workingSize = determineWorkingSize(
            this.options.imageSize, this.options.targetNumPixels);
        this.initialised = true;
        return true;
    }

    @Override
    protected void setBitDepth() {
        // Sets options.bitDepth describing the provided images as 8-bit,
        // 12-bit, or  16-bit. If options.bitDepth is provided, it is used.
        // Otherwise the bit depth is estimated from the max observed
        // intensity, maxI.
        final int xy_2_8 = 256;
        final int xy_2_12 = 4096;
        final int xy_2_16 = 65536;

        if (maxI > xy_2_12 && bitsPerPixel > 8) {
             this.options.bitDepth = xy_2_16;
        } else if (maxI > xy_2_8 && bitsPerPixel > 8) {
             this.options.bitDepth = xy_2_12;
        } else {
             this.options.bitDepth = xy_2_8;
        }
         log.info(
             " {}-bit depth images (estimated from max intensity={})",
             Math.round(Math.log(options.bitDepth) / Math.log(2)), maxI);
    }

    @Override
    public boolean loadImages(int channel) throws Exception {
        log.info("Loading planes from channel {}", channel);
        if (!this.initialised) {
            this.initialise();
        }
        this.checkRequestedDimensions();
        this.readPlanes(channel);
        this.preprocessData();
        return true;
    }

    private void populateDimensions() throws Exception {
        if (!this.series.isEmpty()) {
            this.maxS = Collections.max(this.series);
        } else {
            this.maxS = -1;
        }
        if (!this.timepoints.isEmpty()) {
            this.maxT = Collections.max(this.timepoints);
        } else {
            this.maxT = -1;
        }
        if (!this.zSections.isEmpty()) {
            this.maxZ = Collections.max(this.zSections);
        } else {
            this.maxZ = -1;
        }
        ImageReader reader = new ImageReader();
        BfImageLoader.initializeReader(reader);
        reader.setId(this.options.fileNames.get(0));
        if (!this.series.isEmpty()) {
            reader.setSeries(this.series.get(0));
        } else {
            reader.setSeries(0);
        }
        this.sizeS = reader.getSeriesCount();
        this.sizeC = reader.getSizeC();
        this.sizeT = reader.getSizeT();
        this.sizeZ = reader.getSizeZ();
        this.sizeX = reader.getSizeX();
        this.sizeY = reader.getSizeY();
        this.pixelType = reader.getPixelType();
        this.bitsPerPixel = reader.getBitsPerPixel();
        if (!this.checkDimensions(reader)) {
            throw new Exception("Dimesion check failed.");
        }
    }

    private void checkRequestedDimensions() throws Exception {
        if (this.series.isEmpty()) {
            this.series = IntStream.range(0, this.sizeS).boxed().collect(
                Collectors.toList());
        }
        if (this.timepoints.isEmpty()) {
            this.timepoints = IntStream.range(0, this.sizeT).boxed().collect(
                Collectors.toList());
        }
        if (this.zSections.isEmpty()) {
            this.zSections = IntStream.range(0, this.sizeZ).boxed().collect(
                Collectors.toList());
        }
    }

    public static void initializeReader(ImageReader reader)
            throws DependencyException, ServiceException
    {
        reader.setOriginalMetadataPopulated(true);
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        reader.setMetadataStore(
            service.createOMEXMLMetadata(null, null));
        MetadataOptions options = new DefaultMetadataOptions();
        options.setValidate(true);
        reader.setMetadataOptions(options);
    }

    private boolean checkDimensions(ImageReader reader)
    {
        boolean noError = true;
        if (maxS >= reader.getSeriesCount()) {
            log.error("Not enough series in {}", reader.getCurrentFile());
            noError = false;
        }
        if (maxT >= reader.getSizeT()) {
            log.error("Not enough timpoints in {}", reader.getCurrentFile());
            noError = false;
        }
        if (maxZ >= reader.getSizeZ()) {
            log.error("Not enough z sections in {}", reader.getCurrentFile());
            noError = false;
        }
        return noError;
    }

    private boolean checkReaderDimensions(ImageReader reader) {
        boolean noError = true;
        if (this.sizeS != reader.getSeriesCount()) {
            log.error("Series count differes for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeC != reader.getSizeC()) {
            log.error("Channel count differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeT != reader.getSizeT()) {
            log.error(
                "Timepoints count differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeZ != reader.getSizeZ()) {
            log.error(
                "Z section count differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeX != reader.getSizeX()) {
            log.error(
                "Width differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.sizeY != reader.getSizeY()) {
            log.error(
                "Height differs for {}", reader.getCurrentFile());
            noError = false;
        }
        if (this.pixelType != reader.getPixelType()) {
            log.error("Pixel type differs for {}", reader.getCurrentFile());
        }
        return noError;
    }

    public static double[][] toDoubleArray(
            byte[] b, int bpp, boolean fp, boolean little, boolean unsigned,
            int width, int height)
    {
        log.debug("Converting to double array with bpp={}", bpp);
        double[][] doubles = new double[width][height];
        if (bpp == 1) {
            byte minValue = 0;
            if (unsigned)
                minValue = Byte.MIN_VALUE;
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x][y] = (double) (b[x + y * width] - minValue);
                }
            }
            return doubles;
        }
        else if (bpp == 2) {
            double minValue = 0;
            if (!unsigned)
                minValue = (double) (Short.MIN_VALUE);
            log.debug("Converting to double array with min={}", minValue);
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x][y] = (double) (DataTools.bytesToInt(
                        b, x * 2 + y * 2 * width, 2, little) - minValue);
                }
            }
            return doubles;
        }
        else if (bpp == 4 && fp) {
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x][y] = (double) DataTools.bytesToFloat(
                        b, x * 4 + y * 4 * width, 4, little);
                }
            }
            return doubles;
        }
        else if (bpp == 4) {
            for (int y = 0; y < height; y++) {
                int minValue = 0;
                if (unsigned)
                    minValue = Integer.MIN_VALUE;
                for(int x = 0; x < width; x++) {
                    doubles[x][y] = (double) (DataTools.bytesToInt(
                        b, x * 4 + y * 4 * width, 4, little) - minValue);
                }
            }
            return doubles;
        }
        else if (bpp == 8 && fp) {
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x][y] = DataTools.bytesToDouble(
                        b, x * 8 + y * 8 * width, 8, little);
                }
            }
            return doubles;
        }
        else if (bpp == 8) {
            long minValue = 0;
            if (unsigned)
                minValue = Long.MIN_VALUE;
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x][y] = (double) (DataTools.bytesToLong(
                        b, x * 8 + y * 8 * width, 8, little) - minValue);
                }
            }
            return doubles;
        }
        return null;
    }

    public static double[] toDoubleVector(
            byte[] b, int bpp, boolean fp, boolean little, boolean unsigned,
            int width, int height)
    {
        log.debug("Converting to double array with bpp={}", bpp);
        double[] doubles = new double[width * height];
        if (bpp == 1) {
            byte minValue = 0;
            if (unsigned)
                minValue = Byte.MIN_VALUE;
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x + y * width] = (double) (
                        b[x + y * width] - minValue);
                }
            }
            return doubles;
        }
        else if (bpp == 2) {
            double minValue = 0;
            if (!unsigned)
                minValue = (double) (Short.MIN_VALUE);
            log.debug("Converting to double array with min={}", minValue);
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x + y * width] = (double) (DataTools.bytesToInt(
                        b, x * 2 + y * 2 * width, 2, little) - minValue);
                }
            }
            return doubles;
        }
        else if (bpp == 4 && fp) {
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x + y * width] = (double) DataTools.bytesToFloat(
                        b, x * 4 + y * 4 * width, 4, little);
                }
            }
            return doubles;
        }
        else if (bpp == 4) {
            for (int y = 0; y < height; y++) {
                int minValue = 0;
                if (unsigned)
                    minValue = Integer.MIN_VALUE;
                for(int x = 0; x < width; x++) {
                    doubles[x + y * width] = (double) (DataTools.bytesToInt(
                        b, x * 4 + y * 4 * width, 4, little) - minValue);
                }
            }
            return doubles;
        }
        else if (bpp == 8 && fp) {
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x + y * width] = DataTools.bytesToDouble(
                        b, x * 8 + y * 8 * width, 8, little);
                }
            }
            return doubles;
        }
        else if (bpp == 8) {
            long minValue = 0;
            if (unsigned)
                minValue = Long.MIN_VALUE;
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    doubles[x + y * width] = (double) (DataTools.bytesToLong(
                        b, x * 8 + y * 8 * width, 8, little) - minValue);
                }
            }
            return doubles;
        }
        return null;
    }

    private void readPlanes(int channel) throws Exception {
        if (this.readers.isEmpty()) {
            throw new Exception("No readers initialised.");
        }
        if (channel > this.sizeC) {
            throw new Exception("Requested channel index > sizeC");
        }
        this.S.clear();
        this.maxI = 0.0;
        for (ImageReader reader : this.readers)
        {
            log.info("Reading planes from {}", reader.getCurrentFile());
            this.loadPlanes(reader, channel);
        }
    }

    private void loadPlanes(ImageReader reader, int channel) throws Exception
    {
        boolean fp = false;
        boolean unsigned = false;
        if (reader.getPixelType() == loci.formats.FormatTools.FLOAT ||
            reader.getPixelType() == loci.formats.FormatTools.DOUBLE)
        {
            fp = true;
        }
        if (reader.getPixelType() == loci.formats.FormatTools.UINT8 ||
            reader.getPixelType() == loci.formats.FormatTools.UINT16 ||
            reader.getPixelType() == loci.formats.FormatTools.UINT32)
        {
            unsigned = true;
        }

        double min, max;
        double[][] minImage = new double[this.sizeX][this.sizeY];
        int planeCounter = 0;
        log.info("Image dimensions. S: {}, T: {}, Z: {}",
                 this.series.size(), this.timepoints.size(),
                 this.zSections.size());
        log.info(
            "Loading {} planes",
            this.series.size() * this.timepoints.size() *
            this.zSections.size());
        for (int s : this.series) {
            reader.setSeries(s);
            for (int z : this.zSections) {
                for (int t : this.timepoints) {
                   byte[] plane = reader.openBytes(
                       reader.getIndex(z, channel, t));
                   double[][] planeDouble = BfImageLoader.toDoubleArray(
                       plane, (int) (0.125 * reader.getBitsPerPixel()),
                       fp, reader.isLittleEndian(), unsigned,
                       this.sizeX, this.sizeY);
                   if (planeDouble == null) {
                       throw new Exception("We got no pixels.");
                   }

                   double[][] planeRescaled = CidrePreprocess.imresize(
                       planeDouble, this.sizeX, this.sizeY,
                       this.options.workingSize.width,
                       this.options.workingSize.height);
                   max = this.findMax(planeRescaled);
                   min = this.findMin(planeRescaled);
                   this.maxI = Math.max(this.maxI, max);
                   log.debug("Series {}, Min/Max: [{}, {}]", s, min, max);
                   this.S.add(planeRescaled);
                   if (planeCounter == 0) {
                       minImage = planeDouble.clone();
                   } else {
                       minImage = CidreMath.min(planeDouble, minImage);
                   }
                   planeCounter++;
                }
            }
        }
        max = CidreMath.max(minImage);
        min = CidreMath.min(minImage);
        log.debug(
            "Min Image stats: mean: {}, max: {}, min: {}",
            CidreMath.mean(minImage), max, min);
        this.minImage = minImage.clone();
    }

    private ImageReader getReaderByPlane(Integer planeIndex) {
        return this.readers.get(0);
    }

    @Override
    public int getNumberOfImages() {
        return this.options.numImagesProvided;
    }

    public float[][] toFloatArray(
            byte[] b, int bpp, boolean fp,
            boolean little, boolean unsigned,
            int width, int height)
    {
        log.debug("Converting to double array with bpp={}", bpp);
        float[][] floats = new float[width][height];
        if (bpp == 1) {
            byte minValue = 0;
            if (unsigned)
                minValue = Byte.MIN_VALUE;
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    floats[x][y] = (float) (b[x + y * width] - minValue);
                }
            }
            return floats;
        }
        else if (bpp == 2) {
            short minValue = 0;
            if (unsigned)
                minValue = Short.MIN_VALUE;
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    floats[x][y] = (float) (DataTools.bytesToShort(
                        b, x * 2 + y * 2 * width, 2, little) - minValue);
                }
            }
            return floats;
        }
        else if (bpp == 4 && fp) {
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    floats[x][y] = (float) DataTools.bytesToFloat(
                        b, x * 4 + y * 4 * width, 4, little);
                }
            }
            return floats;
        }
        else if (bpp == 4) {
            for (int y = 0; y < height; y++) {
                int minValue = 0;
                if (unsigned)
                    minValue = Integer.MIN_VALUE;
                for(int x = 0; x < width; x++) {
                    floats[x][y] = (float) (DataTools.bytesToInt(
                        b, x * 4 + y * 4 * width, 4, little) - minValue);
                }
            }
            return floats;
        }
        else if (bpp == 8 && fp) {
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    floats[x][y] = (float) DataTools.bytesToDouble(
                        b, x * 8 + y * 8 * width, 8, little);
                }
            }
            return floats;
        }
        else if (bpp == 8) {
            long minValue = 0;
            if (unsigned)
                minValue = Long.MIN_VALUE;
            for (int y = 0; y < height; y++) {
                for(int x = 0; x < width; x++) {
                    floats[x][y] = (float) (DataTools.bytesToLong(
                        b, x * 8 + y * 8 * width, 8, little) - minValue);
                }
            }
            return floats;
        }
        return null;
    }

    @Override
    public double[][] loadPlane(Integer planeIndex) throws Exception
    {
        int channel = 0;
        int timepoint = 0;
        int zPlane = 0;
        int series = 0;
        ImageReader reader = this.getReaderByPlane(planeIndex);
        reader.setSeries(series);
        boolean fp = false;
        boolean unsigned = false;
        if (reader.getPixelType() == loci.formats.FormatTools.FLOAT ||
            reader.getPixelType() == loci.formats.FormatTools.DOUBLE)
        {
            fp = true;
        }
        if (reader.getPixelType() == loci.formats.FormatTools.UINT8 ||
            reader.getPixelType() == loci.formats.FormatTools.UINT16 ||
            reader.getPixelType() == loci.formats.FormatTools.UINT32)
        {
            unsigned = true;
        }
        byte[] plane = reader.openBytes(
            reader.getIndex(zPlane, channel, timepoint));
        double[][] planeDouble = BfImageLoader.toDoubleArray(
                plane, (int) (0.125 * reader.getBitsPerPixel()),
                fp, reader.isLittleEndian(), unsigned,
                this.sizeX, this.sizeY);
        if (planeDouble == null) {
            throw new Exception("We got no pixels.");
        }
        return planeDouble;
    }

    @Override
    public double[][] loadPlane(
        int series, int channel, int timepoint, int zPlane) throws Exception
    {
        ImageReader reader = this.readers.get(0);
        reader.setSeries(series);
        boolean fp = false;
        boolean unsigned = false;
        if (reader.getPixelType() == FormatTools.FLOAT ||
            reader.getPixelType() == FormatTools.DOUBLE)
        {
            fp = true;
        }
        if (reader.getPixelType() == FormatTools.UINT8 ||
            reader.getPixelType() == FormatTools.UINT16 ||
            reader.getPixelType() == FormatTools.UINT32)
        {
            unsigned = true;
        }
        byte[] plane = reader.openBytes(
            reader.getIndex(zPlane, channel, timepoint));
        double[][] planeDouble = BfImageLoader.toDoubleArray(
                plane, (int) (0.125 * reader.getBitsPerPixel()),
                fp, reader.isLittleEndian(), unsigned,
                this.sizeX, this.sizeY);
        if (planeDouble == null) {
            throw new Exception("We got no pixels.");
        }
        return planeDouble;
    }

    @Override
    public int getWidth() {
        return this.sizeX;
    }

    @Override
    public int getHeight() {
        return this.sizeY;
    }

    public int getSizeS() {
        return this.sizeS;
    }

    public int getSizeC() {
        return this.sizeC;
    }
}
