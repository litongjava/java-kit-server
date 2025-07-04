package com.litongjava.linux.config;

import com.litongjava.context.BootConfiguration;
import com.litongjava.linux.handler.CmdHanlder;
import com.litongjava.linux.handler.DataHandler;
import com.litongjava.linux.handler.DownloadHandler;
import com.litongjava.linux.handler.HlsHandler;
import com.litongjava.linux.handler.ManimHanlder;
import com.litongjava.linux.handler.ManimImageHandler;
import com.litongjava.linux.handler.PingHandler;
import com.litongjava.linux.handler.PythonHanlder;
import com.litongjava.linux.handler.ScriptsHandler;
import com.litongjava.linux.handler.SpeedTestHandler;
import com.litongjava.linux.handler.VideoWaterHandler;
import com.litongjava.linux.handler.YoutubeHandler;
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

      CmdHanlder cmdHanlder = new CmdHanlder();
      r.add("/cmd", cmdHanlder::index);

      PythonHanlder pythonHanlder = new PythonHanlder();
      r.add("/python", pythonHanlder::index);

      ManimHanlder manimHanlder = new ManimHanlder();
      r.add("/manim/start", manimHanlder::start);
      r.add("/manim/finish", manimHanlder::finish);
      r.add("/manim", manimHanlder::index);

      ManimImageHandler manimImageHandler = new ManimImageHandler();
      r.add("/manim/image", manimImageHandler::index);

      DataHandler dataHandler = new DataHandler();
      r.add("/data/**", dataHandler::index);
      r.add("/cache/**", dataHandler::index);
      r.add("/media/**", dataHandler::index);

      ScriptsHandler scriptsHandler = new ScriptsHandler();
      r.add("/scripts/**", scriptsHandler::index);

      HlsHandler hlsHandler = new HlsHandler();
      r.add("/hls/start", hlsHandler::start);

      VideoWaterHandler videoWaterHandler = new VideoWaterHandler();
      r.add("/video/download/water", videoWaterHandler::index);

      YoutubeHandler youtubeHandler = new YoutubeHandler();
      r.add("/youtube/download/mp3", youtubeHandler::downloadMp3);

      DownloadHandler downloadHandler = new DownloadHandler();
      r.add("/download", downloadHandler::donwload);
      
      SpeedTestHandler speedTestHandler = new SpeedTestHandler();
      r.add("/speed/test", speedTestHandler::output);
    }

    String authToken = EnvUtils.get("app.auth.token");

    // tokenInterceptor
    HttpRequestInterceptor tokenInterceptor = new FixedTokenInterceptor(authToken);
    HttpInterceptorModel model = new HttpInterceptorModel();
    model.setInterceptor(tokenInterceptor);

    model.addBlockUrl("/**"); // 拦截所有路由

    // 设置例外路由 index
    model.addAllowUrls("/", "/ping", "/download", "/youtube/**", "/media/**", "/cache/**",
        //
        "/hls/**", "/data/**", "/scripts/**", "/video/download/water","/speed/test");

    HttpInteceptorConfigure serverInteceptorConfigure = new HttpInteceptorConfigure();
    serverInteceptorConfigure.add(model);

    // 将拦截器配置添加到 Tio 服务器,为了提高性能,默认serverInteceptorConfigure为null,必须添加
    server.setHttpInteceptorConfigure(serverInteceptorConfigure);

  }
}
