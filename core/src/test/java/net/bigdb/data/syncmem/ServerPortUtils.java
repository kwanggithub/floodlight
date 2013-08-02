package net.bigdb.data.syncmem;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

public class ServerPortUtils {
    static final Random random = new Random();

    public static int findFreePort(int basePort) {
        for(int i = basePort; i < basePort + 10000; i += random.nextInt(10)) {
            try {
                ServerSocket socket = new ServerSocket(i);
                socket.close();
                return i;
            } catch (IOException e) {
                // try next
            }
        }
        throw new IllegalStateException("Could not find free port");
    }

}
