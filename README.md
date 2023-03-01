[![DOI](https://img.shields.io/badge/DOI-10.12688%2Ff1000research.110385.2-GREEN)](https://doi.org/10.12688/f1000research.110385.2)

# OMERO Batch Plugin

An ImageJ plugin to run a script on a batch of images from/to OMERO.

## How to install

1. Install the [OMERO.insight plugin](https://omero-guides.readthedocs.io/en/latest/fiji/docs/installation.html) (if you
   haven't already).
2. Download the JAR file for this [library](https://github.com/GReD-Clermont/simple-omero-client/releases/tag/5.12.1/).
3. Download the JAR file ([for this plugin](https://github.com/GReD-Clermont/omero_batch-plugin/releases/tag/1.0.5/)).
4. Place these JAR files in your "plugins" folder.

## How to use

### 1. Start the plugin

Once installed, the plugin is accessible from "Plugins > OMERO > Batch process...".

### 2. Choose the source

Specify if you want to process images from OMERO or from a local folder. If you choose "OMERO", a connection window will
appear.

### 3. Select the input images...

#### a. ... from OMERO

If you want to process images from OMERO, once connected, select the dataset you want to process in the "input" panel.
You may have to change the group, user or project to get to the required dataset. You can also specify if you want to
load ROIs from the images or if these should be removed before saving the results (useful if you want to replace ROIs).

#### b. ... from a local folder

If you chose to process local images, you have to browse and select a folder in the "input" panel. To treat
subdirectories, you have to check the "recursive" option.

### 4. Select the macro or script file

In the "macro" panel, you have to browse and set the script file that will be used to process each image. If you use the
plugin in ImageJ2/Fiji, you should be able to choose a script in any supported language. You can then set the arguments
for this script:

- If script parameters are expected in ImageJ2, a window will appear to set the values.
- For ImageJ1 macros, a command line will be passed as an argument (the macro has to use `getArguments()` though).

You also have to specify which output is expected for this script: new image(s), tables, ROIs and/or logs.

### 5. Choose where to save the results

Finally, you have to specify if the results have to be saved locally, on OMERO or both. Furthermore, if you save new
images, you can set a suffix to append to the images names.

#### a. Saving locally

You only have to set the output folder.

#### b. Saving on OMERO

You have to select a project you own, or a dataset if you want to save new images. Be aware of the following:

- Only new images will be saved, unless the original image is not owned by the current user and ROIs should be saved.
- If new images are imported:
	- ROIs from each image overlay will be saved to the corresponding image.
	- ROIs from the ROI manager will be saved to the last image selected in ImageJ.
- If only ROIs are to be saved, they will be added to the input image on OMERO, provided the user has the rights to do
  so.

## About ROIs

As OMERO handles 3D ROIs, it is possible to store these. However, the script used should generate ROIs and add a "ROI"
property to each, and set the same numeric value for those who belong to the same 3D object.

Conversely, if ROIs are loaded from OMERO, they will have two properties set, "ROI" and "ROI_ID":
- "ROI" has the 3D ROI *local* index (eg: for the current image) which contains the 2D shape. 
- "ROI_ID" has the OMERO ID corresponding to the 3D ROI the current 2D shape belongs to. 
