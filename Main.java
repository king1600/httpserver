public class Main {

  public static void main(String[] args) {
    try {
      HttpServer server = new HttpServer(8080);
      server.onRequest(req -> {
        return new HttpResponse();
      });
      server.start();
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    System.exit(0);
  }

}