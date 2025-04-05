package com.litongjava.linux.config;

import com.litongjava.context.BootConfiguration;
import com.litongjava.linux.handler.CacheHandler;
import com.litongjava.linux.handler.DataHandler;
import com.litongjava.linux.handler.HlsHandler;
import com.litongjava.linux.handler.ManimHanlder;
import com.litongjava.linux.handler.PingHandler;
import com.litongjava.linux.handler.PythonHanlder;
import com.litongjava.tio.boot.http.interceptor.HttpInteceptorConfigure;
import com.litongjava.tio.boot.http.interceptor.HttpInterceptorModel;
import com.litongjava.tio.boot.satoken.FixedTokenInterceptor;
import com.litongjava.tio.boot.server.TioBootServer;
import com.litongjava.tio.http.server.intf.HttpRequestInterceptor;
import com.litongjava.tio.http.server.router.HttpRequestRouter;
import com.litongjava.tio.utils.environment.EnvUtils;

public class AppConfig implements BootConfiguration {

  public void config() {

    TioBootServer server = TioBootServer.me();
    HttpRequestRouter r = server.getRequestRouter();
    if (r != null) {
      PingHandler pingHanlder = new PingHandler();
      r.add("/ping", pingHanlder::ping);

      PythonHanlder pythonHanlder = new PythonHanlder();
      r.add("/python", pythonHanlder::index);

      ManimHanlder manimHanlder = new ManimHanlder();
      r.add("/manim", manimHanlder::index);

      CacheHandler cacheHandler = new CacheHandler();
      r.add("/cache/**", cacheHandler::index);

      DataHandler dataHandler = new DataHandler();
      r.add("/data/**", dataHandler::index);

      HlsHandler hlsHandler = new HlsHandler();
      r.add("/hls/start", hlsHandler::start);
    }

    String authToken = EnvUtils.get("app.auth.token");

    // tokenInterceptor
    HttpRequestInterceptor tokenInterceptor = new FixedTokenInterceptor(authToken);
    HttpInterceptorModel model = new HttpInterceptorModel();
    model.setInterceptor(tokenInterceptor);

    model.addBlockUrl("/**"); // 拦截所有路由

    // 设置例外路由 index
    model.addAllowUrls("/", "/ping", "/cache/**", "/hls/**", "/data/**");

    HttpInteceptorConfigure serverInteceptorConfigure = new HttpInteceptorConfigure();
    serverInteceptorConfigure.add(model);

    // 将拦截器配置添加到 Tio 服务器,为了提高性能,默认serverInteceptorConfigure为null,必须添加
    server.setHttpInteceptorConfigure(serverInteceptorConfigure);

  }
}
