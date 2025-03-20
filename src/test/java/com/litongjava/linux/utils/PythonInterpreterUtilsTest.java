package com.litongjava.linux.utils;

import org.junit.Test;

import com.litongjava.linux.vo.PythonResult;
import com.litongjava.tio.utils.json.JsonUtils;

public class PythonInterpreterUtilsTest {

  @Test
  public void executeScript() throws Exception {
    // 假设你的脚本文件名叫 "myscript.py"
    String scriptPath = "myscript.py";
    PythonResult result = PythonInterpreterUtils.executeScript(scriptPath);
    System.out.println(JsonUtils.toJson(result));
  }
}
