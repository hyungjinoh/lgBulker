package com.rayful.lgbulker.vo;

import java.util.ArrayList;
import java.util.List;

public class IndexResultVO {
	int createCount;
	int updateCount;
	int deleteCount;
	int partialCount;
	
	int etcCount;
	List<String> etcResult;
	int errorCount;
	List<String> errorReason;
	
	public IndexResultVO() {
		this.createCount = 0;
		this.updateCount = 0;
		this.deleteCount = 0;
		this.partialCount = 0;

		this.etcCount = 0;
		this.etcResult = new ArrayList<> ();
		this.errorCount = 0;
		this.errorReason = new ArrayList<> ();
	}

	public int getTotalCount() {
		return createCount + updateCount + deleteCount + partialCount + etcCount + errorCount ;
	}
	
	public int getCreateCount() {
		return createCount;
	}

	public int getUpdateCount() {
		return updateCount;
	}

	public int getDeleteCount() {
		return deleteCount;
	}

	public int getPartialCount() {
		return partialCount;
	}

	public int getErrorCount() {
		return errorCount;
	}

	public List<String> getErrorReason() {
		return errorReason;
	}

	public int getEtcCount() {
		return etcCount;
	}

	public List<String> getEtcResult() {
		return etcResult;
	}

	public void increaseCreateCount() {
		this.createCount ++;
	}
	
	public void increaseUpdateCount() {
		this.updateCount ++;
	}
	
	public void increaseDeleteCount() {
		this.deleteCount ++;
	}
	
	public void increasePartialCount() {
		this.partialCount ++;
	}
	
	public void increaseErrorCount(String reason) {
		this.errorCount ++;
		this.errorReason.add(reason);
	}
	
	public void increaseEtcCount(String result) {
		this.etcCount ++;
		this.etcResult.add(result);
	}

	@Override
	public String toString() {
		return "IndexResultVO [createCount=" + createCount + ", updateCount=" + updateCount + ", deleteCount="
				+ deleteCount + ", partialCount=" + partialCount + ", etcCount=" + etcCount + ", etcResult=" + etcResult
				+ ", errorCount=" + errorCount + ", errorReason=" + errorReason + "]";
	}
}
