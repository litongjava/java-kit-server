package com.litongjava.kit.handler;

import java.util.Map;

import com.alibaba.fastjson2.util.TypeUtils;
import com.litongjava.mcp.context.McpRequestContext;
import com.litongjava.mcp.exception.McpRpcException;
import com.litongjava.mcp.model.JsonRpcRequest;
import com.litongjava.mcp.model.JsonRpcResponse;
import com.litongjava.mcp.model.McpInitializeParams;
import com.litongjava.mcp.model.McpInitializeResult;
import com.litongjava.mcp.model.McpMethod;
import com.litongjava.mcp.model.McpToolsCallParams;
import com.litongjava.mcp.model.McpToolsCallResult;
import com.litongjava.mcp.model.McpToolsListResult;
import com.litongjava.mcp.model.RpcError;
import com.litongjava.mcp.server.McpServer;
import com.litongjava.tio.boot.http.TioRequestContext;
import com.litongjava.tio.core.ChannelContext;
import com.litongjava.tio.core.Tio;
import com.litongjava.tio.http.common.HeaderName;
import com.litongjava.tio.http.common.HeaderValue;
import com.litongjava.tio.http.common.HttpMethod;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;
import com.litongjava.tio.http.common.utils.HttpIpUtils;
import com.litongjava.tio.http.server.handler.HttpRequestHandler;
import com.litongjava.tio.http.server.util.SseEmitter;
import com.litongjava.tio.utils.UUIDUtils;
import com.litongjava.tio.utils.hutool.StrUtil;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McpHandler implements HttpRequestHandler {

  private McpServer mcpServer;

  public McpHandler(McpServer mcpServer) {
    this.mcpServer = mcpServer;
  }

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    HttpResponse httpResponse = TioRequestContext.getResponse();
    ChannelContext channelContext = httpRequest.getChannelContext();

    String mcpSessionId = httpRequest.getHeader("mcp-session-id");
    String protocolVersion = httpRequest.getHeader("mcp-protocol-version");

    if (mcpSessionId == null) {
      mcpSessionId = UUIDUtils.random();
    }
    httpResponse.addHeader("mcp-session-id", mcpSessionId);

    String realIp = HttpIpUtils.getRealIp(httpRequest);
    Map<String, String> headers = httpRequest.getHeaders();

    if (httpRequest.getMethod().equals(HttpMethod.DELETE)) {
      McpRequestContext mcpRequestContext = new McpRequestContext(mcpSessionId, protocolVersion, realIp, headers);
      mcpServer.sessionClosed(mcpRequestContext);
      return httpResponse;
    }

    String bodyString = httpRequest.getBodyString();
    if (StrUtil.isBlank(bodyString)) {
      return httpResponse;
    }

    JsonRpcRequest rpcRequest = JsonUtils.parse(bodyString, JsonRpcRequest.class);
    if (rpcRequest == null) {
      return httpResponse;
    }
    String method = rpcRequest.getMethod();
    Object id = rpcRequest.getId();

    log.info("method:{}", method);
    if (McpMethod.INITIALIZE.equals(method)) {
      sendHttpResponseHeader(httpResponse, channelContext);
      Map<String, Object> params = rpcRequest.getParams();
      McpInitializeParams mcpInitializeParams = TypeUtils.cast(params, McpInitializeParams.class);
      if (protocolVersion == null) {
        protocolVersion = mcpInitializeParams.getProtocolVersion();
      }
      McpRequestContext mcpRequestContext = new McpRequestContext(mcpSessionId, protocolVersion, realIp, headers);

      McpInitializeResult initialize;
      try {
        initialize = mcpServer.initialize(mcpInitializeParams, mcpRequestContext);
        JsonRpcResponse<McpInitializeResult> resp = new JsonRpcResponse<>();
        resp.setId(id).setResult(initialize);
        sendSSEData(channelContext, resp);
      } catch (McpRpcException e) {
        log.error(e.getMessage(), e);
        JsonRpcResponse<?> resp = new JsonRpcResponse<>();
        RpcError rpcError = new RpcError();
        rpcError.setMessage(e.getMessage());
        resp.setId(id).setError(rpcError);
        sendSSEData(channelContext, resp);
      }

    } else if (McpMethod.NOTIFICATIONS_INITIALIZED.equals(method)) {
      McpRequestContext mcpRequestContext = new McpRequestContext(mcpSessionId, protocolVersion, realIp, headers);
      try {
        mcpServer.notificationsInitialized(mcpRequestContext);
        httpResponse.setStatus(202);
      } catch (McpRpcException e) {
        log.error(e.getMessage(), e);
        httpResponse.setStatus(500);
      }

    } else if (McpMethod.TOOLS_LIST.equals(method)) {
      sendHttpResponseHeader(httpResponse, channelContext);

      McpRequestContext mcpRequestContext = new McpRequestContext(mcpSessionId, protocolVersion, realIp, headers);
      McpToolsListResult result;
      try {
        result = mcpServer.listTools(mcpRequestContext);
        JsonRpcResponse<McpToolsListResult> resp = new JsonRpcResponse<>();
        resp.setId(id).setResult(result);
        sendSSEData(channelContext, resp);
      } catch (McpRpcException e) {
        log.error(e.getMessage(), e);
        JsonRpcResponse<?> resp = new JsonRpcResponse<>();
        RpcError rpcError = new RpcError();
        rpcError.setMessage(e.getMessage());
        resp.setId(id).setError(rpcError);
        sendSSEData(channelContext, resp);
      }

    } else if (McpMethod.TOOLS_CALL.equals(method)) {
      sendHttpResponseHeader(httpResponse, channelContext);

      McpRequestContext mcpRequestContext = new McpRequestContext(mcpSessionId, protocolVersion, realIp, headers);

      Map<String, Object> params = rpcRequest.getParams();
      McpToolsCallParams toolCallparams = TypeUtils.cast(params, McpToolsCallParams.class);
      McpToolsCallResult result;
      try {
        result = mcpServer.callTool(toolCallparams, mcpRequestContext);
        JsonRpcResponse<McpToolsCallResult> resp = new JsonRpcResponse<>();
        resp.setId(id).setResult(result);
        sendSSEData(channelContext, resp);
      } catch (McpRpcException e) {
        log.error(e.getMessage(), e);
        JsonRpcResponse<?> resp = new JsonRpcResponse<>();
        RpcError rpcError = new RpcError();
        rpcError.setMessage(e.getMessage());
        resp.setId(id).setError(rpcError);
        sendSSEData(channelContext, resp);
      }
    }
    return httpResponse;
  }

  private void sendSSEData(ChannelContext channelContext, JsonRpcResponse<?> resp) {
    String data = JsonUtils.toSkipNullJson(resp);
    // 发送数据
    SseEmitter.pushSSEChunk(channelContext, "message", data);
    // 手动移除连接
    // SseEmitter.closeChunkConnection(channelContext);
  }

  private void sendHttpResponseHeader(HttpResponse httpResponse, ChannelContext channelContext) {
    // 设置sse请求头
    httpResponse.addServerSentEventsHeader();
    httpResponse.addHeader(HeaderName.Transfer_Encoding, HeaderValue.from("chunked"));
    httpResponse.addHeader(HeaderName.Keep_Alive, HeaderValue.from("timeout=60"));
    // 手动发送消息到客户端,因为已经设置了sse的请求头,所以客户端的连接不会关闭
    Tio.bSend(channelContext, httpResponse);
    httpResponse.setSend(false);
  }
}
