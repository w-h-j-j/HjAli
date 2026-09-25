
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;

public class WsServer extends WebSocketServer {
    public WsServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("新连接: " + conn.getRemoteSocketAddress());
        conn.send("欢迎，服务器已连接！");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("连接关闭: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("收到消息: " + message);
        conn.send("服务器回复: " + message);
        broadcast("广播给所有人: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
        System.out.printf("WebSocket onError : " + ex.getMessage());
    }

    @Override
    public void onStart() {
        setConnectionLostTimeout(0);
        System.out.println("WebSocket 服务器已启动，端口 8081");
    }

    public static void main(String[] args) {
        new WsServer(8081).start();
    }
}

