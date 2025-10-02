package com.litongjava.kit.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.litongjava.kit.utils.CmdInterpreterUtils;
import com.litongjava.kit.utils.PythonInterpreterUtils;
import com.litongjava.mcp.model.McpContent;
import com.litongjava.mcp.model.McpServerInfo;
import com.litongjava.mcp.server.McpToolRegistry;
import com.litongjava.mcp.server.RegisteredTool;
import com.litongjava.model.result.ResultVo;
import com.litongjava.tio.utils.commandline.ProcessResult;
import com.litongjava.tio.utils.json.JsonUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 你的具体服务器：只需提供名称/版本。 如需添加或覆盖工具，可覆写 registerAdditionalTools() 或单独覆盖某个 handler。
 */
@Slf4j
public class McpCoderServer extends McpServer {

  public McpCoderServer() {
    super();
  }

  @Override
  protected McpServerInfo getMcpServerInfo() {
    return new McpServerInfo("Coder Server", "1.15.0");
  }

  @Override
  protected void registerTools() {
    // run_shell
    RegisteredTool runShell = McpToolRegistry.builder("run_shell").title("run shell")
        .description("执行 Linux Shell 命令并返回 stdout/stderr/exit_code,支持联网").addStringProperty("command", "Command", true)
        .addIntegerProperty("timeout", "Timeout", 30, false).addNullableProperty("cwd", "Cwd", "string", null, false)
        .addNullableObjectProperty("env", "Env", true, null, false).handler(this::handleRunShell).build();
    registry.register(runShell);

    // run_python
    RegisteredTool runPython = McpToolRegistry.builder("run_python").title("run python")
        .description("在子进程中执行一段 Python 代码，返回 stdout/stderr/exit_code,支持联网").addStringProperty("code", "Code", true)
        .addIntegerProperty("timeout", "Timeout", 30, false)
        .addNullableProperty("stdin", "Stdin", "string", null, false)
        .addBooleanProperty("use_isolated", "Use Isolated", true, false).handler(this::handleRunPython).build();
    registry.register(runPython);

    // ai_search（留一个简单的缺省实现，子类可覆盖 handler 或重载注册）
    RegisteredTool aiSearch = McpToolRegistry.builder("ai_search").title("AI Search")
        .description("智能搜索服务,将问题拆分多个关键字,根据关键字从google搜索引擎获取数据.大模型根据问题和获取的数据进行回答,输出回答后的文本")
        .addStringProperty("question", "Question", true).handler(this::handleAiSearch).build();
    registry.register(aiSearch);

  }

  /** 公共：run_shell 处理逻辑 */
  protected List<McpContent> handleRunShell(Map<String, Object> args) {
    List<McpContent> contents = new ArrayList<>();
    Object command = args.get("command");
    if (command instanceof String) {
      ProcessResult result = CmdInterpreterUtils.executeCmd((String) command);
      String text = JsonUtils.toSkipNullJson(result);
      contents.add(McpContent.buildText(text));
    } else {
      String json = JsonUtils.toSkipNullJson(ResultVo.fail("command must be a string"));
      contents.add(McpContent.buildText(json));
    }
    return contents;
  }

  /** 公共：run_python 处理逻辑 */
  protected List<McpContent> handleRunPython(Map<String, Object> args) {
    List<McpContent> contents = new ArrayList<>();
    Object code = args.get("code");
    if (code instanceof String) {
      ProcessResult result = PythonInterpreterUtils.executeCode((String) code);
      String text = JsonUtils.toSkipNullJson(result);
      contents.add(McpContent.buildText(text));
    } else {
      String json = JsonUtils.toSkipNullJson(ResultVo.fail("code must be a string"));
      contents.add(McpContent.buildText(json));
    }
    return contents;
  }

  /** 公共：ai_search 的缺省实现（示例占位，可被子类替换） */
  protected List<McpContent> handleAiSearch(Map<String, Object> args) {
    List<McpContent> contents = new ArrayList<>();
    Object question = args.get("question");
    if (question instanceof String) {
      log.info("keyword: {}", question);
      String text = JsonUtils.toSkipNullJson("示例搜索结果");
      contents.add(McpContent.buildText(text));
    } else {
      String json = JsonUtils.toSkipNullJson(ResultVo.fail("question must be a string"));
      contents.add(McpContent.buildText(json));
    }
    return contents;
  }
}
