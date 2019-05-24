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

package com.cidre;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cidre.core.Cidre;
import com.cidre.core.Options;
import com.cidre.core.Options.CorrectionMode;

import ch.qos.logback.classic.Level;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.annotation.Arg;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class Main {

    @Arg
    private ArrayList<String> input;

    @Arg
    private String modelFile;

    @Arg
    private String modelOutput;

    @Arg
    private String output;

    @Arg
    private ArrayList<Integer> channels;

    @Arg
    private Boolean skipPreprocessing;

    @Arg
    private Boolean useMinImage;

    @Arg
    private Boolean planePerFile;

    @Arg
    private Boolean debug;

    @Arg
    private Boolean overwrite;

    @Arg
    private CorrectionMode illuminationCorrectionMode;

    private static final Logger log =
        LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Throwable {
        log.info("CIDRE started");

        ArgumentParser parser =
            ArgumentParsers.newArgumentParser("cidre");
        parser.description("Passing Folder or ");
        parser.addArgument("--input")
              .nargs("*")
              .required(true)
              .help("Directory path, file path, list of file paths or "
                    + "file path masks are valid inputs.\n"
                    + "One model file per input file will be created unless "
                    + "`--planePerFile` flag is specified.");
        parser.addArgument("--model")
              .nargs("*")
              .help("Select a file with the model to apply.");
        parser.addArgument("--modelOutput")
              .help("Output directory where a multi-channel model file "
                    + "per input file / set of files will be created.");
        parser.addArgument("--output")
              .help("Output directory for corrected images.  Will also "
                    + "enable image correction mode.");
        parser.addArgument("--channels").nargs("*").type(Integer.class)
              .help("Channel indexes (from 0) to calculate the illumination "
                    + "correction for (default: all).");
        parser.addArgument("--illuminationCorrectionMode")
              .choices(Options.CorrectionMode.values())
              .setDefault(Options.CorrectionMode.ZERO_LIGHT_PRESERVED)
              .help("IlluminationCorrection mode if `--output` is specified. "
                    + "'Zero-light preserved' "
                    + "retains the original intensity range and zero-light "
                    + "level of the original images.  'Dynamic range "
                    + "corrected' retains the intensity range of the original "
                    + "images.  'Direct' subtracts the zero-light term and "
                    + "divides the illumination gain. (default: "
                    + "ZERO_LIGHT_PRESERVED)");
        parser.addArgument("--planePerFile")
              .action(Arguments.storeTrue())
              .help("Use this option if the planes are stored one per file"
                    + " rather then all in a single file.");
        parser.addArgument("--useMinImage")
              .action(Arguments.storeTrue())
              .help("Use min(stack) image as starting point for illumination "
                    + "correction.");
        parser.addArgument("--skipPreprocessing")
              .action(Arguments.storeTrue())
              .help("Skip data reduction by resizing stack if total "
                    + "stack size > 200");
        parser.addArgument("--debug")
              .action(Arguments.storeTrue())
              .help("Set logging level to DEBUG");
        parser.addArgument("--overwrite")
              .action(Arguments.storeTrue())
              .help("Overwrite output file(s) if exist");

        Main main = new Main();
        try {
            parser.parseArgs(args, main);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
        main.correctImages();
    }

    protected class ImageNameFilter implements FilenameFilter {

        private Pattern pattern;

        public ImageNameFilter(String expression) {
            String correctedExpression = ".*";
            if (expression != null && expression != "") {
                correctedExpression = expression.replace(".", "\\.");
                correctedExpression = correctedExpression.replace("*", ".*");
            }
            pattern = Pattern.compile(
                correctedExpression, Pattern.CASE_INSENSITIVE);
        }

        @Override
        public boolean accept(File directory, String fileName) {
            return pattern.matcher(new File(fileName).getName()).matches();
        }
    }

    public void printOptions(Options options) {
        log.info(
            "CidreOptions:\n\tlambdaVreg: {}\n\tlambdaZero: {}" +
            "\n\tmaxLbgfsIterations: {}\n\tqPercent: {}\n\tzLimits:{}, {}" +
            "\n\timageSize: {}\n\tnumImagesProvided: {}\n\tbitDepth: {}" +
            "\n\tcorrectionMode: {}\n\ttargetNumPixels: {}" +
            "\n\tworkingSize: {}\n\tnumberOfQuantiles: {}",
            options.lambdaVreg, options.lambdaZero, options.maxLbgfsIterations,
            options.qPercent, options.zLimits[0], options.zLimits[1],
            options.imageSize, options.numImagesProvided, options.bitDepth,
            options.correctionMode, options.targetNumPixels,
            options.workingSize, options.numberOfQuantiles);
    }

    public void correctImages() throws Exception {
        // Setup logger
        ch.qos.logback.classic.Logger root =
            (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(
                Logger.ROOT_LOGGER_NAME);
        if (this.debug) {
            root.setLevel(Level.DEBUG);
        } else {
            root.setLevel(Level.INFO);
        }

        if (this.planePerFile && this.input.size() == 1)
        {
            Cidre cidre = new Cidre(
                this.input.get(0), this.output,
                this.modelFile, this.modelOutput,
                this.useMinImage, this.skipPreprocessing,
                this.illuminationCorrectionMode);
            if (this.channels != null && this.channels.size() > 0) {
                cidre.setChannelsToProcess(this.channels);
            }
            cidre.execute();
        } else if (!this.planePerFile) {
            for (String fileName : this.input) {
                Cidre cidre = new Cidre(
                    fileName, this.output,
                    this.modelFile, this.modelOutput,
                    this.useMinImage, this.skipPreprocessing,
                    this.illuminationCorrectionMode);
                if (this.channels != null && this.channels.size() > 0) {
                    cidre.setChannelsToProcess(this.channels);
                }
                cidre.execute();
            }
        } else if (this.planePerFile && this.input.size() > 1) {
            throw new Exception(
                "For `planePerFile` option single input Directory or "
                + " a file name mask expected. Use wildcard cahracter `*`"
                + " to specify multiple input files.");
        }
        log.info("Done");
    };
}
