import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.*;

public class Proxy {

    /**
     * 初始主机——重定向主机
     */
    Map<String, String> redirectHostHostMap = new HashMap<>();
    /**
     * 初始主机——重定向后地址
     */
    Map<String, String> redirectHostAddrMap = new HashMap<>();

    Map<String, String> redirectAddrAddrMap = new HashMap<>();

    Set<String> forbidHost = new HashSet<>();

    Set<String> forbidUser = new HashSet<>();

    /**
     * 重定向主机
     *
     * @param oriHost 源主机
     * @return 重定向的主机
     */
    String redirectHost(String oriHost) {
        Set<String> keywordSet = redirectHostHostMap.keySet();
        for (String keyword : keywordSet) {
            if (oriHost.contains(keyword)) {
                System.out.println("源主机: " + oriHost);
                String redHost = redirectHostHostMap.get(oriHost);
                System.out.println("重定向主机Host: " + redHost);
                return redHost;
            }
        }
        return oriHost;
    }

    /**
     * 重定向地址
     * 根据redirectAddrMap中存放的Host——Address Map获取重定向地址
     *
     * @param oriHost 源主机
     * @return 重定向后的地址
     */
    String redirectAddr(String oriHost, String visitAddr) {
        Set<String> keywordSet = redirectAddrAddrMap.keySet();
        for (String keyword : keywordSet) {
            if (oriHost != null && keyword.contains(oriHost)) {
                //直接跳转
                if (visitAddr.equals(keyword)) {
                    return redirectAddrAddrMap.get(keyword);
                }
                if (visitAddr.contains(oriHost)) {
                    String[] temp = visitAddr.split(oriHost);  // 按空格分割
                    String redHost = redirectHostHostMap.get(oriHost);
                    String redAddr = temp[0] + redHost + temp[1];
                    return redAddr;
                }

            }
        }
        return visitAddr;
    }


    Map<String, String> parse(String header) {
        if (header.length() == 0) {
            return new HashMap<>();
        }
        String[] lines = header.split("\\n");
        String method = null;
        String visitAddr = null;
        String httpVersion = null;
        String hostName = null;
        String path = null;
        String fullPath = null;
        String portString = null;
        for (String line : lines) {
            if ((line.contains("GET") || line.contains("POST") || line.contains("CONNECT")) && method == null) {
                // 处理GET website HTTP/1.1
                String[] temp = line.split("\\s");  // 按空格分割
                method = temp[0];
                visitAddr = temp[1];
                httpVersion = temp[2];

                if (visitAddr.contains("http://") || visitAddr.contains("https://")) {

                    String[] temp1 = visitAddr.split(":");

                    if (temp1.length >= 3) {
                        portString = temp1[2];
                    }
                } else {

                    String[] temp1 = visitAddr.split(":");
                    if (temp1.length >= 2) {
                        portString = temp1[1];
                    }
                }

            } else if (line.contains("Host: ") && hostName == null) {
                String[] temp = line.split("\\s");
                hostName = temp[1];
                String[] temp1 = visitAddr.split("\\?");
                fullPath = temp1[0];
                String[] temp2 = fullPath.split(hostName);
                if (temp2.length > 1) {
                    path = temp2[1];
                } else {
                    path = "";
                }
                int colonIndex = hostName.indexOf(':');
                if (colonIndex != -1) {
                    hostName = hostName.substring(0, colonIndex);
                }
            }
        }

        Map<String, String> map = new HashMap<>();
        // 构造参数map
        map.put("method", method);
        map.put("visitAddr", visitAddr);
        map.put("httpVersion", httpVersion);
        map.put("host", hostName);
        map.put("path", path);
        map.put("fullPath", fullPath);


        if (portString == null) {
            map.put("port", "80");
        } else {
            map.put("port", portString);
        }
        return map;
    }

