/*******************************************************************************
 * Copyright (c) 2013, 2014 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Initial API and implementation and/or initial documentation -
 *   Jay Jay Billings
 *******************************************************************************/
package org.eclipse.ice.reflectivity;

import org.apache.commons.math.MathException;
import org.apache.commons.math.complex.Complex;
import org.apache.commons.math.special.Erf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class performs all of the operations necessary to calculate the
 * reflectivity of a stack of materials. It follows the code originally
 * developed by John Ankner at Oak Ridge National Laboratory that uses the
 * method described in Parratt, Phys. Rev. 95, 359(1954). It has been corrected
 * to incorporate incoherent and true absorption.
 *
 * @author Jay Jay Billings, John Ankner
 *
 */
public class ReflectivityCalculator {

	/**
	 * Logger for handling event messages and other information.
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(ReflectivityCalculator.class);

	/**
	 * The maximum number of points used by the convolution routine.
	 */
	public static final int maxPoints = 2000;

	/**
	 * The maximum number of layers of roughness that can be created when
	 * generating the interfacial profile.
	 */
	public static final int maxRoughSize = 101;

	/**
	 * A factor used in computing tile updates
	 */
	private static final double cE = 1.665;

	/**
	 * This operation returns the value of the squared modulus of the specular
	 * reflectivity for a single wave vector Q.
	 *
	 * @param waveVectorQ
	 *            the value of the wave vector
	 * @param wavelength
	 *            the wavelength of the incident neutrons
	 * @param tiles
	 *            the list of Tiles that contains the physical parameters needed
	 *            for the calculation, including the scattering densities,
	 *            absorption parameters and thicknesses.
	 * @return the squared modulus of the specular reflectivity
	 */
	public double getModSqrdSpecRef(double waveVectorQ, double wavelength,
			Tile[] tiles) {

		double modSqrdSpecRef = 0.0;

		if (wavelength > 0.0) {
			// Variables only needed if we are going to do the work, i.e. -
			// wavelength > 0.0.
			Tile tile;
			Complex aNm1Sq, fNm1N, rNm1N = new Complex(0.0, 0.0), one = new Complex(
					1.0, 0.0), qN = new Complex(0.0, 0.0), rNNp1 = new Complex(
					0.0, 0.0);
			// Get the bottom tile
			int nLayers = tiles.length;
			tile = tiles[nLayers - 1];
			// Starting point--no reflected beam in bottom-most (bulk) layer
			double qCSq = 16.0 * Math.PI * tile.scatteringLength;
			double muLAbs = tile.trueAbsLength;
			double mulInc = tile.incAbsLength;
			double thickness = tile.thickness;
			// Setup other values for the problem
			double betaNm1 = 4.0 * Math.PI * (muLAbs + mulInc / wavelength);
			Complex qNm1 = new Complex(waveVectorQ * waveVectorQ - qCSq, -2.0
					* betaNm1);
			qNm1 = qNm1.sqrt();
			// Loop through to calculate recursion formula described in Parratt.
			// Start at the bottom and work up.
			for (int i = nLayers - 1; i > 0; i--) {
				// Get the tile above tile[i] (started at the bottom
				tile = tiles[i - 1];
				// Calculate the normal component of Q for layer and layer-1
				qN = qNm1;
				qCSq = 16.0 * Math.PI * tile.scatteringLength;
				muLAbs = tile.trueAbsLength;
				mulInc = tile.incAbsLength;
				thickness = tile.thickness;
				betaNm1 = 4.0 * Math.PI * (muLAbs + mulInc / wavelength);
				qNm1 = new Complex(waveVectorQ * waveVectorQ - qCSq, -2.0
						* betaNm1);
				qNm1 = qNm1.sqrt();
				// Calculate phase factor, e^(-0.5*d*qNm1)
				aNm1Sq = (new Complex(qNm1.getImaginary(), qNm1.getReal())
						.multiply(-0.5 * thickness)).exp();
				// CDiv(qNm1-qN,qNm1+qN)
				fNm1N = qNm1.subtract(qN).divide(qNm1.add(qN));
				// Calculate the reflectivity amplitude.
				// CMult(aNm1Sq, CMult(aNm1Sq, CDiv(CAdd(rNNp1, fNm1N),
				// CAdd(CMult(rNNp1, fNm1N), CReal(1)))))
				Complex y = rNNp1.multiply(fNm1N).add(one);
				Complex z = rNNp1.add(fNm1N);
				rNm1N = aNm1Sq.multiply(aNm1Sq).multiply(z.divide((y)));
				// Carry over to the next iteration
				rNNp1 = rNm1N;
			}
			modSqrdSpecRef = rNm1N.getReal() * rNm1N.getReal()
					+ rNm1N.getImaginary() * rNm1N.getImaginary();
		}

		return modSqrdSpecRef;
	}

