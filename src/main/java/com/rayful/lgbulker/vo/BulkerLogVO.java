package com.rayful.lgbulker.vo;

public class BulkerLogVO {
	public final CountLogVO email;
	public final CountLogVO attach;
	public final CountLogVO query;
	public final CountLogVO dump;
	public final CountLogVO index;
	public final IndexResultVO indexResult;
	public final CountLogVO qadump;
	public final CountLogVO entity;

	public BulkerLogVO() {
      this.email = new CountLogVO();
      this.attach = new CountLogVO();
	  this.query = new CountLogVO();
		this.dump = new CountLogVO();
		this.index = new CountLogVO();
		this.indexResult = new IndexResultVO();
		this.qadump = new CountLogVO();
		this.entity = new CountLogVO();
	}
	
}
