package com.test.service;

import com.alibaba.fastjson.JSONObject;

import svc.core.ann.*;
import svc.core.base.JSONUtil;
import svc.service.Services;
import svc.service.ann.Service;

@Service("User Service")
public class User {

	@Action(authLevel = 0)
	public JSONObject getLevel0() {
		return Services.ok("level",0);
	}
	
	@Action(authLevel = 1)
	public JSONObject getLevel1() {
		return Services.ok("level",1);
	}
	
	@Action(authLevel = 2)
	public JSONObject getLevel2() {
		return JSONUtil.obj("level",2);
	}
	
	@Action(authLevel = 3)
	public JSONObject getLevel3() {
		return Services.ok("level",3);
	}

}