	/**
	 * This operation convolutes the data in refFit with a Gaussian resolution
	 * function in q, calculated from theta, delThe, and delLamOLam.
	 *
	 * @param waveVector
	 *            the wave vector (Q) plus additional space for the convolution.
	 *            This array should have length = numPoints + numLowPoints.
	 * @param deltaQ0
	 *            the zeroth order term of a Taylor expansion of the
	 *            reflectometer resolution function dQ = dQ_0 + (dQ/Q)_1 x Q +
	 *            ...
	 * @param deltaQ1ByQ
	 *            the zeroth order term of the Q resolution Taylor expansion
	 * @param wavelength
	 *            the wavelength of the incident neutrons
	 * @param numPoints
	 *            the number of points in the wave vector
	 * @param numLowPoints
	 *            the number of points in the low-Q extension to q used for
	 *            convolution of the data with the resolution function. Returned
	 *            by ExtResFixedLambda.
	 * @param numHighPoints
	 *            the number of points in the high-Q extension to q used for
	 *            convolution of the data with the resolution function. Returned
	 *            by ExtResFixedLambda.
	 * @param refFit
	 *            OUTPUT - the specular reflectivity values for each Q in q
	 *            convoluted with instrumental resolution.
	 */
	public void convolute(double[] waveVector, double deltaQ0,
			double deltaQ1ByQ, double wavelength, int numPoints,
			int numLowPoints, int numHighPoints, double[] refFit) {

		double ln2 = Math.log(2.0);
		double qEff = 0.0, qRes = 0.0, rExp = 0.0, rNorm = 0.0;
		double[] refTemp = new double[maxPoints];
		int nStep = 0;
		boolean lFinish = false, hFinish = false;

		// Perform convolution over nPnts between nLow and nHigh extensions
		for (int i = numLowPoints; i <= numLowPoints + numPoints - 1; i++) {
			// Calculate resolution width and initialize resolution loop
			if (waveVector[i] < 1.0e-10) {
				qEff = 1.0e-10;
			} else {
				qEff = waveVector[i];
			}
			double qDel = deltaQ0 + qEff * deltaQ1ByQ;
			double twSgSq = 2.0 * qDel * qDel / (8.0 * ln2);
			if (twSgSq < 1.0e-10) {
				twSgSq = 1.0e-10;
			}
			rNorm = 1.0;
			refTemp[i - numLowPoints] = refFit[i];
			nStep = 1;
			// Check if exponent term becomes < 0.001 and loop until it does so
			lFinish = false;
			hFinish = false;
			while (!lFinish && !hFinish) {
				// Evaluate the low-q side
				if (lFinish) {
					qRes = 1.0e20;
				} else {
					qRes = waveVector[i - nStep] - waveVector[i];
				}
				if (qRes * qRes / twSgSq < 6.908) {
					// Continue evaluating convolution
					rExp = Math.exp(-qRes * qRes / twSgSq);
					rNorm = rNorm + rExp;
					refTemp[i - numLowPoints] = refTemp[i - numLowPoints]
							+ rExp * refFit[i - nStep];
				} else {
					lFinish = true;
				}
				// Evaluate high-q side
				if (hFinish) {
					qRes = 1.0e20;
				} else {
					qRes = waveVector[i + nStep] - waveVector[i];
				}
				if (qRes * qRes / twSgSq < 6.908) {
					// Continue evaluating convolution
					rExp = Math.exp(-qRes * qRes / twSgSq);
					rNorm = rNorm + rExp;
					refTemp[i - numLowPoints] = refTemp[i - numLowPoints]
							+ rExp * refFit[i + nStep];
				} else {
					hFinish = true;
				}
				nStep++;
			}
			// Normalize convoluted value to integrated intensity of resolution
			// function
			refTemp[i - numLowPoints] = refTemp[i - numLowPoints] / rNorm;
		}
		// Transfer convoluted values from refTemp to refFit
		for (int i = 0; i < numPoints; i++) {
			refFit[i] = refTemp[i];
		}

		return;
	}

