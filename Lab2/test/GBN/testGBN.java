package GBN;

import java.io.IOException;

public class testGBN {
    /**
     * 测试GBN协议
     * @throws IOException
     */



    public static void main(String[] args) throws IOException {
        GBN sender = new GBN(20001, 6, 10, 2, "Host Alice");
        sender.setDestPort(20002);
        GBN receiver = new GBN(20002, 6, 10, 2, "Host Bob");
        new Thread(() -> {
            try {
                sender.send();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                receiver.receive();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