    /**
     * 构建重定向报文
     *
     * @param header
     * @param newHost
     * @param newAddr
     * @return
     */
    String RedirectHTTP(String header, String newHost, String newAddr) {
        if (header.length() == 0) {
            return new String();
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = header.split("\\n");
        for (String line : lines) {
            if ((line.contains("GET") || line.contains("POST") || line.contains("CONNECT"))) {
                // 处理GET website HTTP/1.1
                String[] temp = line.split("\\s");  // 按空格分割
                sb.append(temp[0] + " " + newAddr + " " + temp[2] + "\n");

            } else if (line.contains("Host: ")) {
                String[] temp = line.split("\\s");
                sb.append(temp[0] + newHost + "\n");

            } else if (line.contains("Referer: ")) {
                String[] temp = line.split("\\s");
                sb.append(temp[0] + newAddr + "\n");

            } else if (line.contains("Accept: ")) {
                String[] temp = line.split("\\s");
                sb.append(temp[0] + "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8" + "\n");

            } else {
                sb.append(line + "\n");
            }
        }
        return sb.toString();
    }

    void execute() throws IOException {
        // 监听指定的端口
        int port = 20201;
        ServerSocket server = new ServerSocket(port);
        // server将一直等待连接的到来
        System.out.println("代理服务器启动,监听端口：" + server.getLocalPort());


        while (true) {
            Socket clientSocket = server.accept();
            String UserIP = clientSocket.getInetAddress().getHostAddress();
            System.out.println("获取到一个连接！来自 " + UserIP);
            if (forbidUser.contains(UserIP)) {
                System.out.println("用户:" + UserIP + "已被禁止");
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                pw.println("Forbid User!");
                pw.close();
                clientSocket.close();
                continue;
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    String host = "", path = "", fullPath = "";
                    try {
                        // 解析header
                        InputStreamReader r = new InputStreamReader(clientSocket.getInputStream());
                        OutputStream clientOutput = clientSocket.getOutputStream();

                        BufferedReader br = new BufferedReader(r);
                        String readLine = br.readLine();

                        StringBuilder headerBuilder = new StringBuilder();

                        while (readLine != null && !readLine.equals("")) {
                            headerBuilder.append(readLine).append("\n");
                            readLine = br.readLine();
                        }

                        if (headerBuilder.toString().length() == 0) {
                            System.out.println("HTTP头为空！");
                            return;
                        }
                        String header = headerBuilder.toString();

                        System.out.println("\n-----------------");
                        System.out.print("代理服务器获取的HTTP头： 长度" + headerBuilder.toString().length() + "\n" + headerBuilder.toString());
                        System.out.println("-----------------");

                        // 在输入流结束之后判断


                        Map<String, String> map = parse(headerBuilder.toString());

                        host = map.get("host"); // host
                        path = map.get("path");
                        fullPath = map.get("fullPath");
                        // 端口
                        int visitPort = Integer.parseInt(map.getOrDefault("port", "80"));
                        // 访问的网站
                        String visitAddr = map.get("visitAddr");
                        // method
                        String method = map.getOrDefault("method", "GET");
                        if (forbidHost.contains(host)) {
                            System.out.println("访问了禁止访问的网站！");
                            PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                            pw.println("Website: " + visitAddr + " is forbid to visit because " + host + "  is a forbid host");
                            pw.close();
                            clientSocket.close();
                            return;

                        }
                        System.out.println("初始地址：" + visitAddr);
                        String redirectHost = redirectHost(host);
                        if (!host.equals(redirectHost)) {
                            visitAddr = redirectAddr(host, visitAddr);
                            host = redirectHost;
                            String[] temp1 = visitAddr.split("\\?");
                            fullPath = temp1[0];
                            String[] temp2 = fullPath.split(host);
                            if (temp2.length > 1) {
                                path = temp2[1];
                            } else {
                                path = "";
                            }
                            header = RedirectHTTP(header, host, visitAddr);
                        }



                        File cacheFile = new File("cache/" + (host + path).replace('/', '_').replace(':', '+') + ".cache");
                        boolean useCache = false;   
                        boolean existCache = cacheFile.exists() && cacheFile.length() != 0;
                        String lastModified = "Thu, 01 Jul 1970 20:00:00 GMT";

                        if (existCache == true) {
                            System.out.println(visitAddr + "存在本地缓存文件");
                            // 获得修改时间
                            long time = cacheFile.lastModified();
                            SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
                            Calendar cal = Calendar.getInstance();
                            cal.setTimeInMillis(time);
                            cal.set(Calendar.HOUR, -7);
                            cal.setTimeZone(TimeZone.getTimeZone("GMT"));
                            lastModified = formatter.format(cal.getTime());
                            System.out.println("缓存建立时间：" + cal.getTime());
                        }


                        Socket proxySocket = new Socket(host, 80);
                        System.out.println("代理套接字已建立!:" + proxySocket);
                        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(proxySocket.getOutputStream()));


                        StringBuffer requestBuffer = new StringBuffer();
                        requestBuffer.append(header);
                        if(existCache==true)
                            requestBuffer.append("If-Modified-Since: ").append(lastModified).append("\n");


                        writer.write(requestBuffer.append("\n").toString()); // 发送报文
                        writer.flush();

                        System.out.println("\n-----------------");
                        System.out.print("代理服务器转发报文：\n" + requestBuffer.toString());
                        System.out.println("-----------------");




                        // 从远程服务器获得输入的输入流
                        BufferedInputStream remoteInputStream = new BufferedInputStream(proxySocket.getInputStream());
                        System.out.println("获取来自：" + host + "的输入流");

                        // 先使用一个小字节缓存获得头部
                        byte[] tempBytes = new byte[20];
                        int len = remoteInputStream.read(tempBytes);
                        String res = new String(tempBytes);
                        useCache = (res.contains("304") || (res.contains("200"))) && cacheFile.length() != 0;

                        System.out.println("HTTP 状态：" + res + "\n-----------------");

                        if (useCache == true) {
                            // 用缓存
                            // 这是向浏览器输出的输出流
                            System.out.println(visitAddr + " 本地缓存尚未过期，使用缓存");
                            System.out.println(visitAddr + " 正在使用缓存加载,缓存长度" + cacheFile.length());
                            // 建立文件读写
                            FileInputStream fileInputStream = new FileInputStream(cacheFile);
                            int bufferLength = 1;
                            byte[] buffer = new byte[bufferLength];

                            while (true) {
                                int count = fileInputStream.read(buffer);
                                //System.out.println(count + "从缓存中加载网页..." + visitAddr);
                                if (count == -1) {
                                    System.out.println("从缓存中加载完成！");
                                    break;
                                }
                                clientOutput.write(buffer);
                            }
                            clientOutput.flush();
                        } else {
                            System.out.println(visitAddr + " 本地缓存过期或不可用，不使用缓存");
                            clientOutput.write(tempBytes);
                        }

                        FileOutputStream fileOutputStream =
                                new FileOutputStream(
                                        ("cache/" + (host + path).replace('/', '_')
                                                .replace(':', '+') + ".cache"));
                        if (!useCache) {
                            fileOutputStream.write(tempBytes);
                        }
                        int bufferLength = 1;
                        byte[] buffer = new byte[bufferLength];
                        while (true) {
                            int count = remoteInputStream.read(buffer);
                            if (count == -1) {
                                break;
                            }
                            if (!useCache) {
                                clientOutput.write(buffer);
                                fileOutputStream.write(buffer);

                            }
                        }
                        fileOutputStream.flush();   // 输出到文件
                        fileOutputStream.close();   // 关闭文件流

                        clientOutput.flush();   // 输出到浏览器
                        writer.close();

                        remoteInputStream.close();
                        System.out.println(host + "代理已经完成！");

                        proxySocket.close();    // 关闭连接远程服务器的socket
                        clientSocket.close();// 关闭浏览器与程序的socket

                    } catch (IOException e) {
                        System.out.println(host + "出现异常！");
                        e.printStackTrace();


                    } catch (StringIndexOutOfBoundsException e) {
                        System.out.println(host + "返回报文出现异常！");
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public static void main(String[] args) throws IOException {


        Proxy proxy = new Proxy();
        //proxy.forbidUser.add("127.0.0.1");
        //proxy.forbidHost.add("www.4399.com");
//        proxy.forbidHost.add("api2.firefoxchina.cn");
//
        //proxy.forbidHost.add("acm.hit.edu.cn");
        proxy.redirectHostHostMap.put("jwts.hit.edu.cn", "today.hit.edu.cn");
        proxy.redirectHostAddrMap.put("jwts.hit.edu.cn", "http://today.hit.edu.cn/");
        proxy.redirectAddrAddrMap.put("http://jwts.hit.edu.cn/loginLdapQian/", "http://today.hit.edu.cn/");

        proxy.execute();
    }

}