	/**
	 * This operation calculates the length of the low-Q extension of the data
	 * to be convoluted with the delt-Q full-width half-maximum Gaussian
	 * resolution function.
	 *
	 * @param waveVector
	 *            the wave vector (Q) plus additional space for the convolution.
	 *            This array should have length = numPoints + numLowPoints.
	 * @param deltaQ0
	 *            the zeroth order term of a Taylor expansion of the
	 *            reflectometer resolution function dQ = dQ_0 + (dQ/Q)_1 x Q +
	 *            ...
	 * @param deltaQ1ByQ
	 *            the zeroth order term of the Q resolution Taylor expansion
	 * @param numPoints
	 *            the number of points in the wave vector
	 * @return numLowPoints the number of points in the low-Q extension to q
	 *         used for convolution of the data with the resolution function.
	 *         Returned by ExtResFixedLambda.
	 */
	public int getLowExtensionLength(double[] waveVector, double deltaQ0,
			double deltaQ1ByQ, int numPoints) {

		double ln2 = Math.log(2.0);

		// Determine the loq-Q extension
		double qDel = deltaQ0 + waveVector[0] * deltaQ1ByQ;
		double qStep = waveVector[1] - waveVector[0];
		double twSgSq = Math.max(2.0 * qDel * qDel / (8.0 * ln2), 1.0e-10);
		int numLowPoints = 0;
		double qR = 0.0;
		while (qR * qR / twSgSq <= 6.908) {
			numLowPoints++;
			qR = qR + qStep;
		}

		return numLowPoints;
	}

	/**
	 * This operation calculates the length of the high-Q extension of the data
	 * to be convoluted with the delt-Q full-width half-maximum Gaussian
	 * resolution function.
	 *
	 * @param waveVector
	 *            the wave vector (Q) plus additional space for the convolution.
	 *            This array should have length = numPoints + numLowPoints.
	 * @param deltaQ0
	 *            the zeroth order term of a Taylor expansion of the
	 *            reflectometer resolution function dQ = dQ_0 + (dQ/Q)_1 x Q +
	 *            ...
	 * @param deltaQ1ByQ
	 *            the zeroth order term of the Q resolution Taylor expansion
	 * @param numPoints
	 *            the number of points in the wave vector
	 * @return numHighPoints the number of points in the high-Q extension to q
	 *         used for convolution of the data with the resolution function.
	 *         Returned by ExtResFixedLambda.
	 */
	public int getHighExtensionLength(double[] waveVector, double deltaQ0,
			double deltaQ1ByQ, int numPoints) {

		double ln2 = Math.log(2.0);

		// Determine the high-Q extension
		double qDel = deltaQ0 + waveVector[numPoints - 1] * deltaQ1ByQ;
		double qStep = waveVector[numPoints - 1] - waveVector[numPoints - 2];
		double twSgSq = 2.0 * qDel * qDel / (8.0 * ln2);
		int numHighPoints = 0;
		double qR = 0.0;
		while (qR * qR / twSgSq <= 6.908) {
			numHighPoints++;
			qR = qR + qStep;
		}

		return numHighPoints;
	}

