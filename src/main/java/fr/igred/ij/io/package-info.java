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
/**
 * This package contains interfaces and classes meant to handle input/output, most notably images:
 * <ul>
 *     <li>{@link fr.igred.ij.io.BatchImage} to generally handle images in batch</li>
 *     <li>{@link fr.igred.ij.io.OMEROBatchImage} to manage images from OMERO</li>
 *     <li>{@link fr.igred.ij.io.LocalBatchImage} to manage local images</li>
 * </ul>
 * It also contains {@link fr.igred.ij.io.ROIMode} to handle ROI loading.
 */
package fr.igred.ij.io;