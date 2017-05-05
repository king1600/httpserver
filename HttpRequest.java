import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HttpRequest {
  private String path;
  private String method;
  private String version;
  private ParseState state;
  private ClientSocket client;
  private StringBuilder bodyStream;
  private boolean completed = false;
  private Map<String, String> params;
  private Map<String, String> headers;
  private Supplier<HttpResponse> callback;
  private static enum ParseState {METHOD, HEADERS, BODY, EOF};

  public HttpRequest(final ClientSocket client) {
    this.client = client;
    params = new HashMap<>();
    headers = new HashMap<>();
    state = ParseState.METHOD;
    bodyStream = new StringBuilder();
    this.client.setRead(data -> {
      parse(data);
    });
  }

  private void parse(byte[] data) {
    if (completed) return;
    String line;
    String[] parts = new String(data).split("\r\n");
    for (int i = 0; i < parts.length; i++) {
      line = parts[i];
      System.out.println("Line: " + line);

      if (state == ParseState.METHOD) {
        String[] sub = line.split(" ");
        String[] p   = sub[1].split("\\?");
        method = sub[0];
        path   = p[0];
        if (p.length > 1) parseParams(p[1]);
        version = sub[2].split("\\/")[1];
        state = ParseState.HEADERS;

      } else if (state == ParseState.HEADERS) {
        if (line.trim().length() < 1) {
          state = ParseState.BODY;
          continue;
        }
        String[] sub = line.split("\\:");
        headers.put(sub[0], sub[1].substring(1));

      } else if (state == ParseState.BODY) {
        if (line.trim().length() < 1) {
          state = ParseState.EOF;
          continue;
        }
        if (headers.containsKey("Content-Length")) {
          bodyStream.append(line);
          int size = Integer.parseInt(headers.get("Content-Length"));
          if (bodyStream.length() >= size)
            state = ParseState.EOF;
        } else if (headers.containsKey("Transfer-Encoding")) {
          try {
            int remaining = (int)((short)Integer.parseInt(line,16));
            if (remaining < 1) state = ParseState.EOF;
          } catch (Exception e) {
            bodyStream.append(line);
          }
        }

      } else if (state == ParseState.EOF) {
        complete();
        return;
      }
    }
    if (state == ParseState.EOF) complete();
  }

  private void parseParams(String paramString) {
    String[] parts = paramString.split("\\&");
    for (int i = 0; i < parts.length; i++) {
      String[] p = parts[i].split("\\=");
      params.put(p[0], p[1]);
    }
  }

  private void complete() {
    completed = true;
    HttpResponse resp = null;
    if (callback != null) resp = callback.get();
    client.setWrite(() -> {
      client.close();
    });
    String data = "HTTP/1.1 200 OK\r\nHost: localhost:8080\r\n\r\n";
    client.write(data.getBytes());
  }

  public InetAddress getAddress() {
    return ((InetSocketAddress)client.getChannel()
      .socket().getRemoteSocketAddress()).getAddress();
  }

  public String getBody() {
    return (completed) ? bodyStream.toString() : null;
  }

  public String getPath() {
    return path;
  }

  public String getMethod() {
    return method;
  }
  
  public String getVersion() {
    return version;
  }

  public Map<String, String> getParams() {
    return params;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void onComplete(Supplier<HttpResponse> cb) {
    callback = cb;
  }
}