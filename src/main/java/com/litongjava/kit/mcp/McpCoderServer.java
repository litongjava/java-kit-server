package com.litongjava.kit.mcp;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.litongjava.kit.utils.CmdInterpreterUtils;
import com.litongjava.kit.utils.PythonInterpreterUtils;
import com.litongjava.mcp.context.McpRequestContext;
import com.litongjava.mcp.exception.McpRpcException;
import com.litongjava.mcp.model.McpContent;
import com.litongjava.mcp.model.McpInitializeParams;
import com.litongjava.mcp.model.McpInitializeResult;
import com.litongjava.mcp.model.McpToolDescription;
import com.litongjava.mcp.model.McpToolsCallParams;
import com.litongjava.mcp.model.McpToolsCallResult;
import com.litongjava.mcp.model.McpToolsListResult;
import com.litongjava.mcp.server.McpServer;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.hutool.FileUtil;
import com.litongjava.tio.utils.hutool.ResourceUtil;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class McpCoderServer implements McpServer {

  public static final String run_python = "run_python";
  public static final String run_shell = "run_shell";
  public static final String ai_search = "ai_search";

  @Override
  public McpInitializeResult initialize(McpInitializeParams params, McpRequestContext ctx) throws McpRpcException {
    String protocolVersion = ctx.getProtocolVersion();
    String name = "Coder Server";
    String version = "1.15.0";
    McpInitializeResult result = McpInitializeResult.build(protocolVersion, name, version);
    return result;
  }

  @Override
  public void notificationsInitialized(McpRequestContext ctx) throws McpRpcException {

  }

  @Override
  public McpToolsListResult listTools(McpRequestContext ctx) throws McpRpcException {
    List<McpToolDescription> list = new ArrayList<>();
    McpToolDescription runShellDescription = new McpToolDescription();

    URL url = ResourceUtil.getResource("json/run_shell_inputSchema.json");
    String schema = FileUtil.readString(url);

    Map<String, Object> inputSchema = JsonUtils.parseToMap(schema, String.class, Object.class);

    runShellDescription.setName(run_shell).setTitle("run shell")
        .setDescription("执行 Linux Shell 命令并返回 stdout/stderr/exit_code,支持联网")
        //
        .setInputSchema(inputSchema);

    list.add(runShellDescription);

    url = ResourceUtil.getResource("json/run_python_inputSchema.json");
    schema = FileUtil.readString(url);

    inputSchema = JsonUtils.parseToMap(schema, String.class, Object.class);

    McpToolDescription runPythonDescription = new McpToolDescription();

    runPythonDescription.setName(run_python).setTitle("run python")
        .setDescription("在子进程中执行一段 Python 代码，返回 stdout/stderr/exit_code,支持联网")
        //
        .setInputSchema(inputSchema);

    list.add(runPythonDescription);

    McpToolDescription aiSearchDescription = new McpToolDescription();

    url = ResourceUtil.getResource("json/ai_search_inputSchema.json");
    schema = FileUtil.readString(url);

    inputSchema = JsonUtils.parseToMap(schema, String.class, Object.class);
    
    aiSearchDescription.setName(ai_search).setTitle("AI Search")
        .setDescription("智能搜索服务,将问题拆分多个关键字,根据关键字从google搜索引擎获取数据.大模型根据问题和获取的数据进行回答,输出回答后的文本")
        //
        .setInputSchema(inputSchema);

    list.add(aiSearchDescription);

    return new McpToolsListResult(list);
  }

  @Override
  public McpToolsCallResult callTool(McpToolsCallParams params, McpRequestContext ctx) throws McpRpcException {

    List<McpContent> contents = new ArrayList<>();

    String name = params.getName();
    if (run_shell.equals(name)) {
      Object command = params.getArguments().get("command");
      if (command instanceof String) {
        ProcessResult result = CmdInterpreterUtils.executeCmd((String) command);
        String text = JsonUtils.toSkipNullJson(result);
        McpContent content = McpContent.buildText(text);
        contents.add(content);
      }
    } else if (run_python.equals(name)) {
      Object code = params.getArguments().get("code");
      if (code instanceof String) {
        ProcessResult result = PythonInterpreterUtils.executeCode((String) code);
        String text = JsonUtils.toSkipNullJson(result);
        McpContent content = McpContent.buildText(text);
        contents.add(content);
      }

    } else if (ai_search.equals(name)) {
      Object question = params.getArguments().get("question");
      if (question instanceof String) {
        log.info("keyword:{}", question);
        
        String text = JsonUtils.toSkipNullJson("示例搜索结果");
        McpContent content = McpContent.buildText(text);
        contents.add(content);
      }
    }

    McpToolsCallResult mcpToolsCallResult = new McpToolsCallResult();
    mcpToolsCallResult.setContent(contents);
    return mcpToolsCallResult;
  }
}