	/**
	 * This operation generates the interfacial profile using an error function
	 * of numRough ordinate steps based on those of the hyperbolic tangent.
	 *
	 * @param numRough
	 *            the number of ordinate steps
	 * @param zInt
	 *            FIXME! This array must be preallocated with a size n =
	 *            maxRoughSize.
	 * @param rufInt
	 *            FIXME! This array must be preallocated with a size n =
	 *            maxRoughSize.
	 * @throws MathException
	 *             Thrown if the error function cannot be calculated
	 */
	public void getInterfacialProfile(int numRough, double[] zInt,
			double[] rufInt) throws MathException {

		// cE ensures Gaussian = 0.5 when z = zhwhm
		final double cE = 1.665;
		double dist = 0.0, step = 0.0, oHalfstep = 0.0, zTemp = 0.0;
		int j;

		// Check nRough to make sure it is legitimate
		if (numRough < 1) {
			numRough = 1;
		}
		// Set the step size
		step = 2.0 / (numRough + 1);

		// Evaluate the lower half of the interface
		dist = -step / 2.0;
		// Steps calculated from inverse tanh. Note that all of the indices are
		// shifted by -1 from the VB code because this version is zero indexed!
		zInt[numRough / 2] = Math.log((1.0 + dist) / (1.0 - dist)) / (2.0 * cE);
		rufInt[numRough / 2] = Erf.erf(cE * zInt[numRough / 2]);
		for (j = numRough / 2 - 1; j >= 0; j--) {
			dist = dist - step;
			zInt[j] = Math.log((1.0 + dist) / (1.0 - dist)) / (2.0 * cE);
			rufInt[j] = Erf.erf(cE * zInt[j]);
		}

		// Evaluate the upper half of the interface
		dist = step / 2.0;
		// Steps calculated from inverse tanh. Note that all of the indices are
		// shifted by -1 from the VB code because this version is zero indexed!
		zInt[numRough / 2 + 1] = Math.log((1.0 + dist) / (1.0 - dist))
				/ (2.0 * cE);
		rufInt[numRough / 2 + 1] = Erf.erf(cE * zInt[numRough / 2 + 1]);
		for (j = numRough / 2 + 2; j <= numRough; j++) {
			dist = dist + step;
			zInt[j] = Math.log((1.0 + dist) / (1.0 - dist)) / (2.0 * cE);
			rufInt[j] = Erf.erf(cE * zInt[j]);
		}

		// Calculate step widths
		oHalfstep = 0.5 * (zInt[1] - zInt[0]);
		for (j = 0; j <= numRough / 2; j++) {
			zTemp = zInt[j];
			// The zInt values are symmetric, so this loop only computes the
			// bottom half and sets the upper half without doing the
			// calculation.
			zInt[j] = oHalfstep + 0.5 * (zInt[j + 1] - zInt[j]);
			zInt[numRough - j] = zInt[j];
			oHalfstep = 0.5 * (zInt[j + 1] - zTemp);
		}

		return;
	}

