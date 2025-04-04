/*
 *  Copyright (C) 2021-2023 MICA & GReD
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.

 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package fr.igred.ij.macro;


import fr.igred.ij.io.ROIMode;


/**
 * Holds the parameters to batch run scripts.
 */
public class BatchParameters {

	private ROIMode roiMode;
	private boolean saveImages;
	private boolean saveROIs;
	private boolean saveResults;
	private boolean saveLog;
	private boolean clearROIs;
	private boolean outputOnOMERO;
	private boolean outputOnLocal;
	private long outputDatasetId;
	private long outputProjectId;
	private long outputScreenId;
	private String directoryOut;
	private String suffix;


	/**
	 * Default constructor.
	 */
	public BatchParameters() {
		this.roiMode = ROIMode.DO_NOT_LOAD;
		this.saveImages = false;
		this.saveROIs = false;
		this.saveResults = false;
		this.saveLog = false;
		this.clearROIs = false;
		this.outputOnOMERO = false;
		this.outputOnLocal = false;
		this.outputDatasetId = -1L;
		this.outputScreenId = -1L;
		this.outputProjectId = -1L;
		this.suffix = "";
		this.directoryOut = null;
	}


	/**
	 * Copy constructor.
	 *
	 * @param parameters The parameters to copy.
	 */
	public BatchParameters(BatchParameters parameters) {
		this.roiMode = parameters.roiMode;
		this.saveImages = parameters.saveImages;
		this.saveROIs = parameters.saveROIs;
		this.saveResults = parameters.saveResults;
		this.saveLog = parameters.saveLog;
		this.clearROIs = parameters.clearROIs;
		this.outputOnOMERO = parameters.outputOnOMERO;
		this.outputOnLocal = parameters.outputOnLocal;
		this.outputDatasetId = parameters.outputDatasetId;
		this.outputProjectId = parameters.outputProjectId;
		this.outputScreenId = parameters.outputScreenId;
		this.suffix = parameters.suffix;
		this.directoryOut = parameters.directoryOut;
	}


	/**
	 * Returns the output project ID.
	 *
	 * @return See above.
	 */
	public long getOutputProjectId() {
		return outputProjectId;
	}

	/**
	 * Returns the output screen ID.
	 *
	 * @return See above.
	 */
	public long getOutputScreenId() {
		return outputScreenId;
	}


	/**
	 * Sets the output project ID.
	 *
	 * @param outputProjectId See above.
	 */
	public void setOutputProjectId(Long outputProjectId) {
		if (outputProjectId != null) {
			this.outputProjectId = outputProjectId;
		}
	}

	/**
	 * Sets the output screen ID.
	 *
	 * @param outputScreenId See above.
	 */
	public void setOutputScreenId(Long outputScreenId) {
		if (outputScreenId != null) {
			this.outputScreenId = outputScreenId;
		}
	}



	/**
	 * Returns the output dataset ID.
	 *
	 * @return See above.
	 */
	public long getOutputDatasetId() {
		return outputDatasetId;
	}


	/**
	 * Sets the output dataset ID.
	 *
	 * @param outputDatasetId See above.
	 */
	public void setOutputDatasetId(Long outputDatasetId) {
		if (outputDatasetId != null) {
			this.outputDatasetId = outputDatasetId;
		}
	}


	/**
	 * Returns whether the ROIs should be saved or not.
	 *
	 * @return See above.
	 */
	public boolean shouldSaveROIs() {
		return saveROIs;
	}


	/**
	 * Sets whether the ROIs should be saved or not.
	 *
	 * @param saveROIs See above.
	 */
	public void setSaveROIs(boolean saveROIs) {
		this.saveROIs = saveROIs;
	}


	/**
	 * Returns whether the results tables should be saved or not.
	 *
	 * @return See above.
	 */
	public boolean shouldSaveResults() {
		return saveResults;
	}


	/**
	 * Sets whether the results tables should be saved or not.
	 *
	 * @param saveResults See above.
	 */
	public void setSaveResults(boolean saveResults) {
		this.saveResults = saveResults;
	}


	/**
	 * Returns whether the images should be saved or not.
	 *
	 * @return See above.
	 */
	public boolean shouldSaveImages() {
		return saveImages;
	}


	/**
	 * Sets whether the images should be saved or not.
	 *
	 * @param saveImages See above.
	 */
	public void setSaveImages(boolean saveImages) {
		this.saveImages = saveImages;
	}


	/**
	 * Returns whether the ROIs should be loaded or not.
	 *
	 * @return See above.
	 */
	public ROIMode getROIMode() {
		return roiMode;
	}


	/**
	 * Sets whether the ROIs should be loaded or not.
	 *
	 * @param roiMode See above.
	 */
	public void setROIMode(ROIMode roiMode) {
		this.roiMode = roiMode;
	}


	/**
	 * Returns whether the ROIs should be cleared or not.
	 *
	 * @return See above.
	 */
	public boolean shouldClearROIs() {
		return clearROIs;
	}


	/**
	 * Sets whether the ROIs should be cleared or not.
	 *
	 * @param clearROIs See above.
	 */
	public void setClearROIS(boolean clearROIs) {
		this.clearROIs = clearROIs;
	}


	/**
	 * Returns the output directory.
	 *
	 * @return See above.
	 */
	public String getDirectoryOut() {
		return directoryOut;
	}


	/**
	 * Sets the output directory.
	 *
	 * @param directoryOut See above.
	 */
	public void setDirectoryOut(String directoryOut) {
		this.directoryOut = directoryOut;
	}


	/**
	 * Returns the suffix to append to the output files.
	 *
	 * @return See above.
	 */
	public String getSuffix() {
		return suffix;
	}


	/**
	 * Sets the suffix to append to the output files.
	 *
	 * @param suffix See above.
	 */
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}


	/**
	 * Returns whether the output will be on OMERO or not.
	 *
	 * @return See above.
	 */
	public boolean isOutputOnOMERO() {
		return outputOnOMERO;
	}


	/**
	 * Sets whether the output will be on OMERO or not.
	 *
	 * @param outputOnOMERO See above.
	 */
	public void setOutputOnOMERO(boolean outputOnOMERO) {
		this.outputOnOMERO = outputOnOMERO;
	}


	/**
	 * Returns whether the output will be saved locally or not.
	 *
	 * @return See above.
	 */
	public boolean isOutputOnLocal() {
		return outputOnLocal;
	}


	/**
	 * Sets whether the output will be saved locally or not.
	 *
	 * @param outputOnLocal See above.
	 */
	public void setOutputOnLocal(boolean outputOnLocal) {
		this.outputOnLocal = outputOnLocal;
	}


	/**
	 * Returns whether the log should be saved or not.
	 *
	 * @return See above.
	 */
	public boolean shouldSaveLog() {
		return this.saveLog;
	}


	/**
	 * Sets whether the log should be saved or not.
	 *
	 * @param saveLog See above.
	 */
	public void setSaveLog(boolean saveLog) {
		this.saveLog = saveLog;
	}

}
