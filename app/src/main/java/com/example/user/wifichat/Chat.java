package com.example.user.wifichat;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Chat implements Runnable {
    private Socket socket = null;
    private Handler handler;

    private InputStream iStream;
    private OutputStream oStream;

    public Chat(Socket socket, Handler handler) {
        this.socket = socket;
        this.handler = handler;
    }

    @Override
    public void run() {
        try {
            iStream = socket.getInputStream();
            oStream = socket.getOutputStream();
            byte[] buffer = new byte[1024];
            int bytes;
            handler.obtainMessage(MainActivity.MY_CHAT, this)
                    .sendToTarget();
            while (true) {
                try {
                    bytes = iStream.read(buffer);
                    if (bytes == -1) {
                        break;
                    }
                    Log.d(MainActivity.TAG, "Received: " + String.valueOf(buffer));
                    handler.obtainMessage(MainActivity.MSG_READ,
                            bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    Log.e(MainActivity.TAG, "Disconnected: "+e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void write(byte[] buffer) {
        try {
            oStream.write(buffer);
        } catch (IOException e) {
            Log.e(MainActivity.TAG, "Exception during write: "+e.getMessage());
        }
    }
}
