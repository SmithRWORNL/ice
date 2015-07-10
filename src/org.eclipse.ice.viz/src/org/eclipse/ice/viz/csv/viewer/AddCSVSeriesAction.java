/*******************************************************************************
 * Copyright (c) 2014 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Initial API and implementation and/or initial documentation - Jay Jay Billings,
 *   Jordan H. Deyton, Dasha Gorin, Alexander J. McCaskey, Taylor Patterson,
 *   Claire Saunders, Matthew Wang, Anna Wojtowicz
 *******************************************************************************/
package org.eclipse.ice.viz.csv.viewer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ice.datastructures.resource.ICEResource;
import org.eclipse.ice.datastructures.resource.VizResource;
import org.eclipse.ice.viz.VizFileViewer;
import org.eclipse.ice.viz.service.csv.CSVDataLoader;
import org.eclipse.ice.viz.service.csv.CSVDataProvider;
import org.eclipse.ice.viz.service.csv.PlotProvider;
import org.eclipse.ice.viz.service.csv.SeriesProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This Action opens a dialog that allows the user to create a series plot from
 * the selected {@link VizResource} in the {@link VizFileViewer}.
 * 
 * @author Matthew Wang
 * 
 */
public class AddCSVSeriesAction extends Action {

	/**
	 * Logger for handling event messages and other information.
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(AddCSVSeriesAction.class);

	/**
	 * The ViewPart that owns an object of this class.
	 */
	private final ViewPart viewer;

	/**
	 * The constructor
	 * 
	 * @param parentView
	 *            The ViewPart to whom the object of this class belongs.
	 * @param parentAction
	 *            The AddFileAction to whom the object of this class belongs.
	 */
	public AddCSVSeriesAction(ViewPart parentView, AddCSVPlotAction parentAction) {

		// Keep track of the viewer and parent Action containing this Action
		viewer = parentView;

		// Set the display text
		setText("Add a series");
	}

	/**
	 * The function called whenever the action is clicked.
	 */
	@Override
	public void run() {

		// Get the Shell of the workbench
		Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
				.getShell();

		// Get the viewer as a PlotViewer.
		CSVPlotViewer plotViewer = (CSVPlotViewer) viewer;

		// Get the ICEResource used by the PlotViewer.
		ICEResource resource = plotViewer.getResource();

		if (plotViewer.getSelection() == null) {
			return;
		}

		logger.info("AddCSVSeriesAction message: "
				+ "The currently selected resource is \"" + resource.getName()
				+ "\".");

		// Capture the plotProvider
		PlotProvider plotToAddSeries = plotViewer.getSelection();

		// Can't add a series to a contour plot. Return
		if (plotToAddSeries.isContour()) {
			return;
		}

		// If we pulled an ICEResource from the selection, we may proceed.
		if (resource != null) {

			// Checking for a VizResource for a file set
			if (resource instanceof VizResource
					&& ((VizResource) resource).getFileSet() != null) {
				addSeriesFromFileSet(shell, plotToAddSeries,
						(VizResource) resource);
			} else {
				addSeriesFromFile(shell, plotToAddSeries, resource);
			}
		}

		// Draw the newly added series to the plot
		plotViewer.drawPlot(plotToAddSeries);

		return;
	}

