/*
 *  Copyright (C) 2021-2022 MICA & GReD
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
package fr.igred.ij.plugin.frame;


import ij.IJ;


public class OMEROBatchPluginTest {

    public static void main(String[] args) {
        Class<?> clazz = OMEROBatchPlugin.class;
        String name = clazz.getName();
        String url = clazz.getResource("/" +
                                       name.replace('.', '/') +
                                       ".class").toString();
        String pluginsDir = url.substring(5, url.length() - name.length() - 6);
        System.setProperty("plugins.dir", pluginsDir);
        // run the plugin
        IJ.runPlugIn(name, "");
    }
}