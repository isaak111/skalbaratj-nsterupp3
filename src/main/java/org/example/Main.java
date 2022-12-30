package org.example;

import org.example.proxy.ReverseProxyServer;

public class Main {
    public static void main(String[] args) {
      new ReverseProxyServer(80).start();
    }
}