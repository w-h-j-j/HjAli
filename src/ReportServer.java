import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.text.SimpleDateFormat;

public class ReportServer {
    static Connection conn;
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/bike?useSSL=false&serverTimezone=Asia/Shanghai",
                "root", "Whj123456");
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/device/report", exchange -> {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String deviceId = extract(body, "deviceId");
                int battery = Integer.parseInt(extract(body, "battery"));
                int signal = Integer.parseInt(extract(body, "signal"));
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO device_data (device_id,battery,signal_strength,created_at) VALUES (?,?,?,?)");
                ps.setString(1, deviceId); ps.setInt(2, battery); ps.setInt(3, signal);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
                Statement clean = conn.createStatement();
                clean.execute("DELETE FROM device_data WHERE id NOT IN (SELECT id FROM (SELECT id FROM device_data ORDER BY id DESC LIMIT 20) t)");
                sendJson(exchange, 200, "{\"code\":0,\"msg\":\"ok\"}");
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"code\":1,\"msg\":\"" + e.getMessage() + "\"}");
            }
        });

        server.createContext("/api/device/history", exchange -> {
            try {
                String q = exchange.getRequestURI().getQuery();
                String deviceId = extractParam(q, "deviceId");
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT battery,signal_strength,created_at FROM device_data WHERE device_id='" + deviceId + "' ORDER BY id DESC LIMIT 20");
                StringBuilder sb = new StringBuilder("{\"code\":0,\"data\":[");
                SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss");
                while (rs.next()) {
                    String time = sdf.format(new java.util.Date(rs.getLong("created_at")));
                    sb.append("{\"battery\":").append(rs.getInt("battery"))
                            .append(",\"signal\":").append(rs.getInt("signal_strength"))
                            .append(",\"time\":\"").append(time).append("\"},");
                }
                if (sb.length() > 8 && sb.charAt(sb.length()-1)==',') sb.deleteCharAt(sb.length()-1);
                sb.append("]}");
                sendJson(exchange, 200, sb.toString());
            } catch (Exception e) {
                sendJson(exchange, 500, "{\"code\":1,\"msg\":\"" + e.getMessage() + "\"}");
            }
        });

        server.start();
        System.out.println("ReportServer started on 8080");
    }
    static String extract(String json, String key) {
        String p = "\""+key+"\""; int i = json.indexOf(p); if (i<0) return "";
        int c = json.indexOf(":",i); int s = c+1; while(json.charAt(s)==' ') s++;
        if (json.charAt(s)=='"') { int e = json.indexOf("\"",s+1); return json.substring(s+1,e); }
        int e = s; while(e<json.length() && json.charAt(e)!=',' && json.charAt(e)!='}') e++;
        return json.substring(s,e).trim();
    }
    static String extractParam(String q, String key) {
        for (String pair : q.split("&")) { String[] kv = pair.split("="); if (kv[0].equals(key)) return kv[1]; }
        return "";
    }
    static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, b.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
    }
}

