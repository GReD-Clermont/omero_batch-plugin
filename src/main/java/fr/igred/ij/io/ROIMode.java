/*
 *  Copyright (C) 2021-2026 MICA & GReD
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
package fr.igred.ij.io;


import loci.plugins.in.ImporterOptions;


/**
 * Modes used to load ROIs.
 */
public enum ROIMode {
	/**
	 * Do not load ROIs.
	 */
	DO_NOT_LOAD("No"),
	/**
	 * Load ROIs in the ROI Manager.
	 */
	MANAGER(ImporterOptions.ROIS_MODE_MANAGER),
	/**
	 * Load ROIs as overlay.
	 */
	OVERLAY(ImporterOptions.ROIS_MODE_OVERLAY);

	/**
	 * ROI mode String value for ImporterOptions and user selection.
	 */
	private final String value;


	/**
	 * Constructor of the ROIMode enum.
	 *
	 * @param value The ROI mode String value for ImporterOptions.
	 */
	ROIMode(String value) {
		this.value = value;
	}


	/**
	 * Returns the ROI mode String value for ImporterOptions.
	 *
	 * @return See above.
	 */
	@Override
	public String toString() {
		return value;
	}
}