	/**
	 * This operation generates a list of Tiles from the slabs with the
	 * corresponding number of ordinate steps.
	 *
	 * @param slabs
	 *            the slabs of materials that define the system
	 * @param numRough
	 *            the number of ordinate steps
	 * @param zInt
	 *            FIXME! This array must be preallocated with a size n =
	 *            maxRoughSize.
	 * @param rufInt
	 *            FIXME! This array must be preallocated with a size n =
	 *            maxRoughSize.
	 * @return the system of generated tiles
	 * @throws MathException
	 *             Thrown if the error function cannot be calculated
	 */
	public Tile[] generateTiles(Slab[] slabs, int numRough, double[] zInt,
			double[] rufInt) throws MathException {

		// Local Declarations
		int nGlay = 0;
		double totalThickness = 0.0, gDMid = 0.0, step = 0.0, dist = 0.0;
		// The number of slabs was not defined in the original code. I computed
		// it by counting up the loops. This formula is currently off a little
		// bit.
		int numSlabs = 2 + 2 * (numRough / 2 + 1) + (slabs.length - 2)
				* (2 + numRough);
		Tile[] generatedSlabs = new Tile[numSlabs];
		// Create the slabs
		for (int i = 0; i < numSlabs; i++) {
			generatedSlabs[i] = new Slab();
		}

		// Evaluate the first half of the vacuum interface. Create the first
		// slab.
		Tile tmpSlab = generatedSlabs[0];
		Slab refSlab = slabs[0], secondRefSlab = slabs[1], thirdRefSlab;
		tmpSlab.scatteringLength = refSlab.scatteringLength;
		tmpSlab.trueAbsLength = refSlab.trueAbsLength;
		tmpSlab.incAbsLength = refSlab.incAbsLength;
		tmpSlab.thickness = refSlab.thickness;
		++nGlay;
		// Create the other slabs for this half
		for (int i = 0; i < numRough / 2 + 1; i++) {
			tmpSlab = generatedSlabs[nGlay + i];
			updateTileByInterface(tmpSlab, refSlab, secondRefSlab, zInt[i],
					rufInt[i]);
		}
		nGlay += numRough / 2 + 1;

		// Evaluate the total normalized thickness of the surface
		for (int i = 0; i < numRough + 1; i++) {
			totalThickness += zInt[i];
		}

		// Calculate gradation of layers. Using slabs.length - 1 because the
		// last layer is at index 4.
		for (int i = 1; i < slabs.length - 1; i++) {
			refSlab = slabs[i];
			secondRefSlab = slabs[i + 1];
			thirdRefSlab = slabs[i - 1];
			// FIXME! Review gDMid calculation with John because it can be
			// negative.
			gDMid = refSlab.thickness - 0.5 * totalThickness
					* (refSlab.interfaceWidth + secondRefSlab.interfaceWidth);
			if (gDMid <= 1.0e-10) {
				// The interfaces are overlapping. Step through the entire slab
				step = refSlab.thickness / (numRough + 1);
				// Take the first half step
				tmpSlab = generatedSlabs[nGlay];
				tmpSlab.thickness = step / 2.0;
				dist = step / 4.0;
				updateTileByLayer(tmpSlab, thirdRefSlab, refSlab,
						secondRefSlab, dist);
				++nGlay;
				dist += 0.75 * step;
				// Take the remaining steps
				for (int j = 0; j < numRough; j++) {
					tmpSlab = generatedSlabs[nGlay + j];
					tmpSlab.thickness = step;
					updateTileByLayer(tmpSlab, thirdRefSlab, refSlab,
							secondRefSlab, dist);
					dist += step;
				}
				nGlay += numRough;
				// Take final half step
				tmpSlab = generatedSlabs[nGlay];
				tmpSlab.thickness = step / 2.0;
				dist = refSlab.thickness - step / 4.0;
				updateTileByLayer(tmpSlab, thirdRefSlab, refSlab,
						secondRefSlab, dist);
				++nGlay;
			} else {
				// Evaluate contributions from interfaces separately.
				// Top interface
				for (int j = numRough / 2 + 1; j < numRough + 1; j++) {
					// Get the next slab and update it by considering the
					// interface contribution.
					tmpSlab = generatedSlabs[nGlay + j - numRough / 2 - 1];
					updateTileByInterface(tmpSlab, thirdRefSlab, refSlab,
							zInt[j], rufInt[j]);
				}
				nGlay += numRough / 2 + 1;
				// Central, bulk-like portion
				tmpSlab = generatedSlabs[nGlay];
				tmpSlab.scatteringLength = refSlab.scatteringLength;
				tmpSlab.thickness = gDMid;
				tmpSlab.trueAbsLength = refSlab.trueAbsLength;
				tmpSlab.incAbsLength = refSlab.incAbsLength;
				++nGlay;
				// Bottom interface
				for (int j = 0; j < numRough / 2 + 1; j++) {
					tmpSlab = generatedSlabs[nGlay + j];
					updateTileByInterface(tmpSlab, refSlab, secondRefSlab,
							zInt[j], rufInt[j]);
				}
				nGlay += numRough / 2 + 1;
			}
		}

		// Evaluate substrate gradation
		refSlab = slabs[slabs.length - 1];
		secondRefSlab = slabs[slabs.length - 2];
		for (int i = numRough / 2 + 1; i < numRough + 1; i++) {
			tmpSlab = generatedSlabs[nGlay + i - numRough / 2 - 1];
			updateTileByInterface(tmpSlab, secondRefSlab, refSlab, zInt[i],
					rufInt[i]);
		}
		nGlay += numRough / 2 + 1;
		// Handle the last layer
		refSlab = slabs[slabs.length - 1];
		tmpSlab = generatedSlabs[nGlay];
		tmpSlab.scatteringLength = refSlab.scatteringLength;
		tmpSlab.trueAbsLength = refSlab.trueAbsLength;
		tmpSlab.incAbsLength = refSlab.incAbsLength;
		tmpSlab.thickness = refSlab.thickness;
		++nGlay;

		return generatedSlabs;
	}

