package com.litongjava.kit.config;

import java.util.ArrayList;
import java.util.List;

import com.litongjava.context.BootConfiguration;
import com.litongjava.kit.handler.CmdHanlder;
import com.litongjava.kit.handler.GzipBombTestHandler;
import com.litongjava.kit.handler.HlsHandler;
import com.litongjava.kit.handler.ManimImageHandler;
import com.litongjava.kit.handler.ManimVideoHanlder;
import com.litongjava.kit.handler.McpHandler;
import com.litongjava.kit.handler.MomeryHandler;
import com.litongjava.kit.handler.PingHandler;
import com.litongjava.kit.handler.PythonHanlder;
import com.litongjava.kit.handler.ScriptsHandler;
import com.litongjava.kit.handler.SpeedTestHandler;
import com.litongjava.kit.handler.TestController;
import com.litongjava.kit.handler.VideoWaterHandler;
import com.litongjava.kit.handler.YoutubeHandler;
import com.litongjava.kit.mcp.McpCoderServer;
import com.litongjava.llm.proxy.config.LLMProxyAppConfig;
import com.litongjava.tio.boot.admin.config.TioAdminDbConfiguration;
import com.litongjava.tio.boot.http.handler.common.HttpFileDataHandler;
import com.litongjava.tio.boot.http.handler.controller.TioBootHttpControllerRouter;
import com.litongjava.tio.boot.http.interceptor.HttpInteceptorConfigure;
import com.litongjava.tio.boot.http.interceptor.HttpInterceptorModel;
import com.litongjava.tio.boot.satoken.FixedTokenInterceptor;
import com.litongjava.tio.boot.server.TioBootServer;
import com.litongjava.tio.http.server.intf.HttpRequestInterceptor;
import com.litongjava.tio.http.server.router.HttpRequestRouter;
import com.litongjava.tio.utils.environment.EnvUtils;
import com.litongjava.uni.config.UniAiAppConfig;

public class KitAppConfig implements BootConfiguration {

  public void config() {

    // 配置数据库相关
    new TioAdminDbConfiguration().config();

    new UniAiAppConfig().config();
    new LLMProxyAppConfig().config();

    TioBootServer server = TioBootServer.me();

    HttpRequestRouter r = server.getRequestRouter();
    if (r != null) {
      PingHandler pingHanlder = new PingHandler();
      r.add("/ping", pingHanlder::ping);

      CmdHanlder cmdHanlder = new CmdHanlder();
      r.add("/cmd", cmdHanlder::index);

      PythonHanlder pythonHanlder = new PythonHanlder();
      r.add("/python", pythonHanlder::index);

      ManimVideoHanlder manimHanlder = new ManimVideoHanlder();
      r.add("/manim/start", manimHanlder::start);
      r.add("/manim/finish", manimHanlder::finish);
      r.add("/manim", manimHanlder::index);

      ManimImageHandler manimImageHandler = new ManimImageHandler();
      r.add("/manim/image", manimImageHandler::index);

      HttpFileDataHandler dataHandler = new HttpFileDataHandler(false);
      r.add("/data/**", dataHandler::index);
      r.add("/cache/**", dataHandler::index);
      r.add("/media/**", dataHandler::index);

      ScriptsHandler scriptsHandler = new ScriptsHandler();
      r.add("/scripts/**", scriptsHandler::index);

      HlsHandler hlsHandler = new HlsHandler();
      r.add("/hls/start", hlsHandler::start);

      VideoWaterHandler videoWaterHandler = new VideoWaterHandler();
      r.add("/video/download/water", videoWaterHandler::index);
      r.add("/video/exists", videoWaterHandler::exists);

      YoutubeHandler youtubeHandler = new YoutubeHandler();
      r.add("/youtube/download/mp3", youtubeHandler::downloadMp3);

      SpeedTestHandler speedTestHandler = new SpeedTestHandler();
      r.add("/speed/test", speedTestHandler::output);

      GzipBombTestHandler gzipBombTestHandler = new GzipBombTestHandler();
      r.add("/gzip/bomb/test", gzipBombTestHandler::output);

      MomeryHandler momeryHandler = new MomeryHandler();
      r.add("/memory", momeryHandler::index);

      McpCoderServer mcpCoderServer = new McpCoderServer();
      McpHandler mcpCoderHandler = new McpHandler(mcpCoderServer);
      r.add("/mcp", mcpCoderHandler);
//      WebSocketRouter router = TioBootServer.me().getWebSocketRouter();
//      router.add("/mcp/coder", mcpCoderHandler);
    }

    TioBootHttpControllerRouter controllerRouter = TioBootServer.me().getControllerRouter();
    if (controllerRouter == null) {
      return;
    }
    List<Class<?>> scannedClasses = new ArrayList<>();
    scannedClasses.add(TestController.class);
    controllerRouter.addControllers(scannedClasses);

    String authToken = EnvUtils.get("app.auth.token");

    // tokenInterceptor
    HttpRequestInterceptor tokenInterceptor = new FixedTokenInterceptor(authToken);
    HttpInterceptorModel model = new HttpInterceptorModel();
    model.setInterceptor(tokenInterceptor);

    model.addBlockUrl("/**"); // 拦截所有路由

    // 设置例外路由 index
    model.addAllowUrls("/", "/ping", "/download", "/youtube/**",
        //
        "/media/**", "/cache/**", "/hls/**", "/data/**", "/scripts/**", "/video/exists", "/video/download/water",
        //
        "/speed/test", "/gzip/bomb/test",
        //
        "openai/**", "/anthropic/**", "/google/**", "/openrouter/**", "/cerebras/**",
        //
        "/test/**", "/tts", "/subtitle", "/mcp");

    HttpInteceptorConfigure serverInteceptorConfigure = new HttpInteceptorConfigure();
    serverInteceptorConfigure.add(model);

    // 将拦截器配置添加到 Tio 服务器,为了提高性能,默认serverInteceptorConfigure为null,必须添加
    server.setHttpInteceptorConfigure(serverInteceptorConfigure);

  }
}
