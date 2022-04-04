package fr.igred.ij.macro;

public class BatchParameters {

	private boolean loadROIs;
	private boolean saveImage;
	private boolean saveROIs;
	private boolean saveResults;
	private boolean saveLog;
	private boolean clearROIs;
	private boolean outputOnOMERO;
	private boolean outputOnLocal;
	private long outputDatasetId;
	private long outputProjectId;
	private String directoryOut;
	private String suffix;


	public BatchParameters() {
	}


	public BatchParameters(BatchParameters parameters) {
		this.loadROIs = parameters.loadROIs;
		this.saveImage = parameters.saveImage;
		this.saveROIs = parameters.saveROIs;
		this.saveResults = parameters.saveResults;
		this.saveLog = parameters.saveLog;
		this.clearROIs = parameters.clearROIs;
		this.outputOnOMERO = parameters.outputOnOMERO;
		this.outputOnLocal = parameters.outputOnLocal;
		this.outputDatasetId = parameters.outputDatasetId;
		this.outputProjectId = parameters.outputProjectId;
		this.directoryOut = parameters.directoryOut;
		this.suffix = parameters.suffix;
	}


	public long getOutputProjectId() {
		return outputProjectId;
	}


	public void setOutputProjectId(Long outputProjectId) {
		if (outputProjectId != null) this.outputProjectId = outputProjectId;
	}


	public long getOutputDatasetId() {
		return outputDatasetId;
	}


	public void setOutputDatasetId(Long outputDatasetId) {
		if (outputDatasetId != null) this.outputDatasetId = outputDatasetId;
	}


	public boolean shouldSaveROIs() {
		return saveROIs;
	}


	public void setSaveROIs(boolean saveROIs) {
		this.saveROIs = saveROIs;
	}


	public boolean shouldSaveResults() {
		return saveResults;
	}


	public void setSaveResults(boolean saveResults) {
		this.saveResults = saveResults;
	}


	public boolean shouldSaveImage() {
		return saveImage;
	}


	public void setSaveImage(boolean saveImage) {
		this.saveImage = saveImage;
	}


	public boolean shouldLoadROIs() {
		return loadROIs;
	}


	public void setLoadROIS(boolean loadROIs) {
		this.loadROIs = loadROIs;
	}


	public boolean shouldClearROIs() {
		return clearROIs;
	}


	public void setClearROIS(boolean clearROIs) {
		this.clearROIs = clearROIs;
	}


	public String getDirectoryOut() {
		return directoryOut;
	}


	public void setDirectoryOut(String directoryOut) {
		this.directoryOut = directoryOut;
	}


	public String getSuffix() {
		return suffix;
	}


	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}


	public boolean isOutputOnOMERO() {
		return outputOnOMERO;
	}


	public void setOutputOnOMERO(boolean outputOnOMERO) {
		this.outputOnOMERO = outputOnOMERO;
	}


	public boolean isOutputOnLocal() {
		return outputOnLocal;
	}


	public void setOutputOnLocal(boolean outputOnLocal) {
		this.outputOnLocal = outputOnLocal;
	}


	public boolean shouldSaveLog() {
		return this.saveLog;
	}


	public void setSaveLog(boolean saveLog) {
		this.saveLog = saveLog;
	}

}
