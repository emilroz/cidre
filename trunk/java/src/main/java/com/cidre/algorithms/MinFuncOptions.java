    
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

package com.cidre.algorithms;

public class MinFuncOptions {

    /**
     *  max iterations for optimization
     */
    public int maxIter;

    /**
     *  max evaluations of objective function
     */
    public int MaxFunEvals;

    /**
     *  progress tolerance
     */
    public double progTol;

    /**
     *  optimality tolerance
     */
    public double optTol;

    /**
     *  number of corrections to store in memory
     */
    public int Corr;
}
