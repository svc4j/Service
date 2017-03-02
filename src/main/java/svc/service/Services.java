package svc.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;

import com.alibaba.fastjson.JSONObject;

import svc.core.AuthChecker;
import svc.core.Callable;
import svc.core.CodeMessageException;
import svc.core.Inject;
import svc.core.ParameterChecker;
import svc.core.base.Config;
import svc.core.base.JSONUtil;
import svc.core.base.Log;
import svc.service.ann.Service;

public class Services {
	private static String _basePackage = "";
	private static Boolean _enabledDebugLog = true;
	private static String _accessToken = "";
	private static Callable _callable = new Callable();
	private static Map<String, String> _maps = new TreeMap<>(new Comparator<String>() {
		public int compare(String o1, String o2) {
			return o2.length() - o1.length();
		}
	});

	static {
		_basePackage = Config.get("SERVICES_BASE_PACKAGE", "");
		if (_basePackage.length() > 0 && _basePackage.charAt(_basePackage.length() - 1) != '.') {
			_basePackage += ".";
		}
		_accessToken = Config.get("SERVICES_ACCESS_TOKEN", "");
		for (Map.Entry<String, String> item : Config.getAllNoPrefix("SERVICES_AUTH_TOKENS_").entrySet()) {
			try {
				setAuthToken(Integer.parseInt(item.getKey()), item.getValue());
			} catch (Exception e) {
			}
		}

		for (Map.Entry<String, String> map : Config.getAllNoPrefix("SERVICES_MAP_").entrySet()) {
			_maps.put(map.getKey(), map.getValue());
		}

	}

	static public void setBasePackage(String base_package) {
		_basePackage = base_package;
	}

	static public void enableDebugLog(Boolean enabled) {
		_enabledDebugLog = enabled;
	}

	static public void setAuthToken(int level, String token) {
		// _authTokens.put(level, token);
		_callable.setAuthChecker(level, new AuthChecker() {
			public boolean checkAuthLevel(JSONObject args) {
				return token.equals(args.getString("accessToken"));
			}
		});
	}

	static public void setAccessToken(String token) {
		_accessToken = token;
	}

	static public void setParmChecker(String type, ParameterChecker checker) {
		_callable.setParmChecker(type, checker);
	}

	static public void setInject(Class<?> type, Inject checker) {
		_callable.setInject(type, checker);
	}

	public static JSONObject ok() {
		return ok("", null);
	}

	public static JSONObject ok(JSONObject data) {
		return ok("", data);
	}

	public static JSONObject ok(String key1, Object value1, Object... moreKeyValues) {
		return ok("", JSONUtil.obj(key1, value1, moreKeyValues));
	}

	public static JSONObject ok(String message) {
		return ok(message, null);
	}

	public static JSONObject ok(String message, String key1, Object value1, Object... moreKeyValues) {
		return ok(message, JSONUtil.obj(key1, value1, moreKeyValues));
	}

	public static JSONObject ok(String message, JSONObject data) {
		return _buildResult(200, message, data);
	}

	static public JSONObject error(int code, String message) {
		return error(code, message, null);
	}

	static public JSONObject error(int code, String message, String key1, Object value1, Object... moreKeyValues) {
		return error(code, message, JSONUtil.obj(key1, value1, moreKeyValues));
	}

	static public JSONObject error(int code, String message, JSONObject data) {
		return _buildResult(code, message, data);
	}

	static private JSONObject _buildResult(int code, String message, JSONObject data) {
		if (message == null)
			message = "";
		if (data == null)
			data = new JSONObject();
		JSONObject o = new JSONObject();
		o.put("code", code);
		o.put("message", message);
		o.put("data", data);
		return o;
	}

	static public JSONObject call(String target) {
		return call(target, null, null);
	}

	static public JSONObject call(String target, JSONObject args) {
		return call(target, args, null);
	}

	static public JSONObject call(String target, JSONObject args, Callback callback) {

		if (!target.startsWith(_basePackage)) {
			target = _basePackage + target;
		}
		if (args == null)
			args = new JSONObject();

		long start_time = 0;
		if (_enabledDebugLog) {
			System.currentTimeMillis();
		}
		if (args.getString("accessToken") == null) {
			args.put("accessToken", _accessToken);
		}

		String map_url = null;
		for (Map.Entry<String, String> map : _maps.entrySet()) {
			if (target.startsWith(map.getKey())) {
				map_url = map.getValue() + target.substring(map.getKey().length()).replace(".", "/");
				break;
			}
		}

		Boolean call_remote = false;
		if (map_url != null && args.getString("httpClientIp") == null) {
			call_remote = true;
		}

		JSONObject result = null;
		if (!call_remote) {
			try {
				result = _callable.call(target, args, Service.class, JSONObject.class);
				if (map_url != null && result.getIntValue("code") == 404) {
					// 本地找不到对象并且配置了远端时，再次尝试从远端请求
					call_remote = true;
				}
			} catch (CodeMessageException e) {
				if (e.getCode() < 500)
					Log.warn("Services.failed", target, e);
				else
					Log.error("Services.failed", target, e);
				result = Services.error(e.getCode(), e.getMessage());
			} catch (Throwable e) {
				Log.error("Services.failed", target, e);
				result = Services.error(500, e.getMessage());
			}
		}

		if (call_remote) {
			HttpClient client = new HttpClient();
			client.setMaxConnectionsPerDestination(10000);

			try {
				// 异步处理
				if (callback != null) {
					client.start();
					client.newRequest(map_url).method(HttpMethod.POST)
							.content(new StringContentProvider(args.toString())).send(new BufferingResponseListener() {
								public void onComplete(Result r) {
									// Log.info("aaa2");
									JSONObject result = JSONUtil.toJSONObject(getContentAsString());
									callback.call(result);
								}
							});
					// Log.info("aaa1");
					return null;
				} else {
					client.start();
					result = JSONUtil.toJSONObject(client.newRequest(map_url).method(HttpMethod.POST)
							.content(new StringContentProvider(args.toString())).send().getContentAsString());
					client.stop();
				}
			} catch (Exception e) {
				Log.error("Services.failed", target, e);
				result = Services.error(500, e.getMessage());
			}
		}

		if (result == null || result.getInteger("code") == null) {
			result = Services.ok(result);
		}
		if (_enabledDebugLog) {
			long used_time = System.currentTimeMillis() - start_time;
			Log.debug("Services.debug", target, used_time, result.getIntValue("code"), result.getString("message"),
					args, result.getJSONObject("data"));
		}

		if (callback != null) {
			callback.call(result);
			return null;
		} else {
			return result;
		}
	}

	static public abstract class Callback {
		protected Map<String, Object> savedData = null;

		public Callback(Map<String, Object> saved_data) {
			savedData = saved_data;
			if (savedData == null)
				savedData = new HashMap<>();
		}

		abstract public void call(JSONObject result);
	}

}
