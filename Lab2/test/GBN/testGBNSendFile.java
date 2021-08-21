package GBN;

import java.io.IOException;

public class testGBNSendFile {
    public static void main(String[] args) throws IOException {
        GBN sender = new GBN(20001, 3, 20, 2, "Host Alice");
        sender.setDestPort(20002);
        GBN receiver = new GBN(20002, 3, 20, 2, "Host Bob");
        new Thread(() -> {
            try {
                sender.sendData("upload/testFile",512);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                receiver.receiveData("download/output",512);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
