package com.example.aries;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 只给桌宠用的本机静态资源服务：WebView 加载 ES module 必须走 http 源（file:// 会被当成跨域），
 * 而这个项目没有 androidx.webkit 依赖，所以自己起一个只绑 127.0.0.1 的小服务。
 *
 * 三条硬规矩：只监听回环（局域网别人连不上）、只服务给定根目录内的文件（目录穿越一律拒）、
 * 按扩展名给出正确 Content-Type 且脚本带 charset=utf-8（否则 import 直接失败）。
 *
 * 用法（安卓侧）：
 *   PetAssetServer server = new PetAssetServer(rel -> readAsset(rel));   // rel 形如 coopanion/overlay.html
 *   int port = server.start();
 *   webView.loadUrl("http://127.0.0.1:" + port + "/coopanion/overlay.html");
 *   ... onDestroy: server.stop();
 */
public final class PetAssetServer {

    /** 资源来源。返回 null 表示没有这个文件。 */
    public interface Source {
        byte[] read(String relativePath);
    }

    private static final Map<String, String> MIME = new HashMap<String, String>();
    static {
        MIME.put("html", "text/html; charset=utf-8");
        MIME.put("htm", "text/html; charset=utf-8");
        MIME.put("js", "text/javascript; charset=utf-8");
        MIME.put("mjs", "text/javascript; charset=utf-8");
        MIME.put("css", "text/css; charset=utf-8");
        MIME.put("json", "application/json; charset=utf-8");
        MIME.put("png", "image/png");
        MIME.put("jpg", "image/jpeg");
        MIME.put("jpeg", "image/jpeg");
        MIME.put("svg", "image/svg+xml");
        MIME.put("wav", "audio/wav");
        MIME.put("ogg", "audio/ogg");
        MIME.put("woff2", "font/woff2");
    }

    private static final byte[] EMPTY = new byte[0];

    private final Source source;
    private final InetAddress loopback;
    private volatile ServerSocket socket;
    private ExecutorService pool;

    public PetAssetServer(Source source) { this(source, loopbackAddress()); }

    public PetAssetServer(Source source, InetAddress bindAddress) {
        this.source = source;
        this.loopback = bindAddress;
    }

    private static InetAddress loopbackAddress() {
        try { return InetAddress.getByAddress(new byte[]{127, 0, 0, 1}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** 起服务，返回实际端口。 */
    public int start() throws IOException {
        ServerSocket s = new ServerSocket();
        s.setReuseAddress(true);
        s.bind(new InetSocketAddress(loopback, 0), 128);
        socket = s;
        final ExecutorService p = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "pet-asset");
                t.setDaemon(true);
                return t;
            }
        });
        pool = p;
        p.execute(() -> acceptLoop(s, p));
        return s.getLocalPort();
    }

    /** 监听线程只认传进来的那个池子：stop() 把字段置空也不会把这里打崩。 */
    private void acceptLoop(ServerSocket s, ExecutorService p) {
        while (!s.isClosed()) {
            final Socket c;
            try {
                c = s.accept();
            } catch (IOException e) {
                return;                                    // stop() 关掉了监听，正常退出
            }
            if (s.isClosed()) { closeQuietly(c); return; }
            try {
                p.execute(() -> serve(c));
            } catch (RuntimeException e) {
                closeQuietly(c);                             // 池子已关，收掉连接就走
                return;
            }
        }
    }

    private static void closeQuietly(Socket c) {
        try { c.close(); } catch (IOException ignored) { }
    }

    private void serve(Socket c) {
        try {
            c.setTcpNoDelay(true);
            RequestLine rl = readRequest(c.getInputStream());
            byte[] body = rl == null ? null : resolve(rl.path);
            int status;
            byte[] payload;
            if (rl == null) { status = 400; payload = EMPTY; }
            else if (rl.rejected) { status = 403; payload = EMPTY; }
            else if (body == null) { status = 404; payload = EMPTY; }
            else { status = 200; payload = body; }
            String type = status == 200 ? typeOf(rl.path) : "text/plain; charset=utf-8";
            write(c.getOutputStream(), status, type, payload);
        } catch (IOException ignored) {
            // WebView 中途取消请求很常见，这里不值得吵
        } finally {
            try { c.close(); } catch (IOException ignored) { }
        }
    }

    private static class RequestLine {
        String path;
        boolean rejected;
    }

    /** 只取请求行，其余请求头读完丢掉；不需要支持 POST。 */
    private static RequestLine readRequest(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null) return null;
        drainHeaders(in);
        String[] parts = line.split(" ");
        if (parts.length < 2 || !("GET".equals(parts[0]) || "HEAD".equals(parts[0]))) return null;
        RequestLine r = new RequestLine();
        String target = parts[1];
        int q = target.indexOf('?');
        if (q >= 0) target = target.substring(0, q);
        String decoded;
        try { decoded = URLDecoder.decode(target, "UTF-8"); } catch (Exception e) { r.rejected = true; return r; }
        if (decoded.indexOf('\0') >= 0) { r.rejected = true; return r; }
        while (decoded.startsWith("/")) decoded = decoded.substring(1);
        for (String seg : decoded.split("/")) {
            if (seg.equals("..") || seg.indexOf('\\') >= 0) { r.rejected = true; return r; }
        }
        if (decoded.length() == 0) { r.rejected = true; return r; }
        r.path = decoded;
        return r;
    }

    private static void drainHeaders(InputStream in) throws IOException {
        int last = -1, cur;
        int reads = 0;
        while (reads++ < 20000) {
            cur = in.read();
            if (cur < 0) return;
            if (last == '\r' && cur == '\n') return;   // 空行：头结束
            last = cur;
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') bos.write(c);
        }
        if (c < 0 && bos.size() == 0) return null;
        return new String(bos.toByteArray(), "UTF-8");
    }

    private byte[] resolve(String path) {
        try {
            return source.read(path);
        } catch (Exception e) {
            return null;
        }
    }

    private static String typeOf(String path) {
        int dot = path.lastIndexOf('.');
        String ext = dot < 0 ? "" : path.substring(dot + 1).toLowerCase();
        String t = MIME.get(ext);
        return t == null ? "application/octet-stream" : t;
    }

    private static void write(OutputStream out, int status, String type, byte[] body) throws IOException {
        String reason = status == 200 ? "OK" : status == 400 ? "Bad Request" : status == 403 ? "Forbidden" : "Not Found";
        String head = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes("ASCII"));
        out.write(body);
        out.flush();
    }

    public InetSocketAddress boundAddress() {
        ServerSocket s = socket;
        return s == null ? new InetSocketAddress(0) : (InetSocketAddress) s.getLocalSocketAddress();
    }

    public int port() {
        ServerSocket s = socket;
        return s == null ? 0 : s.getLocalPort();
    }

    /** 关监听并停掉工作线程；正在传输的连接由对端超时收尾。 */
    public void stop() throws IOException {
        ServerSocket s = socket;
        socket = null;
        if (s != null) s.close();
        ExecutorService p = pool;
        pool = null;
        if (p != null) p.shutdownNow();
    }
}