	/**
	 * This operation performs the tile updates needed by the generateTile()
	 * operation where interfaces should be considered.
	 *
	 * @param tile
	 *            the tile to update
	 * @param slabM1
	 *            the slab one index less than (so above) the middle slab
	 * @param slab
	 *            the middle slab
	 * @param dist
	 *            the step distance
	 */
	private void updateTileByInterface(Tile tile, Slab slabM1, Slab slab,
			double zInt, double rufInt) {

		// Update the tile
		tile.thickness = zInt * slab.interfaceWidth;
		tile.scatteringLength = 0.5 * (slab.scatteringLength
				+ slabM1.scatteringLength + rufInt
				* (slab.scatteringLength - slabM1.scatteringLength));
		tile.trueAbsLength = 0.5 * (slab.trueAbsLength + slabM1.trueAbsLength + rufInt
				* (slab.trueAbsLength - slabM1.trueAbsLength));
		tile.incAbsLength = 0.5 * (slab.incAbsLength + slabM1.incAbsLength + rufInt
				* (slab.incAbsLength - slabM1.incAbsLength));

		return;
	}

	/**
	 * This operation performs the tile updates needed by the generateTile()
	 * operation.
	 *
	 * @param tile
	 *            the tile to update
	 * @param slabM1
	 *            the slab one index less than (so above) the middle slab
	 * @param slab
	 *            the middle slab
	 * @param slabP1
	 *            the slab one index greater than (so below) the middle slab
	 * @param dist
	 *            the step distance
	 * @throws MathException
	 *             Thrown if the error function cannot be evaluated
	 */
	private void updateTileByLayer(Tile updateSlab, Slab slabM1, Slab slab,
			Slab slabP1, double dist) throws MathException {

		// Compute the exponentials
		double tExpFac = Erf.erf(cE * dist / slab.interfaceWidth);
		double bExpFac = Erf.erf(cE * (dist - slab.thickness)
				/ (slabP1.interfaceWidth));

		// Update the slab properties
		updateSlab.scatteringLength = getTileValue(slabM1.scatteringLength,
				slab.scatteringLength, slabP1.scatteringLength, tExpFac,
				bExpFac);
		updateSlab.trueAbsLength = getTileValue(slabM1.trueAbsLength,
				slab.trueAbsLength, slabP1.trueAbsLength, tExpFac, bExpFac);
		updateSlab.incAbsLength = getTileValue(slabM1.incAbsLength,
				slab.incAbsLength, slabP1.incAbsLength, tExpFac, bExpFac);

		return;
	}

