package com.litongjava.kit.inteceptor;

import java.lang.reflect.Method;

import com.litongjava.tio.boot.http.controller.ControllerInterceptor;
import com.litongjava.tio.http.common.HttpRequest;
import com.litongjava.tio.http.common.HttpResponse;

public class MyControllerInteceptor implements ControllerInterceptor {

  /**
   * 在 Action 方法执行前触发
   * @param request 当前 HttpRequest 对象
   * @param actionMethod 即将执行的控制器方法
   * @return 如果返回非 null 的 HttpResponse，将直接返回该响应，中断后续方法执行
   */
  @Override
  public HttpResponse before(HttpRequest request, Method actionMethod) {
    return null;
  }

  /**
   * 在 Action 方法执行后触发
   * @param request 当前 HttpRequest 对象
   * @param targetController 控制器实例
   * @param actionMethod 执行的控制器方法
   * @param actionReturnValue 方法执行结果
   * @return Action返回的最终结果，通常可对 actionReturnValue 进行二次加工
   */
  @Override
  public Object after(HttpRequest request, Object targetController, Method actionMethod, Object actionReturnValue) {
    return actionReturnValue;
  }
}