	/**
	 * Add a series to the selected {@link PlotProvider} in the
	 * {@link CSVPlotViewer} from the selected file in the {@link VizFileViewer}
	 * 
	 * @param shell
	 *            The Shell containing the workbench used to host Dialogs.
	 * @param plotToAddSeries
	 *            The PlotProvider to add the series to.
	 * @param resource
	 *            The file selected in the VizFileViewer to pull the plot from.
	 */
	public void addSeriesFromFile(Shell shell, PlotProvider plotToAddSeries,
			ICEResource resource) {
		// Get the file from the contents
		File file = resource.getContents();
		String fileName = file.getAbsolutePath();

		CSVDataProvider newDataProvider = null;

		if (fileName.matches(".*\\.csv$")) {
			// Handle a CSV file
			CSVDataLoader newCSVDataLoader = new CSVDataLoader();
			newDataProvider = newCSVDataLoader.load(fileName);
		} else {
			return;
		}
		// Set the source as the file name
		newDataProvider.setSource(file.getName());

		if (newDataProvider.getFeatureList().size() > 1) {
			setIndependentVar(shell, newDataProvider);
		}

		// Create a new AddPlotDialog
		AddPlotDialog plotDialog = new AddPlotDialog(shell);
		if (plotDialog.open() == Window.CANCEL) {
			return;
		}
		String[] userSelectedPlotTypes = plotDialog.getSelections();

		if (userSelectedPlotTypes != null && userSelectedPlotTypes.length > 0) {
			final String plotType = userSelectedPlotTypes[0];
			if ("Contour".equals(plotType)) {
				return;
			} else {
				// Handle plotting a scatter, line, or bar series graph
				SelectFeatureDialog featureDialog = new SelectFeatureDialog(
						shell);

				// Need to set the independent variables for the feature
				// dialog
				featureDialog.setXAxisFeatures(newDataProvider
						.getIndependentVariables());
				// Need to set the features to be plotted
				featureDialog
						.setYAxisFeatures(newDataProvider.getFeatureList());
				if (featureDialog.open() == Window.OK
						&& !featureDialog.getXAxisFeatures().isEmpty()
						&& !featureDialog.getYAxisFeatures().isEmpty()) {

					// Reset the plot's time to the default (first) one.
					Double plotTime = newDataProvider.getTimes().get(0);
					newDataProvider.setTime(plotTime);

					// Get the features that need to be plotted against each
					// other.
					List<String> xAxisFeatures = featureDialog
							.getXAxisFeatures();
					List<String> yAxisFeatures = featureDialog
							.getYAxisFeatures();

					for (String xAxisFeature : xAxisFeatures) {
						// Every series will have this basic title. The x-axis
						// data name goes second.
						String baseTitle = " vs. " + xAxisFeature + " at "
								+ plotTime;
						for (String yAxisFeature : yAxisFeatures) {
							// Create a new series title for the new series
							String newSeriesTitle = yAxisFeature + baseTitle;

							// Create a new series provider
							SeriesProvider newSeriesProvider = new SeriesProvider();
							newSeriesProvider.setDataProvider(newDataProvider);
							newSeriesProvider.setTimeForDataProvider(plotTime);
							newSeriesProvider.setSeriesTitle(newSeriesTitle);
							newSeriesProvider.setXDataFeature(xAxisFeature);
							newSeriesProvider.setYDataFeature(yAxisFeature);
							newSeriesProvider.setSeriesType(plotType);
							// Add this new series to the plot provider
							plotToAddSeries.addSeries(plotTime,
									newSeriesProvider);
						}
					}
				}
			}
		}
		return;
	}

