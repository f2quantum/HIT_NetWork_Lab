package GBN;

import java.io.IOException;

public class testGBNBothWay {
    public static void main(String[] args) throws IOException {
        GBN Alice = new GBN(20001, 5, 20, 2, "Host Alice");
        Alice.setDestPort(20002);
        GBN Bob = new GBN(20002, 5, 20, 2, "Host Bob");
        Bob.setDestPort(20001);
        new Thread(() -> {
            try {
                Alice.sendData("upload/testFileAlice",512);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                Alice.receiveData("download/outputAlice",512);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                Bob.sendData("upload/testFileBob",512);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        new Thread(() -> {
            try {
                Bob.receiveData("download/outputBob",512);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
