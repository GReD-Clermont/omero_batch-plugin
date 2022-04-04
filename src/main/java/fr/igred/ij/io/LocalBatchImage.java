package fr.igred.ij.io;

import fr.igred.omero.repository.ImageWrapper;
import ij.ImagePlus;
import loci.formats.FileStitcher;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class LocalBatchImage implements BatchImage {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private static final File[] EMPTY_FILE_ARRAY = new File[0];

	private final String path;
	private final Integer index;


	public LocalBatchImage(String path, Integer index) {
		this.path = path;
		this.index = index;
	}


	public static List<BatchImage> listImages(String directory, boolean recursive) throws IOException {
		ImporterOptions options = initImporterOptions();
		List<BatchImage> batchImages = new LinkedList<>();
		File dir = new File(directory);
		List<String> files = listFiles(dir, recursive);
		List<String> used = new ArrayList<>(files.size());
		for (String file : files) {
			if (!used.contains(file)) {
				// Open the image
				options.setId(file);
				ImportProcess process = new ImportProcess(options);
				try {
					process.execute();
					int n = process.getSeriesCount();
					FileStitcher fs = process.getFileStitcher();
					if (fs != null) used = Arrays.asList(fs.getUsedFiles());
					else used.add(file);
					for (int i = 0; i < n; i++) {
						batchImages.add(new LocalBatchImage(file, i));
					}
				} catch (IOException | FormatException e) {
					Logger.getLogger(MethodHandles.lookup().lookupClass().getName()).severe(e.getMessage());
				}
			}
		}
		return batchImages;
	}


	/**
	 * Initializes the Bio-Formats importer options.
	 *
	 * @return See above.
	 *
	 * @throws IOException If the importer options could not be initialized.
	 */
	private static ImporterOptions initImporterOptions() throws IOException {
		ImporterOptions options = new ImporterOptions();
		options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
		options.setSwapDimensions(false);
		options.setOpenAllSeries(false);
		options.setSpecifyRanges(false);
		options.setShowMetadata(false);
		options.setShowOMEXML(false);
		options.setCrop(false);
		options.setSplitChannels(false);
		options.setSplitFocalPlanes(false);
		options.setSplitTimepoints(false);
		return options;
	}


	private static List<String> listFiles(File directory, boolean recursive) {
		File[] files = directory.listFiles();
		if (files == null) files = EMPTY_FILE_ARRAY;
		List<String> paths = new ArrayList<>(files.length);
		for (File file : files) {
			if (!file.isDirectory()) {
				String path = file.getAbsolutePath();
				paths.add(path);
			} else if (recursive) {
				listFiles(file, true);
			}
		}
		return paths;
	}


	@Override
	public ImageWrapper getImageWrapper() {
		return null;
	}


	@Override
	public ImagePlus getImagePlus() {
		ImagePlus imp = null;
		try {
			ImporterOptions options = initImporterOptions();
			options.setId(path);
			options.setSeriesOn(index, true);
			ImagePlus[] imps = BF.openImagePlus(options);
			imp = imps[0];
		} catch (FormatException | IOException e) {
			LOGGER.severe(e.getMessage());
		}
		return imp;
	}


	@Override
	public Long getId() {
		return index == null ? null : index.longValue();
	}

}