	/**
	 * This is a convenience operation that performs a lengthy, complicated
	 * update operation. In the original code this formula was used for several
	 * quantities and caused significant bloat.
	 *
	 * @param xm1
	 *            the value at one index less than the midpoint
	 * @param x
	 *            the midpoint
	 * @param xp1
	 *            the value at one index greater than the midpoint
	 * @param tao
	 *            the factor for scaling x-xm1
	 * @param beta
	 *            the factor for scaling xp1-x
	 * @return the updated value
	 */
	private double getTileValue(double xm1, double x, double xp1, double tao,
			double beta) {
		return 0.5 * (xm1 + x + tao * (x - xm1) + x + xp1 + beta * (xp1 - x))
				- x;
	}

	/**
	 * This operation computes the convolution of the reflectivity with a
	 * variable Gaussian resolution function.
	 *
	 * @param deltaQ0
	 *            - FIXME!
	 * @param deltaQ1ByQ
	 *            - FIXME!
	 * @param wavelength
	 *            - FIXME!
	 * @param getRQ4
	 *            - FIXME! True if the routine should compute rq^4, false
	 *            otherwise.
	 * @param waveVector
	 *            The wave vector - FIXME!
	 * @param tiles
	 *            The tiles that define the layered structure of the materials.
	 * @return the reflectivity
	 */
	public double[] convoluteReflectivity(double deltaQ0, double deltaQ1ByQ,
			double wavelength, boolean getRQ4, double[] waveVector, Tile[] tiles) {

		// Local Declarations
		double qEff = 0.0;
		int numPoints = waveVector.length, numLowPoints = 0, numHighPoints = 0;
		double[] reflectivity = new double[numPoints];

		// Determine the length of the high- and low-Q extensions
		numLowPoints = getLowExtensionLength(waveVector, deltaQ0, deltaQ1ByQ,
				numPoints);
		numHighPoints = getHighExtensionLength(waveVector, deltaQ0, deltaQ1ByQ,
				numPoints);

		// Extend the wave vector in a temporary array
		double[] tempWaveVector = new double[numLowPoints + numHighPoints
				+ numPoints];
		double waveVecStep = waveVector[1] - waveVector[0];
		for (int i = 0; i < numLowPoints; i++) {
			tempWaveVector[i] = waveVector[0] - waveVecStep
					* ((double) numLowPoints + 1 - i);
		}
		for (int i = 0; i < numPoints; i++) {
			tempWaveVector[numLowPoints + i] = waveVector[i];
		}
		waveVecStep = waveVector[numPoints - 1] - waveVector[numPoints - 2];
		for (int i = 0; i < numHighPoints; i++) {
			tempWaveVector[i + numLowPoints + numPoints] = waveVector[numPoints - 1]
					+ waveVecStep * (i);
		}

		// Create a temporary array to hold the extended reflectivity.
		double[] tempReflectivity = new double[numPoints + numLowPoints
				+ numHighPoints];
		// Generate reflectivity values for convolution.
		// Calculate perfect-resolution reflectivity on extended wave vector
		for (int i = 0; i < numPoints + numLowPoints + numHighPoints; i++) {
			if (tempWaveVector[i] < 1.0e-10) {
				qEff = 1.0e-10;
			} else {
				qEff = tempWaveVector[i];
			}
			tempReflectivity[i] = getModSqrdSpecRef(qEff, wavelength, tiles);
		}

		// Convolve with instrumental resolution
		convolute(tempWaveVector, deltaQ0, deltaQ1ByQ, wavelength, numPoints,
				numLowPoints, numHighPoints, tempReflectivity);

		// Transfer the results to the reflectivity array.
		for (int i = 0; i < numPoints; i++) {
			reflectivity[i] = tempReflectivity[i];
		}
		// Calculate RQ^4 if needed. FIXME! This is not covered by the current
		// tests and should actually be a separate function!
		if (getRQ4) {
			for (int i = 0; i < numPoints; i++) {
				reflectivity[i] = Math.pow(waveVector[i], 4.0)
						* reflectivity[i];
			}
		}

		return reflectivity;
	}

