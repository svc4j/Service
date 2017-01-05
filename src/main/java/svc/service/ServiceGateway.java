package svc.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import svc.core.CodeMessageException;
import svc.core.base.JSONUtil;
import svc.core.base.Log;

public class ServiceGateway extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		String url_path = request.getRequestURI();
		if(url_path.equals("/favicon.ico")){
			return;
		}

		String class_path = "";
		String method_name = "";
		Throwable ex = null;
		int code = 200;
		String message = "";
		JSONObject args = new JSONObject();
		long start_time = System.currentTimeMillis();
		String client_ip = request.getHeader("x-forwarded-for");
		if (client_ip == null)
			client_ip = request.getRemoteAddr();
		// TODO:过滤掉URL的版本号
		try {
			response.setContentType("application/json; charset=UTF8");
			// 定位站点，分解将要访问的类名和方法名
			int context_path_len = request.getContextPath().length();
			if (context_path_len > 0)
				url_path = url_path.substring(context_path_len);

			// 解析出方法名
			int pos = url_path.lastIndexOf("/");
			if (pos < 0)
				throw new CodeMessageException(400, "Bad request with", url_path);
			method_name = url_path.substring(pos + 1).replace(".", "_"); // 文件名的点在方法名中转换为

			class_path = url_path.substring(0, pos);
			if ("".equals(class_path))
				class_path = "/Index"; // 根目录下使用 Index 作为默认的类

			class_path = class_path.replaceAll("/", ".");
			if (class_path.charAt(0) == '.')
				class_path = class_path.substring(1);
			if ("".equals(method_name))
				method_name = "index"; // 根目录下使用 index 作为默认的方法
			// Log.debug( "请求", class_path, method_name, url_path );

			args.put("httpClientIp", client_ip);
			args.put("httpUserAgent", request.getHeader("User-Agent"));

			for (Entry<String, String[]> posts : request.getParameterMap().entrySet()) {
				if (posts.getValue().length > 1)
					args.put(posts.getKey(), Arrays.asList(posts.getValue()));
				else
					args.put(posts.getKey(), posts.getValue()[0]);
			}

			Enumeration<String> attrs = request.getAttributeNames();
			while (attrs.hasMoreElements()) {
				String name = attrs.nextElement();
				Object obj = request.getAttribute(name);
				args.put(name, obj);
			}

			int clen = request.getContentLength();
			if (clen > 0) {
				byte[] buf = new byte[clen];
				InputStream is = request.getInputStream();
				is.read(buf);
				String json_str = new String(buf, "UTF-8");
				if (json_str.startsWith("{")) {
					args.putAll(JSONUtil.toJSONObject(json_str));
				}
			}

			JSONObject result = Services.call(class_path + "." + method_name, args);
			if (result == null) {
				throw new CodeMessageException(407, "Null result");
			}
			response.getWriter().print(JSONObject.toJSONString(result, SerializerFeature.WriteMapNullValue,
					SerializerFeature.DisableCircularReferenceDetect));
			code = result.getIntValue("code");
			message = result.getString("message");
		} catch (CodeMessageException e) {
			response.setStatus(e.getCode());
			code = e.getCode();
			message = e.getMessage();
			if (code != 404)
				ex = e;
			response.getWriter().print(Services.error(code, message));
		} catch (Throwable e) {
			response.setStatus(500);
			code = 500;
			message = e.getMessage();
			ex = e;
			response.getWriter().print(Services.error(500, "Unknow error"));
		} finally {
			long used_time = System.currentTimeMillis() - start_time;

			args.remove("httpClientIp");
			args.remove("httpUserAgent");
			String msg = String.format("%d	%d	%s	%s:%s	%s	%s	%s	%s	%d	%d	%s", response.getStatus(), code, client_ip,
					class_path, method_name, args.toString(), request.getHeader("Referer"),
					request.getHeader("User-Agent"), request.getCookies(), response.getBufferSize(), used_time,
					message);
			if (code < 300)
				Log.info("Services.access", msg);
			else if (code < 500)
				Log.warn("Services.error", msg, ex);
			else
				Log.error("Services.error", msg, ex);
		}
	}
}
