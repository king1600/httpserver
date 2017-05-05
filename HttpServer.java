import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;

public class HttpServer {
  private SocketServer server;
  public static int MAX_REQUEST_SIZE = 5*1024*1024;
  private Function<HttpRequest, HttpResponse> requestCallback;

  public HttpServer(int port) throws IOException {
    server = new SocketServer(port);
    server.onAccept(client -> {
      HttpRequest request = new HttpRequest(client);
      request.onComplete(() -> {
        if (requestCallback != null)
          return requestCallback.apply(request);
        return null;
      });
    });
  }

  public void start() throws IOException {
    System.out.println("Starting HTTP Server on :" + server.getPort());
    server.run();
  }

  public void onRequest(Function<HttpRequest, HttpResponse> callback) {
    requestCallback = callback;
  }
}