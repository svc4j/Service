package svc.service;

import com.alibaba.fastjson.JSONObject;

import junit.framework.TestCase;
import svc.core.base.JSONUtil;
import svc.core.base.Log;

public class ServicesTest extends TestCase {

	public void testServices() throws InterruptedException {
		Log.setLevel("Off");
		_callUserTests("bad_token", new int[] { 200, 403, 403, 403 });
		_callUserTests("token_111", new int[] { 200, 200, 403, 403 });
		_callUserTests("token_222", new int[] { 200, 200, 200, 403 });
		_callUserTests("token_333", new int[] { 200, 200, 200, 200 });

		_callUserTestsAsync("bad_token", new int[] { 200, 403, 403, 403 });
		_callUserTestsAsync("token_111", new int[] { 200, 200, 403, 403 });
		_callUserTestsAsync("token_222", new int[] { 200, 200, 200, 403 });
		_callUserTestsAsync("token_333", new int[] { 200, 200, 200, 200 });
		Thread.sleep(1000);
	}

	private void _callUserTests(String access_token, int[] codes) {
		JSONObject r, args = JSONUtil.obj("accessToken", access_token);
		for (int level = 0; level <= 3; level++) {
			r = Services.call("User.getLevel" + level, args);
			assertEquals("level "+level+" by "+access_token, r.getIntValue("code"), codes[level]);
			if( r.getIntValue("code") == 200 ){
				assertEquals("level "+level+" by "+access_token+" return "+level, r.getJSONObject("data").getIntValue("level"), level);
			}
		}
	}

	private void _callUserTestsAsync(String access_token, int[] codes) {
		JSONObject r, args = JSONUtil.obj("accessToken", access_token);
		for (int level = 0; level <= 3; level++) {

			Services.call("User.getLevel" + level, args, new Services.Callback(JSONUtil.obj("level", level, "code", codes[level])) {
				public void call(JSONObject r) {
					Log.info("result2",r);
					Integer level = (Integer)savedData.get("level");
					Integer code = (Integer)savedData.get("code");
					Log.debug("code",r.getIntValue("code"), code.intValue());
					assertEquals("level " + level + " by " + access_token, r.getIntValue("code"), code.intValue());
					if (r.getIntValue("code") == 200) {
						assertEquals("level " + level + " by " + access_token + " return " + level, r.getJSONObject("data").getIntValue("level"), level.intValue());
					}
				}
			});
		}
	}

}
