package GBN;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GBNHost。
 */
public class GBN {
    /**
     * 窗口长度。
     */
    protected int WINDOW_SIZE;

    /**
     * 发送方发送分组个数。
     */
    protected int DATA_NUMBER_Send = 0;
    /**
     * 接收方接受的分组个数。
     */
    protected int DATA_NUMBER_Rev =  0;

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
    /**
     * 储存上一个保存文件的报文标号，用于接受数据
     */
    protected int lastSave = 0 ;

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
     * 每个数据报文最大的储存的文件大小
     */
    protected int MaxDataPacketSize=0;

    /**
     * 标志是否发送文件
     */
    protected boolean isSendCarryData =false;
    /**
     * 标志是否接受文件
     */
    protected boolean isRevCarriedData =false;

    /**
     * 储存文件数据
     */
    protected byte[] dataList = new byte[0];
    /**
     * 维护每个数据报文的中数据长度
     */
    protected List<Integer> dataLengthList = new ArrayList<>() ;
    /**
     * 输出文件名
     */
    protected String outputFileName = new String();

    public GBN( int RECEIVE_PORT, int WINDOW_SIZE, int DATA_NUMBER, int TIMEOUT, String name) throws IOException {
        this.WINDOW_SIZE = WINDOW_SIZE;
        this.DATA_NUMBER_Send = DATA_NUMBER;
        this.DATA_NUMBER_Rev=DATA_NUMBER;
        this.TIMEOUT = TIMEOUT;
        this.hostName = name;

        sendSocket = new DatagramSocket();
        receiveSocket = new DatagramSocket(RECEIVE_PORT);
        destAddress = InetAddress.getLocalHost();
    }

    /**
     * 传送文件
     * @param filename 文件名
     */
    public void sendData(String filename,int MaxDataPacketSize) throws IOException {
        File file=new File(filename);
        this.MaxDataPacketSize=MaxDataPacketSize;
        if(file.length()==0){
            System.out.println("文件为空！");
            return;
        }
        try {
            DATA_NUMBER_Send=0;
            FileInputStream fis = new FileInputStream(file);
            byte[] bytes = new byte[MaxDataPacketSize];
            int length = 0;
            while((length = fis.read(bytes, 0, bytes.length)) != -1) {
                DATA_NUMBER_Send++;
                dataList=addBytes(dataList,bytes);
                dataLengthList.add(length);
            }
            isSendCarryData=true;
            System.out.println(hostName+":文件被拆分为"+DATA_NUMBER_Send+"个包");
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        send();


    }
    /**
     * 发送报文
     * @throws IOException
     */
    public void send() throws IOException {
        int maxACK = 0;
        while (true) {
            // 发送分组循环
            while (nextSeq < base + WINDOW_SIZE && nextSeq <= DATA_NUMBER_Send) {
                // 模拟数据丢失
                if (nextSeq % 5 == 0||nextSeq == 8) {
                    System.out.println(hostName + "模拟丢失报文：Seq = " + nextSeq);
                    nextSeq++;
                    continue;
                }
                byte[] data=new byte[MaxDataPacketSize];
                int length=0;
                if(isSendCarryData){
                    length=dataLengthList.get(nextSeq-1);
                    int curByte=0;
                    for(int i=0 ;i<nextSeq-1;i++){
                        curByte+=dataLengthList.get(i);
                    }
                    System.arraycopy(dataList,curByte,data,0,length);
                }
                String sendDataLabel = hostName + ": Sending to port " + destPort + ", Seq = " + nextSeq
                        +" isDataCarried ="+isSendCarryData+" length = "+length +" DATA_NUMBER = "+DATA_NUMBER_Send +"@@@@@";

                byte[] datagram = addBytes(sendDataLabel.getBytes(),data);


                DatagramPacket datagramPacket = new DatagramPacket(datagram, datagram.length, destAddress, destPort);
                sendSocket.send(datagramPacket);

                System.out.println(hostName + "发送到" + destPort + "端口， Seq = " + nextSeq);
                nextSeq=nextSeq+1;
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }

            // 用尽窗口后开始接收ACK
            byte[] bytes = new byte[4096];
            DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length);
            sendSocket.setSoTimeout(1000*TIMEOUT);
            try {
                sendSocket.receive(datagramPacket);
            } catch (SocketTimeoutException ex) {
                System.out.println(hostName + " 等待ACK:Seq="+base+"超时");
                timeOut();
                continue;
            }
            // 转换成String
            String fromServer = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
            // 解析出ACK编号
            int ack = Integer.parseInt(fromServer.substring(fromServer.indexOf("ACK: ") + "ACK: ".length()).trim());
            maxACK = Math.max(ack,maxACK);
            //ack最后确认收到的报文
            //nextSeq 发送完本轮报文期待下一轮收到的报文
            base = maxACK+1;

            System.out.println(hostName + "当前最后接收到的最大ACK: " + maxACK);

            if (maxACK == DATA_NUMBER_Send) {
                // 如果发送完毕
                // 停止计时器
                System.out.println(hostName + "发送完毕，发送方收到了全部的ACK信息");
                return;
            }
        }
    }