	/**
	 * This operation computes the neutron scattering density profile for a set
	 * of tiles.
	 *
	 * @param tiles
	 *            the set of tiles that define the material
	 * @return The neutron scattering density profile.
	 */
	public ScatteringDensityProfile getScatteringDensityProfile(Tile[] tiles) {
		// Create an empty profile
		ScatteringDensityProfile profile = new ScatteringDensityProfile();
		profile.depth = new double[2 * tiles.length];
		profile.scatteringDensity = new double[2 * tiles.length];

		// Put down the first tile as two steps. Step one
		profile.depth[0] = -10.0;
		// Note that I am doing 2.0*n because that layer is the same as the
		// first slab. The original code ready n[slab] + n[tile], but they are
		// always the same.
		profile.scatteringDensity[0] = 2.0 * tiles[0].scatteringLength;
		double sumD = tiles[0].thickness;
		// Step two
		profile.depth[1] = sumD;
		profile.scatteringDensity[1] = 2.0 * tiles[0].scatteringLength;

		// Load the remaining tiles
		for (int i = 1; i < tiles.length; i++) {
			profile.depth[2 * i] = sumD;
			sumD += tiles[i].thickness;
			profile.depth[2 * i + 1] = sumD;
			profile.scatteringDensity[2 * i] = tiles[i].scatteringLength
					+ tiles[0].scatteringLength;
			profile.scatteringDensity[2 * i + 1] = profile.scatteringDensity[2 * i];
		}

		return profile;
	}

	/**
	 * This operation returns the reflectivity profile for the given wave vector
	 * and set of slabs that define the material.
	 *
	 * This function has entirely too many arguments and needs to be refactored.
	 *
	 * @param slabs
	 *            the slabs that define the material
	 * @param numRough
	 *            the number of layers of roughness
	 * @param deltaQ0
	 *            FIXME!
	 * @param deltaQ1ByQ
	 *            FIXME!
	 * @param wavelength
	 *            FIXME!
	 * @param waveVector
	 *            the wave vector
	 * @param getRQ4
	 *            true if the RQ^4 should be calculated, false otherwise
	 * @return The reflectivity profile. It contains both the reflectivity as a
	 *         function of the wave vector and the neutron scattering density as
	 *         a function of depth.
	 */
	public ReflectivityProfile getReflectivityProfile(Slab[] slabs,
			int numRough, double deltaQ0, double deltaQ1ByQ, double wavelength,
			double[] waveVector, boolean getRQ4) {

		ReflectivityProfile profile = new ReflectivityProfile();

		try {
			// Generate the interfacial profile
			double[] zInt = new double[ReflectivityCalculator.maxRoughSize];
			double[] rufInt = new double[ReflectivityCalculator.maxRoughSize];
			getInterfacialProfile(numRough, zInt, rufInt);

			// Correct the refractive indices for incident medium
			double qCCorr = slabs[0].scatteringLength;
			for (int i = 0; i < slabs.length; i++) {
				slabs[i].scatteringLength -= qCCorr;
			}

			// Generate tiled roughness layers
			Tile[] tiles = generateTiles(slabs, numRough, zInt, rufInt);

			// Un-correct the refractive indices for incident medium
			for (int i = 0; i < slabs.length; i++) {
				slabs[i].scatteringLength += qCCorr;
			}

			// Calculate the reflectivities
			double[] reflectivity = convoluteReflectivity(deltaQ0, deltaQ1ByQ,
					wavelength, getRQ4, waveVector, tiles);

			// Get the scattering profile
			ScatteringDensityProfile scatteringProfile = getScatteringDensityProfile(tiles);

			// Put everything into the reflectivity profile
			profile.depth = scatteringProfile.depth;
			profile.reflectivity = reflectivity;
			profile.waveVector = waveVector;
			profile.scatteringDensity = scatteringProfile.scatteringDensity;

		} catch (MathException e) {
			// Complain
			System.err.println("Unable to generate the interfacial profile!");
			logger.error(getClass().getName() + " Exception!",e);
			// Null out the profile so no bad data is returned
			profile = null;
		}

		return profile;
	}

}
