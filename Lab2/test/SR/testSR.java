package SR;

import GBN.GBN;

import java.io.IOException;

public class testSR {
    public static void main(String[] args) throws IOException {
        SR sender = new SR(20001, 5, 20, 2, "Host Alice");
        sender.setDestPort(20002);
        SR receiver = new SR(20002, 5, 20, 2, "Host Bob");
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
