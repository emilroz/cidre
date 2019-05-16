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

import java.io.IOException;
import java.nio.ByteBuffer;

import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.out.TiffWriter;
import loci.formats.services.OMEXMLService;

public class BfImageWriter {

    private TiffWriter writer;

    private String fileName;

    private int width;

    private int height;

    private int sizeC = 1;

    private int sizeZ = 1;

    private int sizeT = 1;

    private String pixelType;

    private boolean littleEndian = false;

    private int samplesPerPixel = 1;

    public BfImageWriter(
            String fileName, int width, int height, String pixelType)
    {
        this.fileName = fileName;
        this.width = width;
        this.height = height;
        this.pixelType = pixelType;
    }

    public BfImageWriter(
            String fileName, boolean littleEndian,
            int width, int height,
            int sizeZ, int sizeC, int sizeT,
            String pixelType, int samplesPerPixel)
    {
        this.fileName = fileName;
        this.width = width;
        this.height = height;
        this.pixelType = pixelType;
        this.littleEndian = littleEndian;
        this.sizeC = sizeC;
        this.sizeT = sizeT;
        this.sizeZ = sizeZ;
        this.samplesPerPixel = samplesPerPixel;
    }

    public void initialise()
            throws DependencyException, ServiceException,
                   FormatException, IOException
    {
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata meta = service.createOMEXMLMetadata();
        MetadataTools.populateMetadata(
            meta, 0, null, this.littleEndian, "XYZCT",
            this.pixelType, this.width, this.height,
            this.sizeZ, this.sizeC, this.sizeT, this.samplesPerPixel);
        this.writer = new TiffWriter();
        this.writer.setMetadataRetrieve(meta);
        this.writer.setId(this.fileName);
    }

    public void write(byte[] buffer, int imageNumber)
            throws FormatException, IOException
    {
        this.writer.saveBytes(imageNumber, buffer);
    }

    public void write(double[] b, int imageNumber)
            throws FormatException, IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(8 * this.width * this.height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.putDouble(b[x * height + y]);
            }
        }
        this.writer.saveBytes(imageNumber, buffer.array());
    }

    public void write(double[][] b, int imageNumber)
            throws FormatException, IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(8 * this.width * this.height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.putDouble(b[x][y]);
            }
        }
        this.writer.saveBytes(imageNumber, buffer.array());
    }

    public void close() throws IOException
    {
        this.writer.close();
    }

}
