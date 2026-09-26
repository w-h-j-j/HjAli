
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;

public class WsServer extends WebSocketServer {

    /** 客户端角色：未注册 */
    private static final String ROLE_UNKNOWN = "UNKNOWN";
    /** 客户端角色：手机 App */
    private static final String ROLE_APP = "APP";
    /** 客户端角色：ESP32 开发板 */
    private static final String ROLE_ESP32 = "ESP32";

    public WsServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.setAttachment(ROLE_UNKNOWN);
        System.out.println("新连接: " + conn.getRemoteSocketAddress() + "，等待首条消息注册角色(APP/ESP32)...");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("连接关闭: " + conn.getRemoteSocketAddress() + " 角色: " + roleOf(conn));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String role = conn.getAttachment();

        // 首条消息注册角色：约定格式 {"role":"APP"} 或 {"role":"ESP32"}，注册包本身不转发
        if (ROLE_UNKNOWN.equals(role)) {
            String reg = message.trim().toLowerCase();
            if (reg.contains("\"role\":\"app\"")) {
                conn.setAttachment(ROLE_APP);
                System.out.println("注册为手机App: " + conn.getRemoteSocketAddress());
            } else if (reg.contains("\"role\":\"esp32\"")) {
                conn.setAttachment(ROLE_ESP32);
                System.out.println("注册为ESP32: " + conn.getRemoteSocketAddress());
            } else {
                System.out.println("未注册连接的消息被忽略: " + message);
            }
            return;
        }

        // 已注册连接：消息不做任何修改，单向对转转发
        int sent;
        if (ROLE_APP.equals(role)) {
            // 规则2：手机App的消息原样转发到ESP32客户端
            sent = forwardTo(ROLE_ESP32, message);
            System.out.println("App -> ESP32 转发 " + sent + " 条: " + message);
        } else if (ROLE_ESP32.equals(role)) {
            // 规则3：ESP32客户端的消息原样转发到手机App客户端
            sent = forwardTo(ROLE_APP, message);
            System.out.println("ESP32 -> App 转发 " + sent + " 条: " + message);
        } else {
            System.out.println("未知角色，消息被忽略: " + message);
        }
    }

    /** 将消息原样发送给指定角色的所有在线连接，返回成功发送数 */
    private int forwardTo(String targetRole, String message) {
        int count = 0;
        for (WebSocket conn : getConnections()) {
            try {
                if (conn.isOpen() && targetRole.equals(conn.getAttachment())) {
                    conn.send(message);
                    count++;
                }
            } catch (Exception e) {
                System.out.println("转发失败: " + e.getMessage());
            }
        }
        return count;
    }

    private String roleOf(WebSocket conn) {
        String role = conn.getAttachment();
        return role == null ? ROLE_UNKNOWN : role;
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

