package com.litongjava.kit.mcp;

import java.util.List;

import com.litongjava.mcp.context.McpRequestContext;
import com.litongjava.mcp.exception.McpRpcException;
import com.litongjava.mcp.model.McpContent;
import com.litongjava.mcp.model.McpInitializeParams;
import com.litongjava.mcp.model.McpInitializeResult;
import com.litongjava.mcp.model.McpServerInfo;
import com.litongjava.mcp.model.McpToolsCallParams;
import com.litongjava.mcp.model.McpToolsCallResult;
import com.litongjava.mcp.model.McpToolsListResult;
import com.litongjava.mcp.server.IMcpServer;
import com.litongjava.mcp.server.McpToolRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * 抽象的 MCP Server，把通用逻辑（注册工具/处理调用/初始化响应）封装在这里。 子类只需提供 getServerName() /
 * getServerVersion()，并可覆盖 registerAdditionalTools() 来扩展工具。
 */
@Slf4j
public abstract class McpServer implements IMcpServer {

  protected final McpToolRegistry registry = new McpToolRegistry();

  protected McpServer() {
    registerTools();
  }

  /** 子类必须提供服务名 */
  /** 子类必须提供版本号 */
  protected abstract McpServerInfo getMcpServerInfo();

  /** 子类注册工具 */
  protected abstract void registerTools();


  // ===== McpServer 通用实现 =====
  @Override
  public McpInitializeResult initialize(McpInitializeParams params, McpRequestContext ctx) throws McpRpcException {
    String protocolVersion = ctx.getProtocolVersion();
    McpServerInfo serverInfo = getMcpServerInfo();
    return McpInitializeResult.build(protocolVersion, serverInfo);
  }

  @Override
  public void notificationsInitialized(McpRequestContext ctx) throws McpRpcException {
    // 缺省无操作；子类可覆写
  }

  @Override
  public McpToolsListResult listTools(McpRequestContext ctx) throws McpRpcException {
    return new McpToolsListResult(registry.getToolDescriptions());
  }

  @Override
  public McpToolsCallResult callTool(McpToolsCallParams params, McpRequestContext ctx) throws McpRpcException {
    String name = params.getName();
    List<McpContent> contents = registry.callTool(name, params.getArguments());
    McpToolsCallResult result = new McpToolsCallResult();
    result.setContent(contents);
    return result;
  }
}
