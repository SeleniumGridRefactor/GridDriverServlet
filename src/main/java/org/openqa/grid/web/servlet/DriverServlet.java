/*
Copyright 2011 Selenium committers
Copyright 2011 Software Freedom Conservancy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.openqa.grid.web.servlet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openqa.grid.internal.ExternalSessionKey;
import org.openqa.grid.internal.HubRegistryInterface;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.web.servlet.handler.RequestHandler;
import org.openqa.grid.web.servlet.handler.SeleniumBasedRequest;
import org.openqa.grid.web.servlet.handler.WebDriverRequest;
import org.openqa.selenium.remote.ErrorCodes;

import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * entry point for all communication request sent by the clients to the remotes
 * managed by the grid.
 *
 * Runs on the socketListener threads of the servlet container
 */
public class DriverServlet extends RegistryBasedServlet {

  private static final long serialVersionUID = -1693540182205547227L;

  @SuppressWarnings("UnusedDeclaration")
  public DriverServlet() {
    super();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    process(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    process(request, response);
  }

  @Override
  protected void doDelete(HttpServletRequest request,
      HttpServletResponse response) throws ServletException, IOException {
    process(request, response);
  }

  protected void process(HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    RequestHandler req = null;
    SeleniumBasedRequest r = null;
    try {
      r = SeleniumBasedRequest.createFromRequest(request, getRegistry());
      req = new RequestHandler(r, response, getRegistry());
      req.process();
      getRegistry().getGridMonitorMBean().handleDriverRequest(request.toString(), req.getResponse().toString());
    } catch (Throwable e) {
      if (r instanceof WebDriverRequest && !response.isCommitted()) {
        // http://code.google.com/p/selenium/wiki/JsonWireProtocol#Error_Handling
        response.reset();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(500);

        JsonObject resp = new JsonObject();

        final ExternalSessionKey serverSession = req.getServerSession();
        resp.addProperty("sessionId",
            serverSession != null ? serverSession.getKey() : null);
        resp.addProperty("status", ErrorCodes.UNHANDLED_ERROR);
        JsonObject value = new JsonObject();
        value.addProperty("message", e.getMessage());
        value.addProperty("class", e.getClass().getCanonicalName());

        JsonArray stacktrace = new JsonArray();
        for (StackTraceElement ste : e.getStackTrace()) {
          JsonObject st = new JsonObject();
          st.addProperty("fileName", ste.getFileName());
          st.addProperty("className", ste.getClassName());
          st.addProperty("methodName", ste.getMethodName());
          st.addProperty("lineNumber", ste.getLineNumber());
          stacktrace.add(st);
        }
        value.add("stackTrace", stacktrace);
        resp.add("value", value);

        String json = new Gson().toJson(resp);
        

        byte[] bytes = json.getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(bytes);
        try {
          response.setHeader("Content-Length", Integer.toString(bytes.length));
          ByteStreams.copy(in, response.getOutputStream());
        } finally {
          in.close();
          response.flushBuffer();
        }
      } else {
        throw (new IOException(e));
      }
    }

  }

@Override
protected Registry getRegistry() {
	return (Registry) super.getRegistry();
}

}
