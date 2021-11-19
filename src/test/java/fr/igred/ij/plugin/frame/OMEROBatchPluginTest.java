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