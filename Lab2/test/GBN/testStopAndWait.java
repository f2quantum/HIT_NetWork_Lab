package GBN;

import java.io.IOException;

public class testStopAndWait {

    public static void main(String[] args) throws IOException {
        GBN sender = new GBN(20001, 1, 20, 2, "Host Alice");
        sender.setDestPort(20002);
        GBN receiver = new GBN(20002, 1, 20, 2, "Host Bob");
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

