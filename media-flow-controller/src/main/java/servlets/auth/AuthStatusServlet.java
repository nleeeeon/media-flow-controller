// AuthStatusServlet.java
package servlets.auth;
import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
@WebServlet("/auth/status")
public class AuthStatusServlet extends HttpServlet {
  @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("application/json; charset=UTF-8");
    HttpSession s = req.getSession(false);
    String sid = s==null? null : s.getId();
    String at  = s==null? null : (String)s.getAttribute("yt_access_token");
    Long   exp = s==null? null : (Long)s.getAttribute("yt_expires_at");
    String rt  = s==null? null : (String)s.getAttribute("yt_refresh_token");
    long now = System.currentTimeMillis();
    boolean valid = (at!=null) && (exp==null || exp>now);
    resp.getWriter().write("{\"sessionId\":\""+(sid==null?"":sid)+"\",\"has_access_token\":"+(at!=null)
      +",\"expires_at\":"+(exp==null?0:exp)+",\"valid_now\":"+valid+",\"has_refresh_token\":"+(rt!=null)+"}");
  }
}
