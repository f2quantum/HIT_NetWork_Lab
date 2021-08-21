package SR;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;

/**
 * GBNHost。
 */
public class SR {
    /**
     * 窗口长度。
     */
    protected int WINDOW_SIZE;

    /**
     * 分组个数。
     */
    protected int DATA_NUMBER;

    /**
     * 超时时间，秒。
     */
    protected int TIMEOUT;

    /**
     * 这个主机的名称。
     */
    protected String hostName;

    /*下面的是发送数据相关的变量*/

    /**
     * 下一个发送的分组。
     */
    protected int nextSeq = 1;

    /**
     * 当前窗口起始位置。
     */
    protected int base = 1;

    /**
     * 分组发送的目标地址。
     */
    protected InetAddress destAddress;

    /**
     * 发送分组的目标端口，初始化为80
     */
    protected int destPort = 80;

    /*接收数据相关*/

    /**
     * 期望收到的分组序列号。
     */
    protected int expectedSeq = 1;

    protected int lastSave = 0;
    /**
     * 作为发送方时发送过的分组。
     */
    private Set<Integer> senderSentSet = new HashSet<>();

    /**
     * 作为发送方时收到的ACK。
     */
    private Set<Integer> senderReceivedACKSet = new HashSet<>();

    /**
     * 作为接收方时收到的分组，用来作为缓存。
     */
    private Set<Integer> receiverReceivedSet = new HashSet<>();
    /*Sockets*/

    /**
     * 发送数据使用的socket。
     */
    protected DatagramSocket sendSocket;

    /**
     * 接收分组使用的socket。
     */
    protected DatagramSocket receiveSocket;

    /**
     * 标志是否传送文件
     */


    public SR(int RECEIVE_PORT, int WINDOW_SIZE, int DATA_NUMBER, int TIMEOUT, String name) throws IOException {
        this.WINDOW_SIZE = WINDOW_SIZE;
        this.DATA_NUMBER = DATA_NUMBER;
        this.TIMEOUT = TIMEOUT;
        this.hostName = name;

        sendSocket = new DatagramSocket();
        receiveSocket = new DatagramSocket(RECEIVE_PORT);
        destAddress = InetAddress.getLocalHost();
    }


    /**
     * 发送报文
     *
     * @throws IOException
     */
    public void send() throws IOException {
        while (true) {
            // 发送分组循环
            while (nextSeq < base + WINDOW_SIZE && nextSeq <= DATA_NUMBER) {
                // 模拟数据丢失
                if (nextSeq % 5 == 0||nextSeq == 7) {
                    System.out.println(hostName + "模拟丢失报文：Seq = " + nextSeq);
                    nextSeq++;
                    continue;
                }

                String sendDataLabel = hostName + ": Sending to port " + destPort + ", Seq = " + nextSeq;

                byte[] datagram = sendDataLabel.getBytes();

                senderSentSet.add(nextSeq);   // 加入已发送set

                DatagramPacket datagramPacket = new DatagramPacket(datagram, datagram.length, destAddress, destPort);
                sendSocket.send(datagramPacket);

                System.out.println(hostName + "发送到" + destPort + "端口， Seq = " + nextSeq);
                nextSeq = nextSeq + 1;
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            // 用尽窗口后开始接收ACK
            byte[] bytes = new byte[4096];
            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);
            sendSocket.setSoTimeout(1000 * TIMEOUT);
            try {
                sendSocket.receive(datagramPacket);
            } catch (SocketTimeoutException ex) {
                System.out.println(hostName + " 等待ACK:Seq=" + base + "超时");
                senderSentSet.remove(base);
                timeOut();
                continue;
            }
            // 转换成String
            String fromServer = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
            // 解析出ACK编号
            int ack = Integer.parseInt(fromServer.substring(fromServer.indexOf("ACK: ") + "ACK: ".length()).trim());
            senderReceivedACKSet.add(ack);    // 加入已收到set
            if (base == ack) {
                // 向右滑动
                while (senderReceivedACKSet.contains(base)) {
                    base++;
                }
                System.out.println(hostName + " 当前窗口 [" + base + "," + (base + WINDOW_SIZE - 1) + "]");
            }

            System.out.println(hostName + "收到了 ACK: " + ack);

            // 发送完了，此时base会滑到右边多一格
            if (base == DATA_NUMBER + 1) {
                System.out.println(hostName + "发送完毕，发送方收到了全部的ACK");
                return;
            }
        }
    }