	/**
	 * Add a series to the selected {@link PlotProvider} in the
	 * {@link CSVPlotViewer} from the selected file set in the
	 * {@link VizFileViewer}.
	 * 
	 * @param shell
	 *            The Shell containing the workbench used to host Dialogs.
	 * @param plotToAddSeries
	 *            The PlotProvider to add the series to.
	 * @param resource
	 *            The file set selected in the VizFileViewer to pull the plot
	 *            from.
	 */
	public void addSeriesFromFileSet(Shell shell, PlotProvider plotToAddSeries,
			VizResource resource) {
		// FIXME Doesn't this method need to mirror what's in
		// CreateCSVPlotAction#plotFileSet(...)?
		String[] fileSet = resource.getFileSet();

		// Creating a new CSVDataProvider for the new file
		CSVDataLoader newDataSetLoader = new CSVDataLoader();
		// Calls the load as file set
		CSVDataProvider newDataSetProvider = null;

		if (resource.getName().matches(".*\\.csv$")) {
			// Handle a CSV file
			newDataSetLoader = new CSVDataLoader();
			newDataSetProvider = newDataSetLoader.loadAsFileSet(fileSet);
		} else {
			return;
		}

		// Set the source of the provider
		newDataSetProvider.setSource(resource.getFileSetTitle());

		// Set the independent variable
		if (newDataSetProvider.getFeatureList().size() > 1) {
			setIndependentVar(shell, newDataSetProvider);
		}

		// Uses the AddPlotDialog class, a custom dialog, to prompt what plot
		// type to use
		AddPlotDialog plotDialog = new AddPlotDialog(shell);
		plotDialog.open();
		// The selected plot types the user would like
		String[] userSelectedPlotTypes = plotDialog.getSelections();

		if (userSelectedPlotTypes == null || userSelectedPlotTypes.length == 0) {
			return;
		}
		final String plotType = userSelectedPlotTypes[0];

		// If there's more than one time, open a select time dialog
		Double currentTime = null;
		if (newDataSetProvider.getTimes().size() > 1) {
			SelectTimeDialog timeDialog = new SelectTimeDialog(shell);
			// Set the available times for the dialog
			timeDialog.setTimes(newDataSetProvider.getTimes());
			// Confirm that the user selected a time and hit okay
			if (timeDialog.open() == Window.OK
					&& timeDialog.getSelectedTime() != null) {
				// Set the selected time for the provider
				currentTime = timeDialog.getSelectedTime();
			}
		} else {
			currentTime = newDataSetProvider.getTimes().get(0);
		}
		newDataSetProvider.setTime(currentTime);

		if ("Contour".equals(userSelectedPlotTypes[0])) {
			// Can't add a series as a contour plot
			return;
		} else {
			if (newDataSetProvider.getDataHeight() != 0
					|| newDataSetProvider.getDataWidth() != 0) {
				return;
			}
			SelectFeatureDialog featureDialog = new SelectFeatureDialog(shell);
			// Set the independent variables from the provider to be plotted
			featureDialog.setXAxisFeatures(newDataSetProvider
					.getIndependentVariables());
			// Set the features from the provider to be plotted
			featureDialog.setYAxisFeatures(newDataSetProvider.getFeatureList());
			// Open the dialog
			if (featureDialog.open() == Window.OK
					&& featureDialog.getXAxisFeatures() != null
					&& featureDialog.getYAxisFeatures() != null) {

				// Get the features that need to be plotted against each other.
				List<String> xAxisFeatures = featureDialog.getXAxisFeatures();
				List<String> yAxisFeatures = featureDialog.getYAxisFeatures();

				// Get the independent variables and features for the data set.
				List<String> independentVariables = newDataSetProvider
						.getIndependentVariables();
				List<String> featureVariables = newDataSetProvider
						.getFeaturesAtCurrentTime();

				// Remove all selected x and y axis variables that are not valid
				// independent and feature variables in the data set.
				for (int x = xAxisFeatures.size() - 1; x >= 0; x--) {
					if (!independentVariables.contains(xAxisFeatures.get(x))) {
						xAxisFeatures.remove(x);
					}
				}
				for (int y = yAxisFeatures.size() - 1; y >= 0; y--) {
					if (!featureVariables.contains(yAxisFeatures.get(y))) {
						yAxisFeatures.remove(y);
					}
				}

				// For all remaning selected x and y axis, plot them.
				for (String xAxisFeature : xAxisFeatures) {
					// Every series will have this basic title. The x-axis
					// data name goes second.
					String baseTitle = " vs. " + xAxisFeature + " at "
							+ currentTime;
					for (String yAxisFeature : yAxisFeatures) {
						// Create a new series title for the new series
						String newSeriesTitle = yAxisFeature + baseTitle;

						// Create a new series provider.
						SeriesProvider newSeriesProvider = new SeriesProvider();
						newSeriesProvider.setDataProvider(newDataSetProvider);
						newSeriesProvider.setTimeForDataProvider(currentTime);
						newSeriesProvider.setSeriesTitle(newSeriesTitle);
						newSeriesProvider.setXDataFeature(xAxisFeature);
						newSeriesProvider.setYDataFeature(yAxisFeature);
						newSeriesProvider.setSeriesType(plotType);
						// Add this new series to the plot provider
						plotToAddSeries.addSeries(currentTime,
								newSeriesProvider);
					}
				}
			}
		}
	}

	/**
	 * This operation presents the user with a dialog to select the independent
	 * variable for the series.
	 * 
	 * @param shell
	 *            The Shell containing the workbench used here to host the
	 *            dialog.
	 * @param newDataProvider
	 *            The CSVDataProvider to set the independent variable for.
	 */
	public void setIndependentVar(Shell shell, CSVDataProvider newDataProvider) {
		// Open the dialog for selecting the independent variables
		SelectIndependentVarDialog independentVarDialog = new SelectIndependentVarDialog(
				shell);
		// Set the feature list that the user will choose from
		independentVarDialog.setFeatureList(newDataProvider.getFeatureList());
		independentVarDialog.setProviderName(newDataProvider.getSourceInfo());

		// If the user hits cancel or doesn't select any independent
		// variables, do not load the file and exit
		if (independentVarDialog.open() == Window.CANCEL
				|| independentVarDialog.getIndependentVars().isEmpty()) {
			System.err.println("No independent variables selected.");
			return;
		}
		// Get the selected independent variables from the dialog
		ArrayList<String> independentVars = independentVarDialog
				.getIndependentVars();
		// Sets the feature as a independent variable in the provider
		for (String newIndependentVar : independentVars) {
			newDataProvider.setFeatureAsIndependentVariable(newIndependentVar);
		}
	}

}
