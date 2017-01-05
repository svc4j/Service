package svc.service.processor;

import java.lang.annotation.Annotation;

import com.alibaba.fastjson.JSONObject;

import svc.core.processor.DocMaker;
import svc.service.ann.Service;

public class ServiceDocMaker extends DocMaker {

	@Override
	protected Class<? extends Annotation> getCallableType() {
		return Service.class;
	}

	@Override
	protected Class<?> getCallableReturnType() {
		return JSONObject.class;
	}

}