    public void receive() throws IOException {

        int rcvBase = 1;
        while (true) {
            byte[] receivedData = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receivedData, receivedData.length);
            receiveSocket.setSoTimeout(1000 * TIMEOUT);
            try {
                receiveSocket.receive(receivePacket);
            } catch (SocketTimeoutException ex) {
                System.out.println(hostName + " 正在等待分组的到来");
                continue;
            }

            // 收到的数据
            String received = new String(receivedData, 0, receivePacket.getLength());

            int seqIndex = received.indexOf("Seq = ");
            if (seqIndex == -1) {
                System.out.println(hostName + " 收到错误的数据");
                // 仍发送之前的ACK
                continue;
            }

            int seq = Integer.parseInt(received.substring(seqIndex + "Seq = ".length()).trim());
            if (seq >= rcvBase && seq <= rcvBase + WINDOW_SIZE - 1) {
                //收到了接受窗口里存在的分组
                receiverReceivedSet.add(seq);
                System.out.println(hostName + "收到一个接收方窗口内的分组，Seq = " + seq + "已确认");
                sendACK(seq, receivePacket.getAddress(), receivePacket.getPort());
                if (seq == rcvBase) {
                    // 收到这个分组后可以开始滑动
                    while (receiverReceivedSet.contains(rcvBase)) {
                        rcvBase++;
                    }
                    if (rcvBase == DATA_NUMBER + 1) {
                        // 停止计时器
                        System.out.println(hostName + "接受完毕，发送方收到了全部的数据");
                        return;
                    }
                }

            }  else {
                // 这个分组序列号太大，不在窗口内，应该舍弃
                System.out.println(hostName + "收到一个不在窗口内的分组，Seq = " + seq + "因此丢弃此分组");
            }

        }
    }

    /**
     * 超时处理——重发数据,不过
     *
     * @throws IOException
     */
    public void timeOut() throws IOException {

        String resendData = hostName
                + ": Resending to port " + destPort + ", Seq = " + base;

        byte[] data = resendData.getBytes();
        DatagramPacket datagramPacket = new DatagramPacket(data,
                data.length, destAddress, destPort);
        sendSocket.send(datagramPacket);
        senderSentSet.add(base);
        System.out.println(hostName
                + "重新发送发送到" + destPort + "端口， Seq = " + base);
        return;


    }

    /**
     * 向发送方回应ACK。
     *
     * @param seq    ACK序列号
     * @param toAddr 目的地址
     * @param toPort 目的端口
     * @throws IOException socket相关错误时抛出
     */
    protected void sendACK(int seq, InetAddress toAddr, int toPort) throws IOException {
        String response = hostName + " responses ACK: " + seq;
        byte[] responseData = response.getBytes();
        // 获得来源IP地址和端口，确定发给谁
        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, toAddr, toPort);
        receiveSocket.send(responsePacket);
    }

    public String getHostName() {
        return hostName;
    }

    /**
     * 设置发送分组的目标地址。
     *
     * @param destAddress 目标地址
     */
    public void setDestAddress(InetAddress destAddress) {
        this.destAddress = destAddress;
    }

    /**
     * 获得目标端口。
     *
     * @return 目标端口，int
     */
    public int getDestPort() {
        return destPort;
    }

    /**
     * 设置目标端口。
     *
     * @param destPort 目标端口
     */
    public void setDestPort(int destPort) {
        this.destPort = destPort;
    }

    /**
     * @param data1
     * @param data2
     * @return data1 与 data2拼接的结果
     */
    public static byte[] addBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;

    }


}