    public void receiveData (String fileName,int MaxDataPacketSize) throws IOException {

        isRevCarriedData = true;
        this.MaxDataPacketSize=MaxDataPacketSize;
        if(!fileName.isBlank()){
            outputFileName=fileName;
            receive();

        }else {
            System.out.println("文件名为空！");
        }


    }

    public void receive() throws IOException {

        File output = null;
        FileOutputStream fos = null;
        if(isRevCarriedData){
             output = new File(outputFileName);
             fos = new FileOutputStream(output);
        }

        while (true) {
            byte[] receivedData = new byte[Math.max(4096,MaxDataPacketSize+500)];
            DatagramPacket receivePacket = new DatagramPacket(receivedData, receivedData.length);
            receiveSocket.setSoTimeout(1000*TIMEOUT);
            try {
                receiveSocket.receive(receivePacket);
            } catch (SocketTimeoutException ex) {
                System.out.println(hostName + " 在正在等待分组： Seq= "+expectedSeq+"的到来 ");
                continue;
            }

            // 收到的数据
            String receivedLabel = new String(receivedData, 0,receivedData.length );
            String label =receivedLabel.split("@@@@@")[0];
            int labelSize = (label+"@@@@@").getBytes().length;

            String pattern = "\\w*: Sending to port \\d+, Seq = (\\d+) isDataCarried =(true|false) length = (\\d+) DATA_NUMBER = (\\d+)";
            Matcher matcher = Pattern.compile(pattern).matcher(label);

            if (!matcher.find()) {
                System.out.println(hostName + " 收到错误数据"+label);
                // 仍发送之前的ACK
                sendACK(expectedSeq - 1, receivePacket.getAddress(), receivePacket.getPort());
                continue;
            }

            int receivedSeq=Integer.parseInt(matcher.group(1));
            isRevCarriedData=Boolean.parseBoolean(matcher.group(2));
            int dataLength = Integer.parseInt(matcher.group(3));
            DATA_NUMBER_Rev = Integer.parseInt(matcher.group(4));

            if (receivedSeq  == expectedSeq) {
                // 收到了预期的数据
                System.out.println(hostName + " 收到了期待的数据,发送ACK：Seq = " + expectedSeq);
                if(isRevCarriedData&&lastSave == receivedSeq -1 ){

                    System.out.println(hostName + "写入数据 " + expectedSeq );
                    //System.out.println(hostName+" 收到的数据："+new String(receivedData,0,receivedData.length) );
                    fos.write(receivedData,labelSize,dataLength);
                    lastSave = receivedSeq ;
                }
                // 发送ACK
                if (expectedSeq % 7 == 0) {
                    //不发送ACK
                    System.out.println(hostName + "收到了期待的数据,但是模拟丢失ACK: " + expectedSeq);
                } else {
                    sendACK(expectedSeq, receivePacket.getAddress(), receivePacket.getPort());

                }

                if (expectedSeq == DATA_NUMBER_Rev) {
                    System.out.println(hostName + "接受完成");
                    if(isRevCarriedData){
                        fos.flush();
                        fos.close();
                    }

                    return;
                }
                // 期待值加1
                expectedSeq++;

            } else {
                // 未收到预期的Seq
                System.out.println(hostName + " 实际收到的数据Seq ="+receivedSeq+"，然而期待顺序收到数据Seq = " + expectedSeq+" 因此丢弃此分组");
                // 仍发送之前的ACK
                sendACK(expectedSeq - 1, receivePacket.getAddress(), receivePacket.getPort());
            }

        }
    }

    /**
     * 超时处理——重发数据
     * @throws IOException
     */
    public void timeOut() throws IOException {
        int curByte=0;
        for (int i =0;i<base-1&&isSendCarryData;i++){
            curByte=curByte+dataLengthList.get(i);
        }
        System.out.println(hostName+" 接受ACK超时，重发Seq："+base+"--"+(nextSeq-1));
        for (int i = base; i < nextSeq; i++) {
            byte[] data=new byte[MaxDataPacketSize];
            int length=0;
            if(isSendCarryData){
                length=dataLengthList.get(i-1);
                System.arraycopy(dataList,curByte,data,0,length);
                curByte=curByte+length;
            }
            String sendDataLabel = hostName + ": Sending to port " + destPort + ", Seq = " + i
                    +" isDataCarried ="+isSendCarryData+" length = "+length +" DATA_NUMBER = "+DATA_NUMBER_Send +"@@@@@";

            // 模拟发送分组
            byte[] datagram = addBytes(sendDataLabel.getBytes(),data);

            DatagramPacket datagramPacket = new DatagramPacket(datagram, datagram.length, destAddress, destPort);
            sendSocket.send(datagramPacket);

            System.out.println(hostName
                    + "重新发送发送到" + destPort + "端口， Seq = " + i);
        }
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
     *
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
